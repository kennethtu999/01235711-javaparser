package kai.javaparser.diagram;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;

import org.gradle.internal.impldep.org.apache.commons.lang.StringUtils;

import kai.javaparser.diagram.filter.DefaultTraceFilter;
import kai.javaparser.diagram.idx.AstIndex;
import kai.javaparser.diagram.output.MermaidOutput;
import kai.javaparser.model.FileAstData;
import kai.javaparser.model.InteractionModel;
import kai.javaparser.model.ControlFlowFragment;
import kai.javaparser.model.MethodGroup;
import kai.javaparser.model.DiagramNode; // Import DiagramNode

/**
 * 序列圖生成器：
 * 1. 載入 AST 索引
 * 2. 遞迴追蹤方法呼叫
 * 3. 產生 Mermaid 語法（安全 ID + 可還原 FQN）
 */
public class SequenceDiagramGenerator {

    public static String generate(String astDir, String entryPointMethodFqn, String packageScope,
            String excludedClasses, String excludedMethods, int depth) {
        try {
            AstIndex astIndex = initAstIndex(astDir);
            TraceFilter filter = new DefaultTraceFilter(parseCsv(excludedClasses), parseCsv(excludedMethods));
            MermaidOutput output = new MermaidOutput();

            // 固定起點 Actor
            output.addActor("User");
            String entryClassFqn = AstClassUtil.getClassFqnFromMethodFqn(entryPointMethodFqn);
            String methodSignature = AstClassUtil.getMethodSignature(entryPointMethodFqn);
            output.addEntryPointCall("User", safeMermaidId(entryClassFqn), entryClassFqn + "." + methodSignature);

            traceMethod(entryPointMethodFqn, packageScope, filter, astIndex, output, new HashSet<>(), depth);

            System.out.println("\n--- Mermaid 序列圖語法 ---");
            System.out.println(output.toString());

            return output.toString();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("執行期間發生錯誤: " + e.getMessage(), e);
        }
    }

    private static AstIndex initAstIndex(String astDir) throws IOException, ClassNotFoundException {
        AstIndex astIndex = new AstIndex(Path.of(astDir));
        astIndex.loadOrBuild();
        return astIndex;
    }

    private static Set<String> parseCsv(String csv) {
        if (StringUtils.isBlank(csv))
            return Collections.emptySet();
        return Arrays.stream(csv.split(",")).map(String::trim).collect(Collectors.toSet());
    }

    /**
     * 遞迴追蹤方法呼叫
     */
    private static boolean traceMethod(String methodFqn, String packageScope, TraceFilter filter,
            AstIndex astIndex, MermaidOutput output, Set<String> callStack, int depth) {

        if (depth <= 0)
            return false;

        if (!isTraceable(methodFqn, packageScope, filter, astIndex, callStack))
            return false;

        String classFqn = AstClassUtil.getClassFqnFromMethodFqn(methodFqn);
        FileAstData astData = astIndex.getAstDataByClassFqn(classFqn);
        if (astData == null)
            return false;

        List<InteractionModel> validInvocations = astData.findMethodNode(methodFqn)
                .map(astData::findMethodInvocations)
                .orElse(new ArrayList<>()).stream()
                .filter(inv -> inv.getMethodName() != null
                        && !filter.shouldExclude(classFqn + "." + inv.getMethodName(), astIndex))
                .collect(Collectors.toList());

        // 獲取當前方法的 MethodGroup
        MethodGroup currentMethodGroup = astData.getSequenceDiagramData()
                .findMethodGroup(AstClassUtil.getMethodSignature(methodFqn).split("\\(")[0]);
        List<ControlFlowFragment> controlFlowFragments = new ArrayList<>();
        if (currentMethodGroup != null) {
            controlFlowFragments.addAll(currentMethodGroup.getControlFlowFragments());
        }

        if (validInvocations.isEmpty() && controlFlowFragments.isEmpty())
            return false;

        callStack.add(methodFqn);

        String callerId = safeMermaidId(classFqn);
        // 不要重複添加 participant，因為 addEntryPointCall 已經添加過了
        output.activate(callerId);

        // 合併所有 DiagramNode 並按行號排序
        List<DiagramNode> sortedNodes = new ArrayList<>();
        sortedNodes.addAll(validInvocations);
        sortedNodes.addAll(controlFlowFragments);
        sortedNodes.sort(Comparator.comparingInt(DiagramNode::getStartLineNumber));

        // 處理排序後的節點
        for (DiagramNode node : sortedNodes) {
            if (node instanceof ControlFlowFragment) {
                traceControlFlowFragment((ControlFlowFragment) node, packageScope, filter, astIndex, output, callStack,
                        depth - 1, callerId);
            } else if (node instanceof InteractionModel) {
                InteractionModel invocation = (InteractionModel) node;
                String calleeClassFqn = invocation.getCallee() != null ? invocation.getCallee() : classFqn;
                String calleeId = safeMermaidId(calleeClassFqn);

                output.addParticipant(calleeId, calleeClassFqn);
                output.addCall(callerId, calleeId, invocation.getMethodName());

                // 遞迴追蹤被呼叫的方法
                String fullCalleeFqn = calleeClassFqn + "." + invocation.getMethodName();
                traceMethod(fullCalleeFqn, packageScope, filter, astIndex, output,
                        callStack, depth - 1);
            }
        }

        output.deactivate(callerId);
        callStack.remove(methodFqn);
        return true;
    }

    private static void traceControlFlowFragment(ControlFlowFragment fragment, String packageScope, TraceFilter filter,
            AstIndex astIndex, MermaidOutput output, Set<String> callStack, int depth, String parentCallerId) {

        if (depth <= 0) {
            return;
        }

        String condition = fragment.getCondition() != null ? fragment.getCondition() : "";

        // output.addCombinedFragment(fragmentType, condition); // Replaced with
        // specific fragment methods
        switch (fragment.getType()) {
            case ALTERNATIVE:
                output.addAltFragment(condition);
                break;
            case LOOP:
                output.addLoopFragment(condition);
                break;
            case OPTIONAL:
                output.addOptFragment(condition);
                break;
        }

        // 處理片段內的 interactions
        for (InteractionModel interaction : fragment.getInteractions()) {
            String calleeClassFqn = interaction.getCallee() != null ? interaction.getCallee()
                    : fragment.getCallerClass(); // Use fragment caller class as default
            String calleeId = safeMermaidId(calleeClassFqn);

            output.addParticipant(calleeId, calleeClassFqn);
            output.addCall(parentCallerId, calleeId, interaction.getMethodName()); // Calls originate from the parent of
                                                                                   // the fragment

            String fullCalleeFqn = calleeClassFqn + "." + interaction.getMethodName();
            traceMethod(fullCalleeFqn, packageScope, filter, astIndex, output, callStack, depth - 1);
        }

        // 處理嵌套的 alternatives (例如 else if, nested loops)
        if (fragment.getAlternatives() != null) {
            for (ControlFlowFragment alternative : fragment.getAlternatives()) {
                traceControlFlowFragment(alternative, packageScope, filter, astIndex, output, callStack, depth - 1,
                        parentCallerId);
            }
        }
        output.endFragment(); // Replaced endCombinedFragment with endFragment
    }

    private static boolean isTraceable(String methodFqn, String packageScope, TraceFilter filter,
            AstIndex astIndex, Set<String> callStack) {
        if (callStack.contains(methodFqn))
            return false;
        if (!methodFqn.startsWith(packageScope))
            return false;
        return !filter.shouldExclude(methodFqn, astIndex);
    }

    /**
     * 產生 Mermaid 安全 ID（以 simple class name 為基礎）
     */
    private static String safeMermaidId(String classFqn) {
        return classFqn.replace(".", "_").replaceAll("[()<>]", "").replace(",", "__");
    }
}
