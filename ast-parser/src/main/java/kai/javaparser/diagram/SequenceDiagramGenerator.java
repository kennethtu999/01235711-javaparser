package kai.javaparser.diagram;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import kai.javaparser.model.AstNode;
import kai.javaparser.model.AstNodeType;
import kai.javaparser.model.FileAstData;

public class SequenceDiagramGenerator {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Map<String, Path> classAstIndex = new HashMap<>();
    private static final Map<Path, FileAstData> astCache = new HashMap<>();
    private static final StringBuilder mermaidBuilder = new StringBuilder();

    public static void main(String[] args) throws IOException {
        // --- MODIFICATION START: Update argument parsing ---
        if (args.length < 3 || args.length > 5) {
            System.out.println("使用方式: java SequenceDiagramGenerator <ast_json_dir> <entry_point_method_fqn> <package_scope> [excluded_classes_fqn]");
            System.out.println("範例: java SequenceDiagramGenerator build/parsed_asts com.example.test.MyClass.doSomething() com.example.test com.example.test.MyService,com.another.UtilityClass");
            System.out.println("[excluded_classes_fqn] 是可選參數，用逗號分隔。");
            return;
        }

        Path astJsonDir = Path.of(args[0]);
        String entryPointMethodFqn = args[1];
        String packageScope = args[2];

        // 解析排除列表 (新功能)
        Set<String> exclusionClassSet = new HashSet<>();
        if (args.length >= 4 && args[3] != null && !args[3].trim().isEmpty()) {
            exclusionClassSet.addAll(Arrays.asList(args[3].split(",")));
            System.out.println("將排除以下類別的追蹤：" + exclusionClassSet);
        }

        Set<String> exclusionMethodSet = new HashSet<>();
        if (args.length == 5 && args[4] != null && !args[4].trim().isEmpty()) {
            exclusionMethodSet.addAll(Arrays.asList(args[4].split(",")));
            System.out.println("將排除以下方法的追蹤：" + exclusionMethodSet);
        }
        // --- MODIFICATION END ---

        System.out.println("正在建立 AST 索引...");
        buildAstIndex(astJsonDir);
        System.out.println("索引建立完成，共找到 " + classAstIndex.size() + " 個類別。");

        mermaidBuilder.append("sequenceDiagram\n");
        String entryPointClassSimpleName = AstClassUtil.getSimpleClassName(entryPointMethodFqn);
        mermaidBuilder.append("    actor User\n");
        mermaidBuilder.append(String.format("    User->>%s: %s\n", entryPointClassSimpleName, AstClassUtil.getMethodSignature(entryPointMethodFqn)));
        
        // --- MODIFICATION START: Pass exclusionSet to traceMethod ---
        traceMethod(entryPointMethodFqn, packageScope, exclusionClassSet, exclusionMethodSet, new HashSet<>());
        // --- MODIFICATION END ---

        System.out.println("\n--- Mermaid 序列圖語法 ---");
        System.out.println(mermaidBuilder.toString());
    }

    // ... buildAstIndex 方法保持不變 ...
    private static void buildAstIndex(Path astJsonDir) throws IOException {
        try (Stream<Path> paths = Files.walk(astJsonDir)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".json"))
                 .forEach(jsonFile -> {
                     try {
                         FileAstData astData = objectMapper.readValue(jsonFile.toFile(), FileAstData.class);
                         astData.getCompilationUnitNode().getChildren().stream()
                            .filter(node -> node.getType() == AstNodeType.TYPE_DECLARATION)
                            .findFirst()
                            .ifPresent(classNode -> {
                                String classFqn = astData.getPackageName() + "." + classNode.getName();
                                classAstIndex.put(classFqn, jsonFile);
                                astCache.put(jsonFile, astData);
                            });
                     } catch (IOException e) {
                         System.err.println("讀取或解析 JSON 檔案失敗: " + jsonFile);
                     }
                 });
        }
    }

    /**
     * 判斷是否被排除
     * @param classFqn
     * @param exclusionClassSet
     * @param exclusionMethodSet
     * @return
     */
    private static boolean isExcluded(String classFqn, Set<String> exclusionClassSet, Set<String> exclusionMethodSet) {
        if (exclusionClassSet.stream().anyMatch(exclusion -> classFqn.startsWith(exclusion))) {
            System.err.println("INFO: 已跳過追蹤被排除的類別: " + classFqn);
            return true;
        }

        if (exclusionMethodSet.stream().anyMatch(exclusion -> classFqn.indexOf(exclusion) != -1)) {
            System.err.println("INFO: 已跳過追蹤被排除的方法: " + classFqn);
            return true;
        }

        
        // 檢查是否是 getter/setter 方法，如果是且對應到 attribute 則排除
        if (classFqn.matches(".*\\.get.*") || classFqn.matches(".*\\.set.*")) {
            String targetClassFqn = AstClassUtil.getClassFqnFromMethodFqn(classFqn);
            String methodName = AstClassUtil.getMethodSignature(classFqn);
            
            // 提取 attribute 名稱
            final String attrName = methodName.replaceAll("^(get|set)", "").replaceAll("\\(.*\\)", "");
            final String finalAttrName;
            if (attrName.length() > 0) {
                // 將第一個字母轉為小寫（Java 命名慣例）
                finalAttrName = attrName.substring(0, 1).toLowerCase() + attrName.substring(1);
            } else {
                finalAttrName = attrName;
            }
            
            // 檢查目標類別是否有對應的 attribute
            Path path = classAstIndex.get(targetClassFqn);
            if (path != null) {
                FileAstData astData = astCache.get(path);
                if (astData != null) {
                    if (astData.isGetterSetter(finalAttrName)) {
                        System.err.println("INFO: 已跳過追蹤對應到 attribute 的 Getter/Setter: " + classFqn + " (attribute: " + finalAttrName + ")");
                        return true;
                    }
                }
            }
            
            // 檢查是否在排除列表中
            if (exclusionClassSet.stream().anyMatch(exclusion -> targetClassFqn.startsWith(exclusion))) {
                System.err.println("INFO: 已跳過追蹤被排除的類別: " + targetClassFqn);
                return true;
            }
        }
        return false;
    }

    // --- MODIFICATION START: Update traceMethod signature and add exclusion logic ---
    private static void traceMethod(String methodFqn, String packageScope, Set<String> exclusionClassSet, Set<String> exclusionMethodSet, Set<String> callStack) throws IOException {
        // --- 防禦性檢查 ---
        if (callStack.contains(methodFqn)) {
            return; // 避免無限遞迴
        }
        
        String classFqn = AstClassUtil.getClassFqnFromMethodFqn(methodFqn);

        // 新增的排除邏輯
        if (isExcluded(classFqn, exclusionClassSet, exclusionMethodSet)) {
            System.err.println("INFO: 已跳過追蹤被排除的類別: " + classFqn);
            return;
        }

        if (!methodFqn.startsWith(packageScope)) {
            return; // 過濾掉不在我們關心範圍內的方法
        }
        // --- MODIFICATION END ---
        
        Path astFile = classAstIndex.get(classFqn);
        if (astFile == null) {
            return;
        }

        callStack.add(methodFqn);
        
        String callerClassSimpleName = AstClassUtil.getSimpleClassName(classFqn);
        mermaidBuilder.append(String.format("    activate %s\n", callerClassSimpleName));

        FileAstData astData = astCache.computeIfAbsent(astFile, path -> {
            try { return objectMapper.readValue(path.toFile(), FileAstData.class); } catch (IOException e) { return null; }
        });

        if (astData == null) return;
        
        findMethodNode(astData, AstClassUtil.getMethodSignature(methodFqn)).ifPresent(methodNode -> {
            findMethodInvocations(methodNode).forEach(invocation -> {
                String invokedMethodFqn = invocation.getFullyQualifiedName();

                if (isExcluded(invokedMethodFqn, exclusionClassSet, exclusionMethodSet)) {
                    System.err.println("INFO: 已跳過追蹤被排除的類別: " + invokedMethodFqn);
                    return;
                }

                if (invokedMethodFqn != null) {
                    String calleeClassSimpleName = AstClassUtil.getSimpleClassName(invokedMethodFqn);
                    String methodSignature = AstClassUtil.getMethodSignature(invokedMethodFqn);

                    mermaidBuilder.append(String.format("    %s->>%s: %s\n", callerClassSimpleName, calleeClassSimpleName, methodSignature));
                    
                    try {
                        // --- MODIFICATION START: Pass exclusionSet in recursive call ---
                        traceMethod(invokedMethodFqn, packageScope, exclusionClassSet, exclusionMethodSet, callStack);
                        // --- MODIFICATION END ---
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        });

        mermaidBuilder.append(String.format("    deactivate %s\n", callerClassSimpleName));
        callStack.remove(methodFqn);
    }

    // --- 輔助方法 (保持不變) ---

    private static Optional<AstNode> findMethodNode(FileAstData fileAstData, String methodSignature) {
        return fileAstData.getCompilationUnitNode().getChildren().stream()
                .filter(node -> node.getType() == AstNodeType.TYPE_DECLARATION)
                .flatMap(classNode -> classNode.getChildren().stream())
                .filter(node -> node.getType() == AstNodeType.METHOD_DECLARATION && node.getName().equals(methodSignature.split("\\(")[0]))
                .findFirst();
    }

    private static List<AstNode> findMethodInvocations(AstNode startNode) {
        List<AstNode> invocations = new ArrayList<>();
        Queue<AstNode> queue = new LinkedList<>();
        queue.add(startNode);
        while (!queue.isEmpty()) {
            AstNode current = queue.poll();
            if (current.getType() == AstNodeType.METHOD_INVOCATION) {
                invocations.add(current);
            }
            if (current.getChildren() != null) {
                queue.addAll(current.getChildren());
            }
        }
        return invocations;
    }

}