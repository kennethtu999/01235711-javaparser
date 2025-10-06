package kai.javaparser.astgrapth.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import kai.javaparser.ast.entity.Neo4jAnnotationNode;
import kai.javaparser.ast.entity.Neo4jClassNode;
import kai.javaparser.ast.entity.Neo4jInterfaceNode;
import kai.javaparser.ast.entity.Neo4jMethodNode;
import kai.javaparser.ast.repository.AstNodeRepositoryService;
import kai.javaparser.configuration.AppConfig;
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
    private AstNodeRepositoryService astNodeRepositoryService;

    @Autowired
    private FileSystemAstRepository astRepository;

    @Autowired
    private AppConfig appConfig;

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
        List<Neo4jClassNode> classNodes = convertClassesToEntities(rootNode, sourceFile, packageName);
        for (Neo4jClassNode classNode : classNodes) {
            astNodeRepositoryService.saveClass(classNode);
            nodesCreated++;
        }

        // 轉換介面
        List<Neo4jInterfaceNode> interfaceNodes = convertInterfacesToEntities(rootNode, sourceFile, packageName);
        for (Neo4jInterfaceNode interfaceNode : interfaceNodes) {
            astNodeRepositoryService.saveInterface(interfaceNode);
            nodesCreated++;
        }

        // 轉換方法
        List<Neo4jMethodNode> methodNodes = convertMethodsToEntities(rootNode, sourceFile, packageName);
        for (Neo4jMethodNode methodNode : methodNodes) {
            astNodeRepositoryService.saveMethod(methodNode);
            nodesCreated++;
        }

        // 轉換註解
        List<Neo4jAnnotationNode> annotationNodes = convertAnnotationsToEntities(rootNode, sourceFile, packageName);
        for (Neo4jAnnotationNode annotationNode : annotationNodes) {
            astNodeRepositoryService.saveAnnotation(annotationNode);
            nodesCreated++;
        }

        // 建立關係
        relationshipsCreated = buildRelationships(rootNode, sourceFile, packageName);

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
        astNodeRepositoryService.clearAllAstData();
        log.info("AST 數據清理完成");
    }

    /**
     * 獲取圖數據庫中的 AST 統計信息
     * 
     * @return 統計信息
     */
    public Map<String, Object> getAstStatistics() {
        Map<String, Long> stats = astNodeRepositoryService.getStatistics();
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Long> entry : stats.entrySet()) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
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
     * 轉換類別節點為實體
     */
    private List<Neo4jClassNode> convertClassesToEntities(JsonNode rootNode, String sourceFile, String packageName) {
        List<Neo4jClassNode> classNodes = new ArrayList<>();

        // 從 sequenceDiagramData 中提取類別信息
        JsonNode sequenceData = rootNode.path("sequenceDiagramData");
        if (sequenceData.isObject()) {
            String className = sequenceData.path("classFqn").asText();
            if (!className.isEmpty()) {
                Neo4jClassNode classNode = createClassEntityFromSequenceData(sequenceData, className, sourceFile,
                        packageName);
                classNodes.add(classNode);
            }
        }

        return classNodes;
    }

    /**
     * 轉換介面節點為實體
     */
    private List<Neo4jInterfaceNode> convertInterfacesToEntities(JsonNode rootNode, String sourceFile,
            String packageName) {
        List<Neo4jInterfaceNode> interfaceNodes = new ArrayList<>();
        // 目前沒有介面信息，暫時返回空列表
        return interfaceNodes;
    }

    /**
     * 轉換方法節點為實體
     */
    private List<Neo4jMethodNode> convertMethodsToEntities(JsonNode rootNode, String sourceFile, String packageName) {
        List<Neo4jMethodNode> methodNodes = new ArrayList<>();

        // 從 sequenceDiagramData 中提取方法信息
        JsonNode sequenceData = rootNode.path("sequenceDiagramData");
        if (sequenceData.isObject()) {
            String className = sequenceData.path("classFqn").asText();
            JsonNode methodGroups = sequenceData.path("methodGroups");
            if (methodGroups.isArray()) {
                for (JsonNode methodGroup : methodGroups) {
                    Neo4jMethodNode methodNode = createMethodEntityFromSequenceData(methodGroup, className, sourceFile,
                            packageName);
                    methodNodes.add(methodNode);
                }
            }
        }

        return methodNodes;
    }

    /**
     * 建立關係
     */
    private long buildRelationships(JsonNode rootNode, String sourceFile, String packageName) {
        long relationshipsCreated = 0;

        // 從 sequenceDiagramData 中提取關係信息
        JsonNode sequenceData = rootNode.path("sequenceDiagramData");
        if (sequenceData.isObject()) {
            String className = sequenceData.path("classFqn").asText();
            JsonNode methodGroups = sequenceData.path("methodGroups");

            if (methodGroups.isArray()) {
                for (JsonNode methodGroup : methodGroups) {
                    String methodName = methodGroup.path("methodName").asText();

                    // 建立包含關係（類別包含方法）
                    if (buildContainsRelationship(className, methodName, packageName)) {
                        relationshipsCreated++;
                    }

                    // 建立方法調用關係
                    JsonNode interactions = methodGroup.path("interactions");
                    if (interactions.isArray()) {
                        for (JsonNode interaction : interactions) {
                            if (buildCallsRelationship(className, methodName, interaction, packageName)) {
                                relationshipsCreated++;
                            }
                        }
                    }
                }
            }

            // 建立註解關係
            relationshipsCreated += buildAnnotationRelationships(sequenceData, packageName);
        }

        return relationshipsCreated;
    }

    /**
     * 轉換註解節點為實體
     */
    private List<Neo4jAnnotationNode> convertAnnotationsToEntities(JsonNode rootNode, String sourceFile,
            String packageName) {
        List<Neo4jAnnotationNode> annotationNodes = new ArrayList<>();

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
                    String annotationName = annotation.path("annotationName").asText();
                    if (annotationName.isEmpty()) {
                        annotationName = annotation.path("simpleName").asText();
                    }

                    // 檢查是否應該排除此註解
                    if (!shouldExcludeAnnotation(annotationName)) {
                        Neo4jAnnotationNode annotationNode = createAnnotationEntityFromData(annotation, "Class",
                                sourceFile, packageName);
                        annotationNodes.add(annotationNode);
                    } else {
                        log.debug("排除類別註解: {}", annotationName);
                    }
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
                            String annotationName = annotation.path("annotationName").asText();
                            if (annotationName.isEmpty()) {
                                annotationName = annotation.path("simpleName").asText();
                            }

                            // 檢查是否應該排除此註解
                            if (!shouldExcludeAnnotation(annotationName)) {
                                Neo4jAnnotationNode annotationNode = createAnnotationEntityFromData(annotation,
                                        "Method", sourceFile, packageName);
                                annotationNodes.add(annotationNode);
                            } else {
                                log.debug("排除方法註解: {} on method {}", annotationName, methodName);
                            }
                        }
                    }
                }
            }
        }

        return annotationNodes;
    }

    // ==================== 節點類型判斷 ====================

    /**
     * 從 sequenceDiagramData 創建類別實體
     */
    private Neo4jClassNode createClassEntityFromSequenceData(JsonNode sequenceData, String className, String sourceFile,
            String packageName) {
        Neo4jClassNode classNode = new Neo4jClassNode();
        classNode.setId(generateClassId(className, packageName));
        classNode.setName(className);
        classNode.setPackageName(packageName);
        classNode.setSourceFile(sourceFile);
        classNode.setLineNumber(1);
        classNode.setColumnNumber(1);
        classNode.setIsAbstract(false);
        classNode.setIsFinal(false);
        classNode.setIsPublic(true);
        classNode.setIsPrivate(false);
        classNode.setIsProtected(false);
        classNode.setIsStatic(false);
        classNode.setModifiers(new ArrayList<>());

        return classNode;
    }

    /**
     * 從 sequenceDiagramData 創建方法實體
     */
    private Neo4jMethodNode createMethodEntityFromSequenceData(JsonNode methodGroup, String className,
            String sourceFile, String packageName) {
        Neo4jMethodNode methodNode = new Neo4jMethodNode();
        String methodName = methodGroup.path("methodName").asText();
        String methodSignature = methodGroup.path("methodSignature").asText();
        int startLine = methodGroup.path("startLineNumber").asInt(0);
        int endLine = methodGroup.path("endLineNumber").asInt(0);

        methodNode.setId(generateMethodId(methodName, className));
        methodNode.setName(methodName);
        methodNode.setClassName(className);
        methodNode.setPackageName(packageName);
        methodNode.setReturnType(methodSignature.isEmpty() ? "void" : methodSignature);
        methodNode.setSourceFile(sourceFile);
        methodNode.setLineNumber(startLine);
        methodNode.setColumnNumber(0);
        methodNode.setBodyLength(endLine - startLine + 1);
        methodNode.setIsConstructor(isConstructor(methodName));
        methodNode.setIsGetter(isGetter(methodName));
        methodNode.setIsSetter(isSetter(methodName));
        methodNode.setParameters(new ArrayList<>());
        methodNode.setModifiers(new ArrayList<>());

        return methodNode;
    }

    /**
     * 從註解數據創建註解實體
     */
    private Neo4jAnnotationNode createAnnotationEntityFromData(JsonNode annotation, String targetType,
            String sourceFile, String packageName) {
        Neo4jAnnotationNode annotationNode = new Neo4jAnnotationNode();
        String annotationName = annotation.path("annotationName").asText();
        if (annotationName.isEmpty()) {
            annotationName = annotation.path("simpleName").asText();
        }
        int lineNumber = annotation.path("startLineNumber").asInt(0);
        JsonNode parameters = annotation.path("parameters");

        annotationNode.setId(generateAnnotationId(annotationName, targetType));
        annotationNode.setName(annotationName);
        annotationNode.setTargetType(targetType);
        annotationNode.setSourceFile(sourceFile);
        annotationNode.setLineNumber(lineNumber);
        annotationNode.setColumnNumber(0);
        annotationNode.setParameters(convertParametersToList(parameters));

        return annotationNode;
    }

    /**
     * 建立包含關係（類別包含方法）
     */
    private boolean buildContainsRelationship(String className, String methodName, String packageName) {
        try {
            Optional<Neo4jClassNode> classOpt = astNodeRepositoryService.findClassByNameAndPackage(className,
                    packageName);
            Optional<Neo4jMethodNode> methodOpt = astNodeRepositoryService.findMethodByNameAndClass(methodName,
                    className);

            if (classOpt.isPresent() && methodOpt.isPresent()) {
                Neo4jClassNode classNode = classOpt.get();
                Neo4jMethodNode methodNode = methodOpt.get();
                classNode.addContainsMethod(methodNode);
                astNodeRepositoryService.saveClass(classNode);
                return true;
            }
        } catch (Exception e) {
            log.error("建立包含關係失敗: {} -> {}", className, methodName, e);
        }
        return false;
    }

    /**
     * 建立調用關係（方法調用方法）
     */
    private boolean buildCallsRelationship(String className, String methodName, JsonNode interaction,
            String packageName) {
        try {
            String targetMethod = interaction.path("methodName").asText();
            String targetClass = interaction.path("callee").asText();

            Optional<Neo4jMethodNode> callerOpt = astNodeRepositoryService.findMethodByNameAndClass(methodName,
                    className);
            Optional<Neo4jMethodNode> calleeOpt = astNodeRepositoryService.findMethodByNameAndClass(targetMethod,
                    targetClass);

            if (callerOpt.isPresent() && calleeOpt.isPresent()) {
                Neo4jMethodNode caller = callerOpt.get();
                Neo4jMethodNode callee = calleeOpt.get();
                caller.addCalls(callee);
                astNodeRepositoryService.saveMethod(caller);
                return true;
            }
        } catch (Exception e) {
            log.error("建立調用關係失敗: {} -> {}", methodName, interaction.path("methodName").asText(), e);
        }
        return false;
    }

    /**
     * 建立註解關係
     */
    private long buildAnnotationRelationships(JsonNode sequenceData, String packageName) {
        long relationshipsCreated = 0;
        String className = sequenceData.path("classFqn").asText();

        // 建立類別註解關係
        JsonNode classAnnotations = sequenceData.path("classAnnotations");
        if (classAnnotations.isArray()) {
            for (JsonNode annotation : classAnnotations) {
                String annotationName = annotation.path("annotationName").asText();
                if (annotationName.isEmpty()) {
                    annotationName = annotation.path("simpleName").asText();
                }

                if (!shouldExcludeAnnotation(annotationName)) {
                    if (buildClassAnnotationRelationship(className, annotationName, packageName)) {
                        relationshipsCreated++;
                    }
                }
            }
        }

        // 建立方法註解關係
        JsonNode methodGroups = sequenceData.path("methodGroups");
        if (methodGroups.isArray()) {
            for (JsonNode methodGroup : methodGroups) {
                String methodName = methodGroup.path("methodName").asText();
                JsonNode methodAnnotations = methodGroup.path("annotations");
                if (methodAnnotations.isArray()) {
                    for (JsonNode annotation : methodAnnotations) {
                        String annotationName = annotation.path("annotationName").asText();
                        if (annotationName.isEmpty()) {
                            annotationName = annotation.path("simpleName").asText();
                        }

                        if (!shouldExcludeAnnotation(annotationName)) {
                            if (buildMethodAnnotationRelationship(className, methodName, annotationName, packageName)) {
                                relationshipsCreated++;
                            }
                        }
                    }
                }
            }
        }

        return relationshipsCreated;
    }

    /**
     * 建立類別註解關係
     */
    private boolean buildClassAnnotationRelationship(String className, String annotationName, String packageName) {
        try {
            Optional<Neo4jClassNode> classOpt = astNodeRepositoryService.findClassByNameAndPackage(className,
                    packageName);
            Optional<Neo4jAnnotationNode> annotationOpt = astNodeRepositoryService
                    .findAnnotationByNameAndTargetType(annotationName, "Class");

            if (classOpt.isPresent() && annotationOpt.isPresent()) {
                Neo4jClassNode classNode = classOpt.get();
                Neo4jAnnotationNode annotationNode = annotationOpt.get();
                classNode.addHasAnnotation(annotationNode);
                astNodeRepositoryService.saveClass(classNode);
                return true;
            }
        } catch (Exception e) {
            log.error("建立類別註解關係失敗: {} -> {}", className, annotationName, e);
        }
        return false;
    }

    /**
     * 建立方法註解關係
     */
    private boolean buildMethodAnnotationRelationship(String className, String methodName, String annotationName,
            String packageName) {
        try {
            Optional<Neo4jMethodNode> methodOpt = astNodeRepositoryService.findMethodByNameAndClass(methodName,
                    className);
            Optional<Neo4jAnnotationNode> annotationOpt = astNodeRepositoryService
                    .findAnnotationByNameAndTargetType(annotationName, "Method");

            if (methodOpt.isPresent() && annotationOpt.isPresent()) {
                Neo4jMethodNode methodNode = methodOpt.get();
                Neo4jAnnotationNode annotationNode = annotationOpt.get();
                methodNode.addHasAnnotation(annotationNode);
                astNodeRepositoryService.saveMethod(methodNode);
                return true;
            }
        } catch (Exception e) {
            log.error("建立方法註解關係失敗: {} -> {} -> {}", className, methodName, annotationName, e);
        }
        return false;
    }

    // ==================== ID 生成方法 ====================

    /**
     * 生成類別 ID
     */
    private String generateClassId(String className, String packageName) {
        return String.format("class_%s_%s", packageName.replace(".", "_"), className);
    }

    /**
     * 生成方法 ID
     */
    private String generateMethodId(String methodName, String className) {
        return String.format("method_%s_%s", className, methodName);
    }

    /**
     * 生成註解 ID
     */
    private String generateAnnotationId(String annotationName, String targetType) {
        return String.format("annotation_%s_%s", targetType, annotationName);
    }

    /**
     * 將參數節點轉換為字符串列表
     */
    private List<String> convertParametersToList(JsonNode parameters) {
        List<String> paramList = new ArrayList<>();
        if (parameters.isArray()) {
            for (JsonNode param : parameters) {
                paramList.add(param.asText());
            }
        }
        return paramList;
    }

    // ==================== 輔助方法 ====================

    /**
     * 檢查註解是否應該被排除
     * 
     * @param annotationName 註解名稱
     * @return 如果應該排除則返回 true
     */
    private boolean shouldExcludeAnnotation(String annotationName) {
        if (annotationName == null || annotationName.trim().isEmpty()) {
            return false;
        }

        // 獲取排除的註解列表
        Set<String> excludedAnnotations = appConfig.getGraph().getExclude().getExcludedAnnotations();

        // 檢查是否在排除列表中
        return excludedAnnotations.contains(annotationName.trim());
    }

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
