package kai.javaparser.astgraph.service;

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

import kai.javaparser.ast.entity.Neo4jAnnotationNode;
import kai.javaparser.ast.entity.Neo4jClassNode;
import kai.javaparser.ast.entity.Neo4jInterfaceNode;
import kai.javaparser.ast.entity.Neo4jMethodNode;
import kai.javaparser.ast.repository.AstNodeRepositoryService;
import kai.javaparser.astgraph.service.handler.ClassAstGraphHandler;
import kai.javaparser.astgraph.service.handler.InterfaceAstGraphHandler;
import kai.javaparser.astgraph.util.AstToGraphUtil;
import kai.javaparser.repository.FileSystemAstRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * AST 到圖數據庫轉換服務 V2
 * 使用 Handler 模式重構，支持不同類型節點的獨立處理
 */
@Slf4j
@Service
public class AstToGraphService {

    @Autowired
    private AstNodeRepositoryService astNodeRepositoryService;

    @Autowired
    private FileSystemAstRepository astRepository;

    @Autowired
    private kai.javaparser.ast.service.Neo4jIndexService neo4jIndexService;

    // Handler 實例
    @Autowired
    private ClassAstGraphHandler classHandler;

    @Autowired
    private InterfaceAstGraphHandler interfaceHandler;

    @Autowired
    private AstToGraphUtil astToGraphUtil;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 轉換狀態追蹤
    private volatile boolean isConverting = false;
    private volatile String conversionStatus = "IDLE";
    private volatile String lastErrorMessage = null;
    private volatile long conversionStartTime = 0;
    private volatile long conversionEndTime = 0;

    /**
     * 轉換單個 AST JSON 文件
     */
    public Map<String, Object> convertSingleAstFile(Path jsonFile) throws IOException {
        log.debug("開始轉換文件: {}", jsonFile);

        // 確保索引存在
        try {
            neo4jIndexService.createAllIndexes();
        } catch (Exception e) {
            log.warn("創建索引失敗，繼續執行轉換: {}", e.getMessage());
        }

        // 讀取 JSON 文件
        String jsonContent = Files.readString(jsonFile);
        JsonNode rootNode = objectMapper.readTree(jsonContent);

        // 提取文件信息
        String sourceFile = jsonFile.toString();
        String packageName = astToGraphUtil.extractPackageName(rootNode);

        long nodesCreated = 0;
        long relationshipsCreated = 0;

        // 使用 Handler 轉換節點
        List<Neo4jClassNode> allClassNodes = new ArrayList<>();
        List<Neo4jInterfaceNode> allInterfaceNodes = new ArrayList<>();
        List<Neo4jMethodNode> allMethodNodes = new ArrayList<>();
        List<Neo4jAnnotationNode> allAnnotationNodes = new ArrayList<>();

        // 轉換類別
        List<Neo4jClassNode> classNodes = classHandler.convertToEntities(rootNode, sourceFile, packageName);
        if (!classNodes.isEmpty()) {
            int classesInserted = astNodeRepositoryService.bulkSaveClasses(classNodes);
            nodesCreated += classesInserted;
            allClassNodes.addAll(classNodes);
            log.debug("批量插入 {} 個類別節點", classesInserted);
        }

        // 轉換介面
        List<Neo4jInterfaceNode> interfaceNodes = interfaceHandler.convertToEntities(rootNode, sourceFile, packageName);
        if (!interfaceNodes.isEmpty()) {
            int interfacesInserted = astNodeRepositoryService.bulkSaveInterfaces(interfaceNodes);
            nodesCreated += interfacesInserted;
            allInterfaceNodes.addAll(interfaceNodes);
            log.debug("批量插入 {} 個介面節點", interfacesInserted);
        }

        // 轉換方法
        List<Neo4jMethodNode> methodNodes = convertMethodsToEntities(rootNode, sourceFile, packageName);
        if (!methodNodes.isEmpty()) {
            int methodsInserted = astNodeRepositoryService.bulkSaveMethods(methodNodes);
            nodesCreated += methodsInserted;
            allMethodNodes.addAll(methodNodes);
            log.debug("批量插入 {} 個方法節點", methodsInserted);
        }

        // 轉換註解
        List<Neo4jAnnotationNode> annotationNodes = convertAnnotationsToEntities(rootNode, sourceFile, packageName);
        if (!annotationNodes.isEmpty()) {
            int annotationsInserted = astNodeRepositoryService.bulkSaveAnnotations(annotationNodes);
            nodesCreated += annotationsInserted;
            allAnnotationNodes.addAll(annotationNodes);
            log.debug("批量插入 {} 個註解節點", annotationsInserted);
        }

        // 使用 Handler 建立關係
        relationshipsCreated += establishRelationships(rootNode, allClassNodes, allInterfaceNodes, allMethodNodes,
                allAnnotationNodes);

        return Map.of(
                "sourceFile", sourceFile,
                "package", packageName,
                "nodes", nodesCreated,
                "relationships", relationshipsCreated);
    }

    /**
     * 批量轉換所有 AST 文件
     */
    public CompletableFuture<Map<String, Object>> convertAllAstToGraphBulk() {
        return CompletableFuture.supplyAsync(() -> {
            log.info("開始批量轉換所有 AST JSON 文件（Handler 模式）");

            // 更新轉換狀態
            isConverting = true;
            conversionStatus = "PROCESSING";
            conversionStartTime = System.currentTimeMillis();
            lastErrorMessage = null;

            Map<String, Object> statistics = new HashMap<>();
            int totalFiles = 0;
            int successFiles = 0;
            int errorFiles = 0;
            long totalNodes = 0;
            long totalRelationships = 0;

            try {
                // 獲取 AST 目錄
                Path astPath = astRepository.getAstJsonDir();
                if (astPath == null || !Files.exists(astPath)) {
                    log.warn("AST 目錄未初始化或不存在");
                    return astToGraphUtil.createErrorResponse("AST 目錄未初始化或不存在");
                }

                // 收集所有節點和關係
                List<Neo4jClassNode> allClassNodes = new ArrayList<>();
                List<Neo4jInterfaceNode> allInterfaceNodes = new ArrayList<>();
                List<Neo4jMethodNode> allMethodNodes = new ArrayList<>();
                List<Neo4jAnnotationNode> allAnnotationNodes = new ArrayList<>();
                Map<String, List<String>> allClassMethodRelations = new HashMap<>();
                Map<String, List<String>> allInterfaceMethodRelations = new HashMap<>();
                Map<String, List<String>> allMethodCallRelations = new HashMap<>();
                Map<String, List<String>> allClassAnnotationRelations = new HashMap<>();
                Map<String, List<String>> allInterfaceAnnotationRelations = new HashMap<>();
                Map<String, List<String>> allMethodAnnotationRelations = new HashMap<>();

                // 遍歷所有 JSON 文件
                try (Stream<Path> paths = Files.walk(astPath)) {
                    List<Path> jsonFiles = paths
                            .filter(Files::isRegularFile)
                            .filter(path -> path.toString().endsWith(".json"))
                            .toList();

                    totalFiles = jsonFiles.size();
                    log.info("找到 {} 個 JSON 文件，開始批量處理", totalFiles);

                    for (Path jsonFile : jsonFiles) {
                        try {
                            // 讀取 JSON 文件
                            String jsonContent = Files.readString(jsonFile);
                            JsonNode rootNode = objectMapper.readTree(jsonContent);

                            // 提取文件信息
                            String sourceFile = jsonFile.toString();
                            String packageName = astToGraphUtil.extractPackageName(rootNode);

                            // 使用 Handler 轉換節點
                            List<Neo4jClassNode> classNodes = classHandler.convertToEntities(rootNode, sourceFile,
                                    packageName);
                            List<Neo4jInterfaceNode> interfaceNodes = interfaceHandler.convertToEntities(rootNode,
                                    sourceFile, packageName);
                            List<Neo4jMethodNode> methodNodes = convertMethodsToEntities(rootNode, sourceFile,
                                    packageName);
                            List<Neo4jAnnotationNode> annotationNodes = convertAnnotationsToEntities(rootNode,
                                    sourceFile, packageName);

                            // 收集節點
                            allClassNodes.addAll(classNodes);
                            allInterfaceNodes.addAll(interfaceNodes);
                            allMethodNodes.addAll(methodNodes);
                            allAnnotationNodes.addAll(annotationNodes);

                            // 使用 Handler 收集關係
                            Map<String, List<String>> classMethodRelations = classHandler
                                    .extractMethodRelations(rootNode, classNodes, methodNodes);
                            Map<String, List<String>> interfaceMethodRelations = interfaceHandler
                                    .extractMethodRelations(rootNode, interfaceNodes, methodNodes);
                            Map<String, List<String>> methodCallRelations = extractMethodCallRelations(rootNode,
                                    methodNodes);

                            Map<String, List<String>> classAnnotationRelations = classHandler
                                    .extractAnnotationRelations(rootNode, classNodes, annotationNodes);
                            Map<String, List<String>> interfaceAnnotationRelations = interfaceHandler
                                    .extractAnnotationRelations(rootNode, interfaceNodes, annotationNodes);
                            Map<String, List<String>> methodAnnotationRelations = extractMethodAnnotationRelations(
                                    rootNode, methodNodes, annotationNodes);

                            allClassMethodRelations.putAll(classMethodRelations);
                            allInterfaceMethodRelations.putAll(interfaceMethodRelations);
                            allMethodCallRelations.putAll(methodCallRelations);

                            allClassAnnotationRelations.putAll(classAnnotationRelations);
                            allInterfaceAnnotationRelations.putAll(interfaceAnnotationRelations);
                            allMethodAnnotationRelations.putAll(methodAnnotationRelations);

                            successFiles++;
                            log.debug("成功處理文件: {}", jsonFile.getFileName());

                        } catch (Exception e) {
                            errorFiles++;
                            log.error("處理文件失敗: {}", jsonFile, e);
                        }
                    }
                }

                // 批量保存介面節點
                int interfacesInserted = 0;
                if (!allInterfaceNodes.isEmpty()) {
                    interfacesInserted = astNodeRepositoryService.bulkSaveInterfaces(allInterfaceNodes);
                    log.info("批量保存 {} 個介面節點", interfacesInserted);
                }

                // 批量保存註解節點
                int annotationsInserted = 0;
                if (!allAnnotationNodes.isEmpty()) {
                    annotationsInserted = astNodeRepositoryService.bulkSaveAnnotations(allAnnotationNodes);
                    log.info("批量保存 {} 個註解節點", annotationsInserted);
                }

                // 使用異步批量處理
                log.info("開始異步批量處理，類別: {}, 方法: {}", allClassNodes.size(), allMethodNodes.size());

                CompletableFuture<kai.javaparser.ast.service.BulkNeo4jService.BulkOperationResult> bulkResult = astNodeRepositoryService
                        .asyncBulkProcess(
                                allClassNodes,
                                allMethodNodes,
                                allClassMethodRelations,
                                allMethodCallRelations);

                // 等待批量處理完成
                kai.javaparser.ast.service.BulkNeo4jService.BulkOperationResult result = bulkResult.get();

                // 處理註解關係
                int classAnnotationRelationsCreated = 0;
                int interfaceAnnotationRelationsCreated = 0;
                int methodAnnotationRelationsCreated = 0;

                if (!allClassAnnotationRelations.isEmpty()) {
                    classAnnotationRelationsCreated = astNodeRepositoryService
                            .bulkCreateClassAnnotationRelations(allClassAnnotationRelations);
                    log.info("批量建立 {} 個類別與註解的關係", classAnnotationRelationsCreated);
                }

                if (!allInterfaceAnnotationRelations.isEmpty()) {
                    interfaceAnnotationRelationsCreated = astNodeRepositoryService
                            .bulkCreateInterfaceAnnotationRelations(allInterfaceAnnotationRelations);
                    log.info("批量建立 {} 個介面與註解的關係", interfaceAnnotationRelationsCreated);
                }

                if (!allMethodAnnotationRelations.isEmpty()) {
                    methodAnnotationRelationsCreated = astNodeRepositoryService
                            .bulkCreateMethodAnnotationRelations(allMethodAnnotationRelations);
                    log.info("批量建立 {} 個方法與註解的關係", methodAnnotationRelationsCreated);
                }

                totalNodes = result.classesInserted + result.methodsInserted + interfacesInserted + annotationsInserted;
                totalRelationships = result.classMethodRelationsCreated + result.methodCallRelationsCreated +
                        classAnnotationRelationsCreated + interfaceAnnotationRelationsCreated
                        + methodAnnotationRelationsCreated;

                statistics.put("success", true);
                statistics.put("message", "批量轉換完成");
                statistics.put("totalFiles", totalFiles);
                statistics.put("successFiles", successFiles);
                statistics.put("errorFiles", errorFiles);
                statistics.put("totalNodes", totalNodes);
                statistics.put("totalRelationships", totalRelationships);
                statistics.put("classesInserted", result.classesInserted);
                statistics.put("methodsInserted", result.methodsInserted);
                statistics.put("classMethodRelationsCreated", result.classMethodRelationsCreated);
                statistics.put("methodCallRelationsCreated", result.methodCallRelationsCreated);

                log.info("批量轉換完成 - 總文件: {}, 成功: {}, 失敗: {}, 節點: {}, 關係: {}",
                        totalFiles, successFiles, errorFiles, totalNodes, totalRelationships);

                // 更新轉換狀態為完成
                isConverting = false;
                conversionStatus = "COMPLETED";
                conversionEndTime = System.currentTimeMillis();

            } catch (Exception e) {
                log.error("批量轉換過程中發生錯誤", e);
                statistics = astToGraphUtil.createErrorResponse("批量轉換失敗: " + e.getMessage());

                // 更新轉換狀態為失敗
                isConverting = false;
                conversionStatus = "FAILED";
                conversionEndTime = System.currentTimeMillis();
                lastErrorMessage = e.getMessage();
            }

            return statistics;
        });
    }

    /**
     * 建立關係
     */
    private long establishRelationships(JsonNode rootNode, List<Neo4jClassNode> classNodes,
            List<Neo4jInterfaceNode> interfaceNodes, List<Neo4jMethodNode> methodNodes,
            List<Neo4jAnnotationNode> annotationNodes) {
        long relationshipsCreated = 0;

        // 建立類別與方法的關係
        if (!classNodes.isEmpty() && !methodNodes.isEmpty()) {
            Map<String, List<String>> classMethodRelations = classHandler.extractMethodRelations(rootNode, classNodes,
                    methodNodes);
            if (!classMethodRelations.isEmpty()) {
                int classMethodRelationsCreated = astNodeRepositoryService
                        .bulkCreateClassMethodRelations(classMethodRelations);
                relationshipsCreated += classMethodRelationsCreated;
                log.debug("批量建立 {} 個類別與方法的關係", classMethodRelationsCreated);
            }
        }

        // 建立介面與方法的關係
        if (!interfaceNodes.isEmpty() && !methodNodes.isEmpty()) {
            Map<String, List<String>> interfaceMethodRelations = interfaceHandler.extractMethodRelations(rootNode,
                    interfaceNodes, methodNodes);
            if (!interfaceMethodRelations.isEmpty()) {
                int interfaceMethodRelationsCreated = astNodeRepositoryService
                        .bulkCreateInterfaceMethodRelations(interfaceMethodRelations);
                relationshipsCreated += interfaceMethodRelationsCreated;
                log.debug("批量建立 {} 個介面與方法的關係", interfaceMethodRelationsCreated);
            }
        }

        // 建立方法呼叫關係
        if (!methodNodes.isEmpty()) {
            Map<String, List<String>> methodCallRelations = extractMethodCallRelations(rootNode, methodNodes);
            if (!methodCallRelations.isEmpty()) {
                int methodCallRelationsCreated = astNodeRepositoryService
                        .bulkCreateMethodCallRelations(methodCallRelations);
                relationshipsCreated += methodCallRelationsCreated;
                log.debug("批量建立 {} 個方法呼叫關係", methodCallRelationsCreated);
            }
        }

        // 建立註解關係
        if (!annotationNodes.isEmpty()) {
            // 建立類別與註解的關係
            Map<String, List<String>> classAnnotationRelations = classHandler.extractAnnotationRelations(rootNode,
                    classNodes, annotationNodes);
            if (!classAnnotationRelations.isEmpty()) {
                int classAnnotationRelationsCreated = astNodeRepositoryService
                        .bulkCreateClassAnnotationRelations(classAnnotationRelations);
                relationshipsCreated += classAnnotationRelationsCreated;
                log.debug("批量建立 {} 個類別與註解的關係", classAnnotationRelationsCreated);
            }

            // 建立介面與註解的關係
            Map<String, List<String>> interfaceAnnotationRelations = interfaceHandler
                    .extractAnnotationRelations(rootNode, interfaceNodes, annotationNodes);
            if (!interfaceAnnotationRelations.isEmpty()) {
                int interfaceAnnotationRelationsCreated = astNodeRepositoryService
                        .bulkCreateInterfaceAnnotationRelations(interfaceAnnotationRelations);
                relationshipsCreated += interfaceAnnotationRelationsCreated;
                log.debug("批量建立 {} 個介面與註解的關係", interfaceAnnotationRelationsCreated);
            }

            // 建立方法與註解的關係
            Map<String, List<String>> methodAnnotationRelations = extractMethodAnnotationRelations(rootNode,
                    methodNodes, annotationNodes);
            if (!methodAnnotationRelations.isEmpty()) {
                int methodAnnotationRelationsCreated = astNodeRepositoryService
                        .bulkCreateMethodAnnotationRelations(methodAnnotationRelations);
                relationshipsCreated += methodAnnotationRelationsCreated;
                log.debug("批量建立 {} 個方法與註解的關係", methodAnnotationRelationsCreated);
            }
        }

        return relationshipsCreated;
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
     * 獲取轉換處理狀態
     */
    public Map<String, Object> getConversionStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("isConverting", isConverting);
        status.put("status", conversionStatus);
        status.put("startTime", conversionStartTime);
        status.put("endTime", conversionEndTime);

        if (conversionStartTime > 0) {
            long elapsedTime = (conversionEndTime > 0 ? conversionEndTime : System.currentTimeMillis())
                    - conversionStartTime;
            status.put("elapsedTime", elapsedTime);
        }

        if (lastErrorMessage != null) {
            status.put("lastError", lastErrorMessage);
        }

        return status;
    }

    /**
     * 獲取圖數據庫中的 AST 統計信息
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
     * 轉換方法節點為實體
     */
    private List<Neo4jMethodNode> convertMethodsToEntities(JsonNode rootNode, String sourceFile, String packageName) {
        List<Neo4jMethodNode> methodNodes = new ArrayList<>();

        JsonNode sequenceData = rootNode.path("sequenceDiagramData");
        if (sequenceData.isObject()) {
            String className = sequenceData.path("classFqn").asText();
            JsonNode methodGroups = sequenceData.path("methodGroups");
            if (methodGroups.isArray()) {
                List<Neo4jMethodNode> allMethodNodes = new ArrayList<>();
                for (JsonNode methodGroup : methodGroups) {
                    Neo4jMethodNode methodNode = astToGraphUtil.createMethodEntityFromSequenceData(methodGroup,
                            className, sourceFile,
                            packageName);
                    allMethodNodes.add(methodNode);
                }

                // 應用過濾邏輯
                for (Neo4jMethodNode methodNode : allMethodNodes) {
                    String methodName = methodNode.getName();
                    if (!astToGraphUtil.shouldFilterMethod(methodName, className, allMethodNodes)) {
                        methodNodes.add(methodNode);
                        log.debug("保留方法: {} (類別: {})", methodName, className);
                    } else {
                        log.debug("過濾方法: {} (類別: {})", methodName, className);
                    }
                }
            }
        }

        log.info("方法過濾完成 - 過濾後方法數: {}", methodNodes.size());
        return methodNodes;
    }

    /**
     * 轉換註解節點為實體
     */
    private List<Neo4jAnnotationNode> convertAnnotationsToEntities(JsonNode rootNode, String sourceFile,
            String packageName) {
        List<Neo4jAnnotationNode> annotationNodes = new ArrayList<>();

        JsonNode sequenceData = rootNode.path("sequenceDiagramData");
        if (sequenceData.isObject()) {
            String className = sequenceData.path("classFqn").asText();
            String classType = sequenceData.path("classType").asText();
            if (className.isEmpty()) {
                className = astToGraphUtil.extractClassNameFromJson(rootNode);
            }

            // 轉換類別/介面註解
            JsonNode classAnnotations = sequenceData.path("classAnnotations");
            if (classAnnotations.isArray()) {
                for (JsonNode annotation : classAnnotations) {
                    String annotationName = annotation.path("annotationName").asText();
                    if (annotationName.isEmpty()) {
                        annotationName = annotation.path("simpleName").asText();
                    }

                    if (!astToGraphUtil.shouldExcludeAnnotation(annotationName)) {
                        // 根據類別類型決定註解的 targetType
                        String targetType = "Interface".equalsIgnoreCase(classType) ? "Interface" : "Class";
                        Neo4jAnnotationNode annotationNode = astToGraphUtil.createAnnotationEntityFromData(annotation,
                                targetType,
                                sourceFile, packageName);
                        annotationNodes.add(annotationNode);
                        log.debug("創建{}註解: {}", targetType, annotationName);
                    } else {
                        log.debug("排除{}註解: {}", "Interface".equalsIgnoreCase(classType) ? "介面" : "類別", annotationName);
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

                            if (!astToGraphUtil.shouldExcludeAnnotation(annotationName)) {
                                Neo4jAnnotationNode annotationNode = astToGraphUtil.createAnnotationEntityFromData(
                                        annotation,
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

    /**
     * 提取方法呼叫關係
     */
    private Map<String, List<String>> extractMethodCallRelations(JsonNode rootNode, List<Neo4jMethodNode> methodNodes) {
        Map<String, List<String>> relations = new HashMap<>();

        JsonNode sequenceData = rootNode.path("sequenceDiagramData");
        if (sequenceData.isObject()) {
            JsonNode methodGroups = sequenceData.path("methodGroups");
            if (methodGroups.isArray()) {
                for (JsonNode methodGroup : methodGroups) {
                    String methodName = methodGroup.path("methodName").asText();
                    String className = sequenceData.path("classFqn").asText();

                    Neo4jMethodNode callerMethod = methodNodes.stream()
                            .filter(m -> m.getName().equals(methodName) && m.getClassName().equals(className))
                            .findFirst()
                            .orElse(null);

                    if (callerMethod != null) {
                        List<String> calledMethodIds = new ArrayList<>();

                        JsonNode interactions = methodGroup.path("interactions");
                        if (interactions.isArray()) {
                            for (JsonNode interaction : interactions) {
                                String calledMethodName = interaction.path("methodName").asText();
                                String calledClassName = interaction.path("className").asText();

                                Neo4jMethodNode calledMethod = methodNodes.stream()
                                        .filter(m -> m.getName().equals(calledMethodName)
                                                && m.getClassName().equals(calledClassName))
                                        .findFirst()
                                        .orElse(null);

                                if (calledMethod != null && !callerMethod.getId().equals(calledMethod.getId())) {
                                    calledMethodIds.add(calledMethod.getId());
                                }
                            }
                        }

                        if (!calledMethodIds.isEmpty()) {
                            relations.put(callerMethod.getId(), calledMethodIds);
                        }
                    }
                }
            }
        }

        return relations;
    }

    /**
     * 提取方法與註解的關係
     */
    private Map<String, List<String>> extractMethodAnnotationRelations(JsonNode rootNode,
            List<Neo4jMethodNode> methodNodes, List<Neo4jAnnotationNode> annotationNodes) {
        Map<String, List<String>> relations = new HashMap<>();

        JsonNode sequenceData = rootNode.path("sequenceDiagramData");
        if (sequenceData.isObject()) {
            JsonNode methodGroups = sequenceData.path("methodGroups");
            if (methodGroups.isArray()) {
                for (JsonNode methodGroup : methodGroups) {
                    String methodName = methodGroup.path("methodName").asText();
                    String className = sequenceData.path("classFqn").asText();

                    Neo4jMethodNode methodNode = methodNodes.stream()
                            .filter(m -> m.getName().equals(methodName) && m.getClassName().equals(className))
                            .findFirst()
                            .orElse(null);

                    if (methodNode != null) {
                        List<String> annotationIds = new ArrayList<>();

                        JsonNode methodAnnotations = methodGroup.path("methodAnnotations");
                        if (methodAnnotations.isArray()) {
                            for (JsonNode annotation : methodAnnotations) {
                                String annotationName = annotation.path("annotationName").asText();
                                if (annotationName.isEmpty()) {
                                    annotationName = annotation.path("simpleName").asText();
                                }

                                final String finalAnnotationName = annotationName;
                                Neo4jAnnotationNode annotationNode = annotationNodes.stream()
                                        .filter(a -> a.getName().equals(finalAnnotationName)
                                                && "Method".equals(a.getTargetType()))
                                        .findFirst()
                                        .orElse(null);

                                if (annotationNode != null) {
                                    annotationIds.add(annotationNode.getId());
                                }
                            }
                        }

                        if (!annotationIds.isEmpty()) {
                            relations.put(methodNode.getId(), annotationIds);
                        }
                    }
                }
            }
        }

        return relations;
    }

    // ==================== 輔助方法 ====================

}
