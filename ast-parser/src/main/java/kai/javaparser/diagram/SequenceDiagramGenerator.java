package kai.javaparser.diagram;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kai.javaparser.diagram.idx.AstIndex;
import kai.javaparser.diagram.output.MermaidOutput;
import kai.javaparser.model.ControlFlowFragment;
import kai.javaparser.model.DiagramNode; // Import DiagramNode
import kai.javaparser.model.FileAstData;
import kai.javaparser.model.InteractionModel;
import kai.javaparser.model.MethodGroup;

/**
 * 序列圖生成器：
 * 1. 載入 AST 索引
 * 2. 遞迴追蹤方法呼叫
 * 3. 產生 Mermaid 語法（安全 ID + 可還原 FQN）
 */
public class SequenceDiagramGenerator {
    private static final Logger logger = LoggerFactory.getLogger(SequenceDiagramGenerator.class);

    public static String generate(String astDir, String entryPointMethodFqn, String packageScope,
            TraceFilter filter, int depth) {
        try {
            AstIndex astIndex = initAstIndex(astDir);
            MermaidOutput output = new MermaidOutput();

            // 固定起點 Actor
            output.addActor("User");
            String entryClassFqn = AstClassUtil.getClassFqnFromMethodFqn(entryPointMethodFqn);
            String methodSignature = AstClassUtil.getMethodSignature(entryPointMethodFqn);
            output.addEntryPointCall("User", safeMermaidId(entryClassFqn), entryClassFqn + "." + methodSignature);

            traceMethod(entryPointMethodFqn, packageScope, filter, astIndex, output, new HashSet<>(), depth);

            logger.info("\n--- Mermaid 序列圖語法 ---");
            logger.info(output.toString());

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
                        && !filter.shouldExclude(inv.getCallee(), inv.getMethodName(), astIndex))
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
                output.addCall(callerId, calleeId, invocation.getMethodName(), invocation.getArguments(),
                        invocation.getAssignedToVariable());

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

            if (!filter.shouldExclude(calleeClassFqn, interaction.getMethodName(), astIndex)) {
                output.addParticipant(calleeId, calleeClassFqn);
                output.addCall(parentCallerId, calleeId, interaction.getMethodName(),
                        interaction.getArguments(), interaction.getAssignedToVariable());
            }

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
