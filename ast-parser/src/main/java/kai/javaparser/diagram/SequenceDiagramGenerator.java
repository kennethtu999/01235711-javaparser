package kai.javaparser.diagram;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import org.gradle.internal.impldep.org.apache.commons.lang.StringUtils;

import kai.javaparser.diagram.filter.DefaultTraceFilter;
import kai.javaparser.diagram.idx.AstIndex;
import kai.javaparser.diagram.output.MermaidOutput;
import kai.javaparser.model.AstNode;
import kai.javaparser.model.FileAstData;

/**
 * 序列圖生成器：
 * 1. 載入 AST 索引
 * 2. 遞迴追蹤方法呼叫
 * 3. 產生 Mermaid 語法（安全 ID + 可還原 FQN）
 */
public class SequenceDiagramGenerator {

    public static String generate(String astDir, String entryPointMethodFqn, String packageScope,
            String excludedClasses, String excludedMethods) {
        try {
            AstIndex astIndex = initAstIndex(astDir);
            TraceFilter filter = new DefaultTraceFilter(parseCsv(excludedClasses), parseCsv(excludedMethods));
            MermaidOutput output = new MermaidOutput();

            // 固定起點 Actor
            output.addActor("User");
            String entryClassFqn = AstClassUtil.getClassFqnFromMethodFqn(entryPointMethodFqn);
            output.addEntryPointCall("User", safeMermaidId(entryClassFqn), entryClassFqn);

            traceMethod(entryPointMethodFqn, packageScope, filter, astIndex, output, new HashSet<>());

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
            AstIndex astIndex, MermaidOutput output, Set<String> callStack) {
        if (!isTraceable(methodFqn, packageScope, filter, astIndex, callStack))
            return false;

        String classFqn = AstClassUtil.getClassFqnFromMethodFqn(methodFqn);
        FileAstData astData = astIndex.getAstDataByClassFqn(classFqn);
        if (astData == null)
            return false;

        List<AstNode> validInvocations = astData.findMethodNode(methodFqn)
                .map(astData::findMethodInvocations)
                .orElse(List.of()).stream()
                .filter(inv -> inv.getFullyQualifiedName() != null
                        && !filter.shouldExclude(inv.getFullyQualifiedName(), astIndex))
                .collect(Collectors.toList());

        if (validInvocations.isEmpty())
            return false;

        callStack.add(methodFqn);

        String callerId = safeMermaidId(classFqn);
        output.addParticipant(callerId, classFqn);
        output.activate(callerId);

        for (AstNode invocation : validInvocations) {
            String calleeFqn = invocation.getFullyQualifiedName();
            String calleeClassFqn = AstClassUtil.getClassFqnFromMethodFqn(calleeFqn);
            String calleeId = safeMermaidId(calleeClassFqn);

            output.addParticipant(calleeId, calleeClassFqn);

            String methodSignature = AstClassUtil.getMethodSignature(calleeFqn);
            output.addCall(callerId, calleeId, methodSignature);

            traceMethod(calleeFqn, packageScope, filter, astIndex, output, callStack);
        }

        output.deactivate(callerId);
        callStack.remove(methodFqn);
        return true;
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
