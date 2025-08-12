package kai.javaparser.diagram;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import kai.javaparser.diagram.filter.DefaultTraceFilter;
import kai.javaparser.diagram.idx.AstIndex;
import kai.javaparser.diagram.output.MermaidOutput;
import kai.javaparser.model.AstNode;
import kai.javaparser.model.FileAstData;

/**
 * 主程式，負責生成序列圖。
 * <p>
 * 作為一個協調器，它將複雜的任務拆分給專門的元件處理：
 * 1. {@link AstIndex}: 負責高效地載入和查詢 AST 資訊。
 * 2. {@link TraceFilter}: 負責決定哪些方法呼叫應該被追蹤或忽略。
 * 3. {@link MermaidOutput}: 負責產生最終的 Mermaid 圖表語法。
 * <p>
 * 核心方法 {@code traceMethod} 採用遞迴方式追蹤方法呼叫鏈，並實現了智能的 activate/deactivate 邏輯：
 * 只有當一個方法內部確實包含有效的、未被過濾的下游呼叫時，才會為其生成 activate/deactivate 區塊，
 * 使產生的圖表更加簡潔和有意義。
 * </p>
 */
public class SequenceDiagramGenerator {

    public static void main(String[] args) {
        if (args.length < 3 || args.length > 5) {
            System.out.println(
                    "使用方式: java SequenceDiagramGenerator <ast_json_dir> <entry_point_method_fqn> <package_scope> [excluded_classes_fqn]");
            System.out.println(
                    "範例: java SequenceDiagramGenerator build/parsed_asts com.example.MyClass.doSomething() com.example com.example.util,java.lang");
            return;
        }

        try {
            // 1. 初始化元件
            Path astJsonDir = Path.of(args[0]);
            String entryPointMethodFqn = args[1];
            String packageScope = args[2];

            Set<String> exclusionClassSet = new HashSet<>();
            Set<String> exclusionMethodSet = new HashSet<>();
            if (args.length >= 4 && args[3] != null && !args[3].trim().isEmpty()) {
                exclusionClassSet.addAll(Arrays.asList(args[3].split(",")));
            }
            if (args.length >= 5 && args[4] != null && !args[4].trim().isEmpty()) {
                exclusionMethodSet.addAll(Arrays.asList(args[4].split(",")));
            }

            AstIndex astIndex = new AstIndex(astJsonDir);
            astIndex.loadOrBuild(); // 從快取或檔案系統載入索引

            TraceFilter filter = new DefaultTraceFilter(exclusionClassSet, exclusionMethodSet);
            MermaidOutput output = new MermaidOutput();

            // 2. 設定進入點並開始追蹤
            output.addActor("User");
            output.addEntryPointCall("User", entryPointMethodFqn);
            traceMethod(entryPointMethodFqn, packageScope, filter, astIndex, output, new HashSet<>());

            // 3. 輸出結果
            System.out.println("\n--- Mermaid 序列圖語法 ---");
            System.out.println(output.toString());

        } catch (IOException | ClassNotFoundException e) {
            System.err.println("執行期間發生錯誤: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 遞迴追蹤方法呼叫。
     *
     * @return 如果此方法或其任何下游呼叫產生了追蹤輸出，則返回 true。
     */
    private static boolean traceMethod(String methodFqn, String packageScope, TraceFilter filter,
            AstIndex astIndex, MermaidOutput output, Set<String> callStack) {
        // --- 防禦性檢查 ---
        if (callStack.contains(methodFqn)) {
            return false; // 避免無限遞迴
        }
        if (!methodFqn.startsWith(packageScope)) {
            return false; // 過濾掉不在我們關心範圍內的方法
        }
        if (filter.shouldExclude(methodFqn, astIndex)) {
            return false; // 被過濾器排除
        }

        String classFqn = AstClassUtil.getClassFqnFromMethodFqn(methodFqn);
        FileAstData astData = astIndex.getAstDataByClassFqn(classFqn);
        if (astData == null) {
            return false; // 找不到 AST 資訊
        }

        // --- 尋找此方法節點內的所有下游呼叫 ---
        List<AstNode> invocations = astData.findMethodNode(methodFqn)
                .map(astData::findMethodInvocations)
                .orElse(List.of());

        // 過濾掉不需追蹤的下游呼叫
        List<AstNode> validInvocations = invocations.stream()
                .filter(inv -> inv.getFullyQualifiedName() != null
                        && !filter.shouldExclude(inv.getFullyQualifiedName(), astIndex))
                .collect(Collectors.toList());

        // *** 核心邏輯: 如果沒有任何有效的下游呼叫，則不為此方法產生任何輸出 ***
        if (validInvocations.isEmpty()) {
            return false;
        }

        // --- 執行追蹤與輸出 ---
        callStack.add(methodFqn);
        String callerSimpleName = AstClassUtil.getSimpleClassName(methodFqn);
        output.activate(callerSimpleName);

        boolean tracedSomethingDownstream = false;
        for (AstNode invocation : validInvocations) {
            String invokedMethodFqn = invocation.getFullyQualifiedName();
            String calleeSimpleName = AstClassUtil.getSimpleClassName(invokedMethodFqn);
            String methodSignature = AstClassUtil.getMethodSignature(invokedMethodFqn);

            output.addCall(callerSimpleName, calleeSimpleName, methodSignature);

            // 遞迴呼叫
            if (traceMethod(invokedMethodFqn, packageScope, filter, astIndex, output, callStack)) {
                tracedSomethingDownstream = true;
            }
        }

        output.deactivate(callerSimpleName);
        callStack.remove(methodFqn);

        return true; // 因為我們至少產生了 activate/deactivate 和 call，所以返回 true
    }
}