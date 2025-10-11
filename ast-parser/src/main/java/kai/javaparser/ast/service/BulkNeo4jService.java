package kai.javaparser.ast.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kai.javaparser.ast.entity.Neo4jAnnotationNode;
import kai.javaparser.ast.entity.Neo4jClassNode;
import kai.javaparser.ast.entity.Neo4jInterfaceNode;
import kai.javaparser.ast.entity.Neo4jMethodNode;
import kai.javaparser.service.Neo4jService;
import lombok.extern.slf4j.Slf4j;

/**
 * 大量 Neo4j 操作服務
 * 針對超過 100,000 節點的系統進行優化
 */
@Slf4j
@Service
public class BulkNeo4jService {

    @Autowired
    private Neo4jService neo4jService;

    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    /**
     * 批量插入類別節點，防止重複插入
     * 
     * @param classNodes 類別節點列表
     * @return 成功插入的節點數量
     */
    @Transactional
    public int bulkInsertClasses(List<Neo4jClassNode> classNodes) {
        if (classNodes == null || classNodes.isEmpty()) {
            return 0;
        }

        log.info("開始批量插入 {} 個類別節點", classNodes.size());

        // 使用 MERGE 語句防止重複插入
        String cypher = """
                UNWIND $classNodes AS classNode
                MERGE (c:Class {id: classNode.id})
                SET c.name = classNode.name,
                    c.nodeType = classNode.nodeType,
                    c.package = classNode.package,
                    c.modifiers = classNode.modifiers,
                    c.isAbstract = classNode.isAbstract,
                    c.isFinal = classNode.isFinal,
                    c.isPublic = classNode.isPublic,
                    c.isPrivate = classNode.isPrivate,
                    c.isProtected = classNode.isProtected,
                    c.isStatic = classNode.isStatic,
                    c.sourceFile = classNode.sourceFile,
                    c.lineNumber = classNode.lineNumber,
                    c.columnNumber = classNode.columnNumber,
                    c.createdAt = classNode.createdAt
                """;

        Map<String, Object> parameters = Map.of("classNodes",
                classNodes.stream()
                        .map(this::convertClassNodeToMap)
                        .collect(Collectors.toList()));

        try {
            neo4jService.executeWrite(cypher, parameters);
            log.info("成功批量插入 {} 個類別節點", classNodes.size());
            return classNodes.size();
        } catch (Exception e) {
            log.error("批量插入類別節點失敗", e);
            throw new RuntimeException("批量插入類別節點失敗", e);
        }
    }

    /**
     * 批量插入方法節點，防止重複插入
     * 
     * @param methodNodes 方法節點列表
     * @return 成功插入的節點數量
     */
    @Transactional
    public int bulkInsertMethods(List<Neo4jMethodNode> methodNodes) {
        if (methodNodes == null || methodNodes.isEmpty()) {
            return 0;
        }

        log.info("開始批量插入 {} 個方法節點", methodNodes.size());

        // 使用 MERGE 語句防止重複插入
        String cypher = """
                UNWIND $methodNodes AS methodNode
                MERGE (m:Method {id: methodNode.id})
                SET m.name = methodNode.name,
                    m.className = methodNode.className,
                    m.package = methodNode.package,
                    m.returnType = methodNode.returnType,
                    m.parameters = methodNode.parameters,
                    m.modifiers = methodNode.modifiers,
                    m.isAbstract = methodNode.isAbstract,
                    m.isFinal = methodNode.isFinal,
                    m.isPublic = methodNode.isPublic,
                    m.isPrivate = methodNode.isPrivate,
                    m.isProtected = methodNode.isProtected,
                    m.isStatic = methodNode.isStatic,
                    m.isSynchronized = methodNode.isSynchronized,
                    m.isConstructor = methodNode.isConstructor,
                    m.isGetter = methodNode.isGetter,
                    m.isSetter = methodNode.isSetter,
                    m.sourceFile = methodNode.sourceFile,
                    m.lineNumber = methodNode.lineNumber,
                    m.columnNumber = methodNode.columnNumber,
                    m.bodyLength = methodNode.bodyLength,
                    m.createdAt = methodNode.createdAt
                """;

        Map<String, Object> parameters = Map.of("methodNodes",
                methodNodes.stream()
                        .map(this::convertMethodNodeToMap)
                        .collect(Collectors.toList()));

        try {
            neo4jService.executeWrite(cypher, parameters);
            log.info("成功批量插入 {} 個方法節點", methodNodes.size());
            return methodNodes.size();
        } catch (Exception e) {
            log.error("批量插入方法節點失敗", e);
            throw new RuntimeException("批量插入方法節點失敗", e);
        }
    }

    /**
     * 批量插入介面節點，防止重複插入
     * 
     * @param interfaceNodes 介面節點列表
     * @return 成功插入的節點數量
     */
    @Transactional
    public int bulkInsertInterfaces(List<Neo4jInterfaceNode> interfaceNodes) {
        if (interfaceNodes == null || interfaceNodes.isEmpty()) {
            return 0;
        }

        log.info("開始批量插入 {} 個介面節點", interfaceNodes.size());

        // 使用 MERGE 語句防止重複插入
        String cypher = """
                UNWIND $interfaceNodes AS interfaceNode
                MERGE (i:Interface {id: interfaceNode.id})
                SET i.name = interfaceNode.name,
                    i.package = interfaceNode.package,
                    i.modifiers = interfaceNode.modifiers,
                    i.isPublic = interfaceNode.isPublic,
                    i.isPrivate = interfaceNode.isPrivate,
                    i.isProtected = interfaceNode.isProtected,
                    i.isStatic = interfaceNode.isStatic,
                    i.sourceFile = interfaceNode.sourceFile,
                    i.lineNumber = interfaceNode.lineNumber,
                    i.columnNumber = interfaceNode.columnNumber,
                    i.createdAt = interfaceNode.createdAt
                """;

        Map<String, Object> parameters = Map.of("interfaceNodes",
                interfaceNodes.stream()
                        .map(this::convertInterfaceNodeToMap)
                        .collect(Collectors.toList()));

        try {
            neo4jService.executeWrite(cypher, parameters);
            log.info("成功批量插入 {} 個介面節點", interfaceNodes.size());
            return interfaceNodes.size();
        } catch (Exception e) {
            log.error("批量插入介面節點失敗", e);
            throw new RuntimeException("批量插入介面節點失敗", e);
        }
    }

    /**
     * 批量插入註解節點，防止重複插入
     * 
     * @param annotationNodes 註解節點列表
     * @return 成功插入的節點數量
     */
    @Transactional
    public int bulkInsertAnnotations(List<Neo4jAnnotationNode> annotationNodes) {
        if (annotationNodes == null || annotationNodes.isEmpty()) {
            return 0;
        }

        log.info("開始批量插入 {} 個註解節點", annotationNodes.size());

        // 使用 MERGE 語句防止重複插入
        String cypher = """
                UNWIND $annotationNodes AS annotationNode
                MERGE (a:Annotation {id: annotationNode.id})
                SET a.name = annotationNode.name,
                    a.targetType = annotationNode.targetType,
                    a.parameters = annotationNode.parameters,
                    a.sourceFile = annotationNode.sourceFile,
                    a.lineNumber = annotationNode.lineNumber,
                    a.columnNumber = annotationNode.columnNumber,
                    a.createdAt = annotationNode.createdAt
                """;

        Map<String, Object> parameters = Map.of("annotationNodes",
                annotationNodes.stream()
                        .map(this::convertAnnotationNodeToMap)
                        .collect(Collectors.toList()));

        try {
            neo4jService.executeWrite(cypher, parameters);
            log.info("成功批量插入 {} 個註解節點", annotationNodes.size());
            return annotationNodes.size();
        } catch (Exception e) {
            log.error("批量插入註解節點失敗", e);
            throw new RuntimeException("批量插入註解節點失敗", e);
        }
    }

    /**
     * 批量建立類別與方法的關係
     * 
     * @param classMethodRelations 類別ID到方法ID列表的映射
     * @return 成功建立的關係數量
     */
    @Transactional
    public int bulkCreateClassMethodRelations(Map<String, List<String>> classMethodRelations) {
        if (classMethodRelations == null || classMethodRelations.isEmpty()) {
            return 0;
        }

        log.info("開始批量建立類別與方法的關係，共 {} 個類別", classMethodRelations.size());

        String cypher = """
                UNWIND $relations AS relation
                MATCH (c {id: relation.classId})
                WHERE c:Class OR c:AbstractClass
                MATCH (m:Method {id: relation.methodId})
                MERGE (c)-[:CONTAINS]->(m)
                """;

        List<Map<String, String>> relations = classMethodRelations.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                        .map(methodId -> Map.of("classId", entry.getKey(), "methodId", methodId)))
                .collect(Collectors.toList());

        Map<String, Object> parameters = Map.of("relations", relations);

        try {
            neo4jService.executeWrite(cypher, parameters);
            log.info("成功建立 {} 個類別與方法的關係", relations.size());
            return relations.size();
        } catch (Exception e) {
            log.error("批量建立類別與方法關係失敗", e);
            throw new RuntimeException("批量建立類別與方法關係失敗", e);
        }
    }

    /**
     * 批量建立介面與方法的關係
     * 
     * @param interfaceMethodRelations 介面ID到方法ID列表的映射
     * @return 成功建立的關係數量
     */
    @Transactional
    public int bulkCreateInterfaceMethodRelations(Map<String, List<String>> interfaceMethodRelations) {
        if (interfaceMethodRelations == null || interfaceMethodRelations.isEmpty()) {
            return 0;
        }

        log.info("開始批量建立介面與方法的關係，共 {} 個介面", interfaceMethodRelations.size());

        String cypher = """
                UNWIND $relations AS relation
                MATCH (i:Interface {id: relation.interfaceId})
                MATCH (m:Method {id: relation.methodId})
                MERGE (i)-[:CONTAINS]->(m)
                """;

        List<Map<String, String>> relations = interfaceMethodRelations.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                        .map(methodId -> Map.of("interfaceId", entry.getKey(), "methodId", methodId)))
                .collect(Collectors.toList());

        Map<String, Object> parameters = Map.of("relations", relations);

        try {
            neo4jService.executeWrite(cypher, parameters);
            log.info("成功建立 {} 個介面與方法的關係", relations.size());
            return relations.size();
        } catch (Exception e) {
            log.error("批量建立介面與方法關係失敗", e);
            throw new RuntimeException("批量建立介面與方法關係失敗", e);
        }
    }

    /**
     * 批量建立方法與方法的呼叫關係
     * 
     * @param methodCallRelations 呼叫者方法ID到被呼叫方法ID列表的映射
     * @return 成功建立的關係數量
     */
    @Transactional
    public int bulkCreateMethodCallRelations(Map<String, List<String>> methodCallRelations) {
        if (methodCallRelations == null || methodCallRelations.isEmpty()) {
            return 0;
        }

        log.info("開始批量建立方法呼叫關係，共 {} 個呼叫者", methodCallRelations.size());

        String cypher = """
                UNWIND $relations AS relation
                MATCH (caller:Method {id: relation.callerId})
                MATCH (callee:Method {id: relation.calleeId})
                MERGE (caller)-[:CALLS]->(callee)
                """;

        List<Map<String, String>> relations = methodCallRelations.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                        .map(calleeId -> Map.of("callerId", entry.getKey(), "calleeId", calleeId)))
                .collect(Collectors.toList());

        Map<String, Object> parameters = Map.of("relations", relations);

        try {
            neo4jService.executeWrite(cypher, parameters);
            log.info("成功建立 {} 個方法呼叫關係", relations.size());
            return relations.size();
        } catch (Exception e) {
            log.error("批量建立方法呼叫關係失敗", e);
            throw new RuntimeException("批量建立方法呼叫關係失敗", e);
        }
    }

    /**
     * 批量建立類別與註解的關係
     * 
     * @param classAnnotationRelations 類別ID到註解ID列表的映射
     * @return 成功建立的關係數量
     */
    @Transactional
    public int bulkCreateClassAnnotationRelations(Map<String, List<String>> classAnnotationRelations) {
        if (classAnnotationRelations == null || classAnnotationRelations.isEmpty()) {
            return 0;
        }

        log.info("開始批量建立類別與註解的關係，共 {} 個類別", classAnnotationRelations.size());

        String cypher = """
                UNWIND $relations AS relation
                MATCH (c {id: relation.classId})
                WHERE c:Class OR c:AbstractClass
                MATCH (a:Annotation {id: relation.annotationId})
                MERGE (c)-[:HAS_ANNOTATION]->(a)
                """;

        List<Map<String, String>> relations = classAnnotationRelations.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                        .map(annotationId -> Map.of("classId", entry.getKey(), "annotationId", annotationId)))
                .collect(Collectors.toList());

        Map<String, Object> parameters = Map.of("relations", relations);

        try {
            neo4jService.executeWrite(cypher, parameters);
            log.info("成功建立 {} 個類別與註解的關係", relations.size());
            return relations.size();
        } catch (Exception e) {
            log.error("批量建立類別與註解關係失敗", e);
            throw new RuntimeException("批量建立類別與註解關係失敗", e);
        }
    }

    /**
     * 批量建立介面與註解的關係
     * 
     * @param interfaceAnnotationRelations 介面ID到註解ID列表的映射
     * @return 成功建立的關係數量
     */
    @Transactional
    public int bulkCreateInterfaceAnnotationRelations(Map<String, List<String>> interfaceAnnotationRelations) {
        if (interfaceAnnotationRelations == null || interfaceAnnotationRelations.isEmpty()) {
            return 0;
        }

        log.info("開始批量建立介面與註解的關係，共 {} 個介面", interfaceAnnotationRelations.size());

        String cypher = """
                UNWIND $relations AS relation
                MATCH (i:Interface {id: relation.interfaceId})
                MATCH (a:Annotation {id: relation.annotationId})
                MERGE (i)-[:HAS_ANNOTATION]->(a)
                """;

        List<Map<String, String>> relations = interfaceAnnotationRelations.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                        .map(annotationId -> Map.of("interfaceId", entry.getKey(), "annotationId", annotationId)))
                .collect(Collectors.toList());

        Map<String, Object> parameters = Map.of("relations", relations);

        try {
            neo4jService.executeWrite(cypher, parameters);
            log.info("成功建立 {} 個介面與註解的關係", relations.size());
            return relations.size();
        } catch (Exception e) {
            log.error("批量建立介面與註解關係失敗", e);
            throw new RuntimeException("批量建立介面與註解關係失敗", e);
        }
    }

    /**
     * 批量建立方法與註解的關係
     * 
     * @param methodAnnotationRelations 方法ID到註解ID列表的映射
     * @return 成功建立的關係數量
     */
    @Transactional
    public int bulkCreateMethodAnnotationRelations(Map<String, List<String>> methodAnnotationRelations) {
        if (methodAnnotationRelations == null || methodAnnotationRelations.isEmpty()) {
            return 0;
        }

        log.info("開始批量建立方法與註解的關係，共 {} 個方法", methodAnnotationRelations.size());

        String cypher = """
                UNWIND $relations AS relation
                MATCH (m:Method {id: relation.methodId})
                MATCH (a:Annotation {id: relation.annotationId})
                MERGE (m)-[:HAS_ANNOTATION]->(a)
                """;

        List<Map<String, String>> relations = methodAnnotationRelations.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                        .map(annotationId -> Map.of("methodId", entry.getKey(), "annotationId", annotationId)))
                .collect(Collectors.toList());

        Map<String, Object> parameters = Map.of("relations", relations);

        try {
            neo4jService.executeWrite(cypher, parameters);
            log.info("成功建立 {} 個方法與註解的關係", relations.size());
            return relations.size();
        } catch (Exception e) {
            log.error("批量建立方法與註解關係失敗", e);
            throw new RuntimeException("批量建立方法與註解關係失敗", e);
        }
    }

    /**
     * 異步批量處理，適用於大量數據
     * 
     * @param classNodes           類別節點列表
     * @param methodNodes          方法節點列表
     * @param classMethodRelations 類別方法關係
     * @param methodCallRelations  方法呼叫關係
     * @return 處理結果的 CompletableFuture
     */
    public CompletableFuture<BulkOperationResult> asyncBulkProcess(
            List<Neo4jClassNode> classNodes,
            List<Neo4jMethodNode> methodNodes,
            Map<String, List<String>> classMethodRelations,
            Map<String, List<String>> methodCallRelations) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("開始異步批量處理，類別: {}, 方法: {}",
                        classNodes != null ? classNodes.size() : 0,
                        methodNodes != null ? methodNodes.size() : 0);

                BulkOperationResult result = new BulkOperationResult();

                // 批量插入類別
                if (classNodes != null && !classNodes.isEmpty()) {
                    result.classesInserted = bulkInsertClasses(classNodes);
                }

                // 批量插入方法
                if (methodNodes != null && !methodNodes.isEmpty()) {
                    result.methodsInserted = bulkInsertMethods(methodNodes);
                }

                // 建立類別與方法關係
                if (classMethodRelations != null && !classMethodRelations.isEmpty()) {
                    result.classMethodRelationsCreated = bulkCreateClassMethodRelations(classMethodRelations);
                }

                // 建立方法呼叫關係
                if (methodCallRelations != null && !methodCallRelations.isEmpty()) {
                    result.methodCallRelationsCreated = bulkCreateMethodCallRelations(methodCallRelations);
                }

                log.info("異步批量處理完成: {}", result);
                return result;

            } catch (Exception e) {
                log.error("異步批量處理失敗", e);
                throw new RuntimeException("異步批量處理失敗", e);
            }
        }, executorService);
    }

    /**
     * 將類別節點轉換為 Map
     */
    private Map<String, Object> convertClassNodeToMap(Neo4jClassNode node) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", node.getId());
        map.put("name", node.getName() != null ? node.getName() : "");
        map.put("nodeType", node.getNodeType() != null ? node.getNodeType() : "Class");
        map.put("package", node.getPackageName() != null ? node.getPackageName() : "");
        map.put("sourceFile", node.getSourceFile() != null ? node.getSourceFile() : "");
        map.put("lineNumber", node.getLineNumber() != null ? node.getLineNumber() : 0);
        map.put("columnNumber", node.getColumnNumber() != null ? node.getColumnNumber() : 0);
        map.put("createdAt", node.getCreatedAt() != null ? node.getCreatedAt() : System.currentTimeMillis());
        return map;
    }

    /**
     * 將方法節點轉換為 Map
     */
    private Map<String, Object> convertMethodNodeToMap(Neo4jMethodNode node) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", node.getId());
        map.put("name", node.getName() != null ? node.getName() : "");
        map.put("className", node.getClassName() != null ? node.getClassName() : "");
        map.put("package", node.getPackageName() != null ? node.getPackageName() : "");
        map.put("returnType", node.getReturnType() != null ? node.getReturnType() : "");
        map.put("parameters", node.getParameters() != null ? node.getParameters() : List.of());
        map.put("sourceFile", node.getSourceFile() != null ? node.getSourceFile() : "");
        map.put("lineNumber", node.getLineNumber() != null ? node.getLineNumber() : 0);
        map.put("columnNumber", node.getColumnNumber() != null ? node.getColumnNumber() : 0);
        map.put("bodyLength", node.getBodyLength() != null ? node.getBodyLength() : 0);
        map.put("createdAt", node.getCreatedAt() != null ? node.getCreatedAt() : System.currentTimeMillis());
        return map;
    }

    /**
     * 將介面節點轉換為 Map
     */
    private Map<String, Object> convertInterfaceNodeToMap(Neo4jInterfaceNode node) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", node.getId());
        map.put("name", node.getName() != null ? node.getName() : "");
        map.put("package", node.getPackageName() != null ? node.getPackageName() : "");
        map.put("sourceFile", node.getSourceFile() != null ? node.getSourceFile() : "");
        map.put("lineNumber", node.getLineNumber() != null ? node.getLineNumber() : 0);
        map.put("columnNumber", node.getColumnNumber() != null ? node.getColumnNumber() : 0);
        map.put("createdAt", node.getCreatedAt() != null ? node.getCreatedAt().getTime() : System.currentTimeMillis());
        return map;
    }

    /**
     * 將註解節點轉換為 Map
     */
    private Map<String, Object> convertAnnotationNodeToMap(Neo4jAnnotationNode node) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", node.getId());
        map.put("name", node.getName() != null ? node.getName() : "");
        map.put("targetType", node.getTargetType() != null ? node.getTargetType() : "");
        map.put("parameters", node.getParameters() != null ? node.getParameters() : List.of());
        map.put("sourceFile", node.getSourceFile() != null ? node.getSourceFile() : "");
        map.put("lineNumber", node.getLineNumber() != null ? node.getLineNumber() : 0);
        map.put("columnNumber", node.getColumnNumber() != null ? node.getColumnNumber() : 0);
        map.put("createdAt", node.getCreatedAt() != null ? node.getCreatedAt().getTime() : System.currentTimeMillis());
        return map;
    }

    /**
     * 批量操作結果
     */
    public static class BulkOperationResult {
        public int classesInserted = 0;
        public int methodsInserted = 0;
        public int classMethodRelationsCreated = 0;
        public int methodCallRelationsCreated = 0;

        @Override
        public String toString() {
            return String.format("BulkOperationResult{classes=%d, methods=%d, classMethodRels=%d, methodCallRels=%d}",
                    classesInserted, methodsInserted, classMethodRelationsCreated, methodCallRelationsCreated);
        }
    }
}
