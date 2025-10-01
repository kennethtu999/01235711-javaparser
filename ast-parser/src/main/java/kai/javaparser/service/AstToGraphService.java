package kai.javaparser.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import kai.javaparser.repository.FileSystemAstRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * AST 到圖數據庫轉換服務
 * 負責將 AST JSON 文件轉換為 Neo4j 圖數據庫中的節點和關係
 */
@Slf4j
@Service
public class AstToGraphService {

    @Autowired
    private Neo4jService neo4jService;

    @Autowired
    private FileSystemAstRepository astRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 將所有 AST JSON 文件轉換為圖數據庫
     * 
     * @return 轉換的統計信息
     */
    public Map<String, Object> convertAllAstToGraph() {
        log.info("開始將所有 AST JSON 文件轉換為圖數據庫");

        Map<String, Object> statistics = new HashMap<>();
        int totalFiles = 0;
        int successFiles = 0;
        int errorFiles = 0;
        long totalNodes = 0;
        long totalRelationships = 0;

        try {
            // 獲取 AST 目錄
            Path astPath = astRepository.getAstJsonDir();
            if (astPath == null) {
                log.warn("AST 目錄未初始化");
                return Map.of(
                        "success", false,
                        "message", "AST 目錄未初始化",
                        "totalFiles", 0,
                        "successFiles", 0,
                        "errorFiles", 0,
                        "totalNodes", 0,
                        "totalRelationships", 0);
            }

            if (!Files.exists(astPath)) {
                log.warn("AST 目錄不存在: {}", astPath);
                return Map.of(
                        "success", false,
                        "message", "AST 目錄不存在: " + astPath,
                        "totalFiles", 0,
                        "successFiles", 0,
                        "errorFiles", 0,
                        "totalNodes", 0,
                        "totalRelationships", 0);
            }

            // 遍歷所有 JSON 文件
            try (Stream<Path> paths = Files.walk(astPath)) {
                List<Path> jsonFiles = paths
                        .filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".json"))
                        .toList();

                totalFiles = jsonFiles.size();
                log.info("找到 {} 個 JSON 文件", totalFiles);

                for (Path jsonFile : jsonFiles) {
                    try {
                        Map<String, Object> result = convertSingleAstFile(jsonFile);
                        successFiles++;
                        totalNodes += (Long) result.getOrDefault("nodes", 0L);
                        totalRelationships += (Long) result.getOrDefault("relationships", 0L);
                        log.debug("成功轉換文件: {}, 節點: {}, 關係: {}",
                                jsonFile.getFileName(),
                                result.get("nodes"),
                                result.get("relationships"));
                    } catch (Exception e) {
                        errorFiles++;
                        log.error("轉換文件失敗: {}", jsonFile, e);
                    }
                }
            }

            statistics.put("success", true);
            statistics.put("message", "轉換完成");
            statistics.put("totalFiles", totalFiles);
            statistics.put("successFiles", successFiles);
            statistics.put("errorFiles", errorFiles);
            statistics.put("totalNodes", totalNodes);
            statistics.put("totalRelationships", totalRelationships);

            log.info("AST 轉換完成 - 總文件: {}, 成功: {}, 失敗: {}, 節點: {}, 關係: {}",
                    totalFiles, successFiles, errorFiles, totalNodes, totalRelationships);

        } catch (Exception e) {
            log.error("轉換過程中發生錯誤", e);
            statistics.put("success", false);
            statistics.put("message", "轉換失敗: " + e.getMessage());
            statistics.put("error", e.getMessage());
        }

        return statistics;
    }

    /**
     * 轉換單個 AST JSON 文件
     * 
     * @param jsonFile JSON 文件路徑
     * @return 轉換結果統計
     */
    public Map<String, Object> convertSingleAstFile(Path jsonFile) throws IOException {
        log.debug("開始轉換文件: {}", jsonFile);

        // 讀取 JSON 文件
        String jsonContent = Files.readString(jsonFile);
        JsonNode rootNode = objectMapper.readTree(jsonContent);

        // 提取文件信息
        String sourceFile = jsonFile.toString();
        String packageName = extractPackageName(rootNode);

        long nodesCreated = 0;
        long relationshipsCreated = 0;

        // 轉換類別
        List<String> classCyphers = convertClasses(rootNode, sourceFile, packageName);
        for (String cypher : classCyphers) {
            long affected = neo4jService.executeWrite(cypher);
            nodesCreated += affected;
        }

        // 轉換介面
        List<String> interfaceCyphers = convertInterfaces(rootNode, sourceFile, packageName);
        for (String cypher : interfaceCyphers) {
            long affected = neo4jService.executeWrite(cypher);
            nodesCreated += affected;
        }

        // 轉換方法
        List<String> methodCyphers = convertMethods(rootNode, sourceFile, packageName);
        for (String cypher : methodCyphers) {
            long affected = neo4jService.executeWrite(cypher);
            nodesCreated += affected;
        }

        // 轉換註解
        List<String> annotationCyphers = convertAnnotations(rootNode, sourceFile,
                packageName);
        for (String cypher : annotationCyphers) {
            long affected = neo4jService.executeWrite(cypher);
            nodesCreated += affected;
        }

        // 轉換關係
        List<String> relationshipCyphers = convertRelationships(rootNode, sourceFile, packageName);
        for (String cypher : relationshipCyphers) {
            long affected = neo4jService.executeWrite(cypher);
            relationshipsCreated += affected;
        }

        return Map.of(
                "sourceFile", sourceFile,
                "package", packageName,
                "nodes", nodesCreated,
                "relationships", relationshipsCreated);
    }

    /**
     * 異步轉換所有 AST 文件
     * 
     * @return CompletableFuture 包含轉換統計信息
     */
    public CompletableFuture<Map<String, Object>> convertAllAstToGraphAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return convertAllAstToGraph();
            } catch (Exception e) {
                log.error("異步轉換失敗", e);
                return Map.of(
                        "success", false,
                        "message", "異步轉換失敗: " + e.getMessage(),
                        "error", e.getMessage());
            }
        });
    }

    /**
     * 清理圖數據庫中的所有 AST 相關數據
     */
    public void clearAstData() {
        log.info("開始清理 AST 相關數據");

        // 刪除所有關係
        neo4jService
                .executeWrite("MATCH ()-[r:EXTENDS|IMPLEMENTS|CALLS|CONTAINS|ANNOTATES|HAS_ANNOTATION]->() DELETE r");

        // 刪除所有節點
        neo4jService.executeWrite("MATCH (n:Class|Interface|Method|Field|Annotation) DELETE n");

        log.info("AST 數據清理完成");
    }

    /**
     * 獲取圖數據庫中的 AST 統計信息
     * 
     * @return 統計信息
     */
    public Map<String, Object> getAstStatistics() {
        Map<String, Object> stats = new HashMap<>();

        // 統計節點數量
        List<org.neo4j.driver.Record> classCount = neo4jService
                .executeQuery("MATCH (c:Class) RETURN count(c) as count");
        List<org.neo4j.driver.Record> interfaceCount = neo4jService
                .executeQuery("MATCH (i:Interface) RETURN count(i) as count");
        List<org.neo4j.driver.Record> methodCount = neo4jService
                .executeQuery("MATCH (m:Method) RETURN count(m) as count");
        List<org.neo4j.driver.Record> annotationCount = neo4jService
                .executeQuery("MATCH (a:Annotation) RETURN count(a) as count");

        // 統計關係數量
        List<org.neo4j.driver.Record> extendsCount = neo4jService
                .executeQuery("MATCH ()-[r:EXTENDS]->() RETURN count(r) as count");
        List<org.neo4j.driver.Record> implementsCount = neo4jService
                .executeQuery("MATCH ()-[r:IMPLEMENTS]->() RETURN count(r) as count");
        List<org.neo4j.driver.Record> callsCount = neo4jService
                .executeQuery("MATCH ()-[r:CALLS]->() RETURN count(r) as count");
        List<org.neo4j.driver.Record> containsCount = neo4jService
                .executeQuery("MATCH ()-[r:CONTAINS]->() RETURN count(r) as count");
        List<org.neo4j.driver.Record> hasAnnotationCount = neo4jService
                .executeQuery("MATCH ()-[r:HAS_ANNOTATION]->() RETURN count(r) as count");

        stats.put("classes", classCount.isEmpty() ? 0 : classCount.get(0).get("count").asInt());
        stats.put("interfaces", interfaceCount.isEmpty() ? 0 : interfaceCount.get(0).get("count").asInt());
        stats.put("methods", methodCount.isEmpty() ? 0 : methodCount.get(0).get("count").asInt());
        stats.put("annotations", annotationCount.isEmpty() ? 0 : annotationCount.get(0).get("count").asInt());
        stats.put("extends", extendsCount.isEmpty() ? 0 : extendsCount.get(0).get("count").asInt());
        stats.put("implements", implementsCount.isEmpty() ? 0 : implementsCount.get(0).get("count").asInt());
        stats.put("calls", callsCount.isEmpty() ? 0 : callsCount.get(0).get("count").asInt());
        stats.put("contains", containsCount.isEmpty() ? 0 : containsCount.get(0).get("count").asInt());
        stats.put("hasAnnotations", hasAnnotationCount.isEmpty() ? 0 : hasAnnotationCount.get(0).get("count").asInt());

        return stats;
    }

    // ==================== 私有輔助方法 ====================

    /**
     * 提取包名
     */
    private String extractPackageName(JsonNode rootNode) {
        JsonNode packageNode = rootNode.path("packageName");
        if (packageNode.isTextual()) {
            return packageNode.asText();
        }
        return "";
    }

    /**
     * 轉換類別節點
     */
    private List<String> convertClasses(JsonNode rootNode, String sourceFile, String packageName) {
        List<String> cyphers = new ArrayList<>();

        // 從 sequenceDiagramData 中提取類別信息
        JsonNode sequenceData = rootNode.path("sequenceDiagramData");
        if (sequenceData.isObject()) {
            String className = sequenceData.path("classFqn").asText();
            if (!className.isEmpty()) {
                // 創建一個模擬的類別節點
                JsonNode classNode = createClassNodeFromSequenceData(sequenceData, className);
                String cypher = generateClassCypher(classNode, sourceFile, packageName);
                cyphers.add(cypher);
            }
        }

        return cyphers;
    }

    /**
     * 轉換介面節點
     */
    private List<String> convertInterfaces(JsonNode rootNode, String sourceFile, String packageName) {
        List<String> cyphers = new ArrayList<>();
        // 目前沒有介面信息，暫時返回空列表
        return cyphers;
    }

    /**
     * 轉換方法節點
     */
    private List<String> convertMethods(JsonNode rootNode, String sourceFile, String packageName) {
        List<String> cyphers = new ArrayList<>();

        // 從 sequenceDiagramData 中提取方法信息
        JsonNode sequenceData = rootNode.path("sequenceDiagramData");
        if (sequenceData.isObject()) {
            String className = sequenceData.path("classFqn").asText();
            JsonNode methodGroups = sequenceData.path("methodGroups");
            if (methodGroups.isArray()) {
                for (JsonNode methodGroup : methodGroups) {
                    String cypher = generateMethodCypher(methodGroup, className, sourceFile, packageName);
                    cyphers.add(cypher);
                }
            }
        }

        return cyphers;
    }

    /**
     * 轉換關係
     */
    private List<String> convertRelationships(JsonNode rootNode, String sourceFile, String packageName) {
        List<String> cyphers = new ArrayList<>();

        // 從 sequenceDiagramData 中提取關係信息
        JsonNode sequenceData = rootNode.path("sequenceDiagramData");
        if (sequenceData.isObject()) {
            String className = sequenceData.path("classFqn").asText();
            JsonNode methodGroups = sequenceData.path("methodGroups");

            if (methodGroups.isArray()) {
                for (JsonNode methodGroup : methodGroups) {
                    String methodName = methodGroup.path("methodName").asText();

                    // 轉換包含關係（類別包含方法）
                    String cypher = generateContainsCypher(className, methodName, sourceFile, packageName);
                    cyphers.add(cypher);

                    // 轉換方法調用關係
                    JsonNode interactions = methodGroup.path("interactions");
                    if (interactions.isArray()) {
                        for (JsonNode interaction : interactions) {
                            String callCypher = generateCallsCypher(className, methodName, interaction, sourceFile,
                                    packageName);
                            cyphers.add(callCypher);
                        }
                    }
                }
            }
        }

        return cyphers;
    }

    /**
     * 轉換註解節點
     */
    private List<String> convertAnnotations(JsonNode rootNode, String sourceFile, String packageName) {
        List<String> cyphers = new ArrayList<>();

        // 從 sequenceDiagramData 中提取註解信息
        JsonNode sequenceData = rootNode.path("sequenceDiagramData");
        if (sequenceData.isObject()) {
            String className = sequenceData.path("classFqn").asText();
            if (className.isEmpty()) {
                className = extractClassNameFromJson(rootNode);
            }

            // 轉換類別註解
            JsonNode classAnnotations = sequenceData.path("classAnnotations");
            if (classAnnotations.isArray()) {
                for (JsonNode annotation : classAnnotations) {
                    // 創建 HAS_ANNOTATION 關係（內部會創建 Annotation 節點）
                    String relationCypher = generateHasAnnotationCypher("Class", className, annotation, sourceFile,
                            packageName, rootNode);
                    cyphers.add(relationCypher);
                }
            }

            // 轉換方法註解
            JsonNode methodGroups = sequenceData.path("methodGroups");
            if (methodGroups.isArray()) {
                for (JsonNode methodGroup : methodGroups) {
                    String methodName = methodGroup.path("methodName").asText();
                    JsonNode methodAnnotations = methodGroup.path("annotations");
                    if (methodAnnotations.isArray()) {
                        for (JsonNode annotation : methodAnnotations) {
                            // 創建 HAS_ANNOTATION 關係（內部會創建 Annotation 節點）
                            String relationCypher = generateHasAnnotationCypher("Method", methodName, annotation,
                                    sourceFile, packageName, rootNode);
                            cyphers.add(relationCypher);
                        }
                    }
                }
            }
        }

        return cyphers;
    }

    // ==================== 節點類型判斷 ====================

    /**
     * 從 sequenceDiagramData 創建類別節點
     */
    private JsonNode createClassNodeFromSequenceData(JsonNode sequenceData, String className) {
        // 創建一個模擬的類別節點，包含基本信息
        com.fasterxml.jackson.databind.node.ObjectNode classNode = objectMapper.createObjectNode();
        classNode.put("name", className);
        classNode.put("type", "class");
        classNode.putArray("modifiers");
        classNode.put("isAbstract", false);
        classNode.put("isFinal", false);
        classNode.put("isPublic", true);
        classNode.put("isPrivate", false);
        classNode.put("isProtected", false);
        classNode.put("isStatic", false);

        com.fasterxml.jackson.databind.node.ObjectNode position = objectMapper.createObjectNode();
        position.put("line", 1);
        position.put("column", 1);
        classNode.set("position", position);

        return classNode;
    }

    // ==================== Cypher 生成方法 ====================

    private String generateClassCypher(JsonNode classNode, String sourceFile, String packageName) {
        String name = classNode.path("name").asText();
        JsonNode modifiers = classNode.path("modifiers");
        JsonNode position = classNode.path("position");

        return String.format("""
                MERGE (c:Class {name: '%s', package: '%s'})
                SET c.modifiers = %s,
                    c.isAbstract = %s,
                    c.isFinal = %s,
                    c.isPublic = %s,
                    c.isPrivate = %s,
                    c.isProtected = %s,
                    c.isStatic = %s,
                    c.sourceFile = '%s',
                    c.lineNumber = %d,
                    c.columnNumber = %d
                """,
                name,
                packageName,
                formatStringArray(modifiers),
                hasModifier(modifiers, "abstract"),
                hasModifier(modifiers, "final"),
                hasModifier(modifiers, "public"),
                hasModifier(modifiers, "private"),
                hasModifier(modifiers, "protected"),
                hasModifier(modifiers, "static"),
                sourceFile,
                position.path("line").asInt(0),
                position.path("column").asInt(0));
    }

    private String generateMethodCypher(JsonNode methodNode, String className, String sourceFile, String packageName) {
        String name = methodNode.path("methodName").asText();
        String methodSignature = methodNode.path("methodSignature").asText();
        String returnType = methodSignature.isEmpty() ? "void" : methodSignature;
        JsonNode parameters = objectMapper.createArrayNode(); // 暫時為空
        JsonNode modifiers = objectMapper.createArrayNode(); // 暫時為空
        int startLine = methodNode.path("startLineNumber").asInt(0);
        int endLine = methodNode.path("endLineNumber").asInt(0);

        return String.format("""
                MERGE (m:Method {name: '%s', className: '%s', parameters: %s})
                SET m.returnType = '%s',
                    m.modifiers = %s,
                    m.isAbstract = %s,
                    m.isFinal = %s,
                    m.isPublic = %s,
                    m.isPrivate = %s,
                    m.isProtected = %s,
                    m.isStatic = %s,
                    m.isSynchronized = %s,
                    m.isConstructor = %s,
                    m.isGetter = %s,
                    m.isSetter = %s,
                    m.sourceFile = '%s',
                    m.lineNumber = %d,
                    m.columnNumber = %d,
                    m.bodyLength = %d
                """,
                name,
                className,
                formatStringArray(parameters),
                returnType,
                formatStringArray(modifiers),
                hasModifier(modifiers, "abstract"),
                hasModifier(modifiers, "final"),
                hasModifier(modifiers, "public"),
                hasModifier(modifiers, "private"),
                hasModifier(modifiers, "protected"),
                hasModifier(modifiers, "static"),
                hasModifier(modifiers, "synchronized"),
                isConstructor(name),
                isGetter(name),
                isSetter(name),
                sourceFile,
                startLine,
                0, // column
                endLine - startLine + 1); // body length
    }

    private String generateCallsCypher(String className, String methodName, JsonNode callNode, String sourceFile,
            String packageName) {
        String targetMethod = callNode.path("methodName").asText();
        String targetClass = callNode.path("callee").asText();
        int lineNumber = callNode.path("lineNumber").asInt(0);
        JsonNode arguments = callNode.path("arguments");

        return String.format("""
                MATCH (caller:Method {name: '%s', className: '%s'})
                MATCH (callee:Method {name: '%s', className: '%s'})
                MERGE (caller)-[r:CALLS {
                    callType: '%s',
                    sourceFile: '%s',
                    lineNumber: %d,
                    columnNumber: %d,
                    argumentCount: %d
                }]->(callee)
                """,
                methodName, className,
                targetMethod, targetClass,
                "DIRECT", // 默認調用類型
                sourceFile,
                lineNumber,
                0, // column
                arguments.size());
    }

    private String generateContainsCypher(String className, String methodName, String sourceFile, String packageName) {
        return String.format("""
                MATCH (c:Class {name: '%s', package: '%s'})
                MATCH (m:Method {name: '%s', className: '%s'})
                MERGE (c)-[r:CONTAINS {sourceFile: '%s', lineNumber: 0}]->(m)
                """,
                className, packageName, methodName, className, sourceFile);
    }

    private String generateAnnotationCypher(JsonNode annotationNode, String targetType) {
        String annotationName = annotationNode.path("annotationName").asText();
        JsonNode parameters = annotationNode.path("parameters");

        return String.format("""
                MERGE (a:Annotation {name: '%s', targetType: '%s'})
                SET a.parameters = %s
                """,
                annotationName,
                targetType,
                formatStringArray(parameters));
    }

    private String generateHasAnnotationCypher(String targetType, String targetName, JsonNode annotationNode,
            String sourceFile, String packageName, JsonNode rootNode) {
        // 修正：使用正確的字段名稱
        String annotationName = annotationNode.path("annotationName").asText();
        if (annotationName.isEmpty()) {
            annotationName = annotationNode.path("simpleName").asText();
        }

        // 修正：使用正確的行號字段
        int lineNumber = annotationNode.path("startLineNumber").asInt(0);

        // 獲取註解參數
        JsonNode parameters = annotationNode.path("parameters");

        // 根據目標類型選擇正確的節點匹配條件
        String targetMatchCondition;
        if ("Class".equals(targetType)) {
            // 從完整的類名中提取簡單類名
            String simpleClassName = targetName;
            if (targetName.contains(".")) {
                simpleClassName = targetName.substring(targetName.lastIndexOf('.') + 1);
            }
            targetMatchCondition = String.format("(target:Class {name: '%s', package: '%s'})", simpleClassName,
                    packageName);
        } else if ("Method".equals(targetType)) {
            // 從 sequenceDiagramData 中提取完整的類名（classFqn）
            JsonNode sequenceData = rootNode.path("sequenceDiagramData");
            String className = sequenceData.path("classFqn").asText();
            if (className.isEmpty()) {
                className = extractClassNameFromJson(rootNode);
            }
            targetMatchCondition = String.format("(target:Method {name: '%s', className: '%s'})", targetName,
                    className);
        } else {
            targetMatchCondition = String.format("(target {name: '%s'})", targetName);
        }

        return String.format("""
                MERGE %s
                MERGE (a:Annotation {name: '%s', targetType: '%s'})
                SET a.parameters = %s
                MERGE (target)-[r:HAS_ANNOTATION {sourceFile: '%s', lineNumber: %d}]->(a)
                """,
                targetMatchCondition,
                annotationName,
                targetType,
                formatStringArray(parameters),
                sourceFile,
                lineNumber);
    }

    // ==================== 輔助方法 ====================

    /**
     * 從 JSON 節點中提取類名
     */
    private String extractClassNameFromJson(JsonNode rootNode) {
        // 從 sequenceDiagramData 中提取類名
        JsonNode sequenceData = rootNode.path("sequenceDiagramData");
        if (sequenceData.isObject()) {
            String classFqn = sequenceData.path("classFqn").asText();
            if (!classFqn.isEmpty()) {
                // 提取簡單類名（去掉包名）
                int lastDotIndex = classFqn.lastIndexOf('.');
                if (lastDotIndex >= 0) {
                    return classFqn.substring(lastDotIndex + 1);
                }
                return classFqn;
            }
        }
        return "UnknownClass";
    }

    private String formatStringArray(JsonNode arrayNode) {
        if (arrayNode.isArray()) {
            List<String> items = new ArrayList<>();
            for (JsonNode item : arrayNode) {
                items.add("'" + item.asText() + "'");
            }
            return "[" + String.join(", ", items) + "]";
        }
        return "[]";
    }

    private boolean hasModifier(JsonNode modifiers, String modifier) {
        if (modifiers.isArray()) {
            for (JsonNode mod : modifiers) {
                if (modifier.equals(mod.asText())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isConstructor(String methodName) {
        // 簡單的構造函數判斷邏輯
        return methodName.contains("Constructor") || methodName.equals("init");
    }

    private boolean isGetter(String methodName) {
        return methodName.startsWith("get") && methodName.length() > 3;
    }

    private boolean isSetter(String methodName) {
        return methodName.startsWith("set") && methodName.length() > 3;
    }
}
