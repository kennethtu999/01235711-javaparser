package kai.javaparser.ast.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import kai.javaparser.ast.entity.Neo4jAnnotationNode;
import kai.javaparser.ast.entity.Neo4jClassNode;
import kai.javaparser.ast.entity.Neo4jInterfaceNode;
import kai.javaparser.ast.entity.Neo4jMethodNode;
import kai.javaparser.ast.service.BulkNeo4jService;
import kai.javaparser.repository.Neo4jInterfaceNodeRepository;

/**
 * AST 節點 Repository 服務
 * 提供統一的 AST 節點操作介面
 */
@Service
public class AstNodeRepositoryService {

    @Autowired
    private Neo4jClassNodeRepository classRepository;

    @Autowired
    private Neo4jMethodNodeRepository methodRepository;

    @Autowired
    private Neo4jInterfaceNodeRepository interfaceRepository;

    @Autowired
    private Neo4jAnnotationNodeRepository annotationRepository;

    @Autowired
    private BulkNeo4jService bulkNeo4jService;

    // ==================== 類別操作 ====================

    public Neo4jClassNode saveClass(Neo4jClassNode classNode) {
        return classRepository.save(classNode);
    }

    public Optional<Neo4jClassNode> findClassById(String id) {
        return classRepository.findById(id);
    }

    public Optional<Neo4jClassNode> findClassByNameAndPackage(String name, String packageName) {
        return classRepository.findByNameAndPackageName(name, packageName);
    }

    public List<Neo4jClassNode> findClassesBySourceFile(String sourceFile) {
        return classRepository.findBySourceFile(sourceFile);
    }

    public void deleteClassesBySourceFile(String sourceFile) {
        classRepository.deleteBySourceFile(sourceFile);
    }

    public void deleteAllClasses() {
        classRepository.deleteAllClasses();
    }

    // ==================== 方法操作 ====================

    public Neo4jMethodNode saveMethod(Neo4jMethodNode methodNode) {
        return methodRepository.save(methodNode);
    }

    public Optional<Neo4jMethodNode> findMethodById(String id) {
        return methodRepository.findById(id);
    }

    public Optional<Neo4jMethodNode> findMethodByNameAndClass(String name, String className) {
        return methodRepository.findByNameAndClassName(name, className);
    }

    public List<Neo4jMethodNode> findMethodsBySourceFile(String sourceFile) {
        return methodRepository.findBySourceFile(sourceFile);
    }

    public void deleteMethodsBySourceFile(String sourceFile) {
        methodRepository.deleteBySourceFile(sourceFile);
    }

    public void deleteAllMethods() {
        methodRepository.deleteAllMethods();
    }

    // ==================== 介面操作 ====================

    public Neo4jInterfaceNode saveInterface(Neo4jInterfaceNode interfaceNode) {
        return interfaceRepository.save(interfaceNode);
    }

    public Optional<Neo4jInterfaceNode> findInterfaceById(String id) {
        return interfaceRepository.findById(id);
    }

    public Optional<Neo4jInterfaceNode> findInterfaceByNameAndPackage(String name, String packageName) {
        return interfaceRepository.findByNameAndPackageName(name, packageName);
    }

    public List<Neo4jInterfaceNode> findInterfacesBySourceFile(String sourceFile) {
        return interfaceRepository.findBySourceFile(sourceFile);
    }

    public void deleteInterfacesBySourceFile(String sourceFile) {
        interfaceRepository.deleteBySourceFile(sourceFile);
    }

    public void deleteAllInterfaces() {
        interfaceRepository.deleteAllInterfaces();
    }

    // ==================== 註解操作 ====================

    public Neo4jAnnotationNode saveAnnotation(Neo4jAnnotationNode annotationNode) {
        return annotationRepository.save(annotationNode);
    }

    public Optional<Neo4jAnnotationNode> findAnnotationById(String id) {
        return annotationRepository.findById(id);
    }

    public Optional<Neo4jAnnotationNode> findAnnotationByNameAndTargetType(String name, String targetType) {
        return annotationRepository.findByNameAndTargetType(name, targetType);
    }

    public List<Neo4jAnnotationNode> findAnnotationsBySourceFile(String sourceFile) {
        return annotationRepository.findBySourceFile(sourceFile);
    }

    public List<Neo4jAnnotationNode> findAnnotationsByClassId(String classId) {
        return annotationRepository.findByClassId(classId);
    }

    public List<Neo4jAnnotationNode> findAnnotationsByMethodId(String methodId) {
        return annotationRepository.findByMethodId(methodId);
    }

    public void deleteAnnotationsBySourceFile(String sourceFile) {
        annotationRepository.deleteBySourceFile(sourceFile);
    }

    public void deleteAllAnnotations() {
        annotationRepository.deleteAllAnnotations();
    }

    // ==================== 統計操作 ====================

    public Map<String, Long> getStatistics() {
        return Map.of(
                "classes", classRepository.countClasses(),
                "methods", methodRepository.countMethods(),
                "interfaces", interfaceRepository.countInterfaces(),
                "annotations", annotationRepository.countAnnotations());
    }

    // ==================== 清理操作 ====================

    public void clearAllAstData() {
        deleteAllAnnotations();
        deleteAllMethods();
        deleteAllInterfaces();
        deleteAllClasses();
    }

    public void clearAstDataBySourceFile(String sourceFile) {
        deleteAnnotationsBySourceFile(sourceFile);
        deleteMethodsBySourceFile(sourceFile);
        deleteInterfacesBySourceFile(sourceFile);
        deleteClassesBySourceFile(sourceFile);
    }

    // ==================== 批量操作 ====================

    /**
     * 批量保存類別節點，防止重複插入
     * 
     * @param classNodes 類別節點列表
     * @return 成功插入的節點數量
     */
    public int bulkSaveClasses(List<Neo4jClassNode> classNodes) {
        return bulkNeo4jService.bulkInsertClasses(classNodes);
    }

    /**
     * 批量保存方法節點，防止重複插入
     * 
     * @param methodNodes 方法節點列表
     * @return 成功插入的節點數量
     */
    public int bulkSaveMethods(List<Neo4jMethodNode> methodNodes) {
        return bulkNeo4jService.bulkInsertMethods(methodNodes);
    }

    /**
     * 批量建立類別與方法的關係
     * 
     * @param classMethodRelations 類別ID到方法ID列表的映射
     * @return 成功建立的關係數量
     */
    public int bulkCreateClassMethodRelations(Map<String, List<String>> classMethodRelations) {
        return bulkNeo4jService.bulkCreateClassMethodRelations(classMethodRelations);
    }

    /**
     * 批量建立介面與方法的關係
     * 
     * @param interfaceMethodRelations 介面ID到方法ID列表的映射
     * @return 成功建立的關係數量
     */
    public int bulkCreateInterfaceMethodRelations(Map<String, List<String>> interfaceMethodRelations) {
        return bulkNeo4jService.bulkCreateInterfaceMethodRelations(interfaceMethodRelations);
    }

    /**
     * 批量建立方法與方法的呼叫關係
     * 
     * @param methodCallRelations 呼叫者方法ID到被呼叫方法ID列表的映射
     * @return 成功建立的關係數量
     */
    public int bulkCreateMethodCallRelations(Map<String, List<String>> methodCallRelations) {
        return bulkNeo4jService.bulkCreateMethodCallRelations(methodCallRelations);
    }

    /**
     * 批量建立類別與註解的關係
     * 
     * @param classAnnotationRelations 類別ID到註解ID列表的映射
     * @return 成功建立的關係數量
     */
    public int bulkCreateClassAnnotationRelations(Map<String, List<String>> classAnnotationRelations) {
        return bulkNeo4jService.bulkCreateClassAnnotationRelations(classAnnotationRelations);
    }

    /**
     * 批量建立介面與註解的關係
     * 
     * @param interfaceAnnotationRelations 介面ID到註解ID列表的映射
     * @return 成功建立的關係數量
     */
    public int bulkCreateInterfaceAnnotationRelations(Map<String, List<String>> interfaceAnnotationRelations) {
        return bulkNeo4jService.bulkCreateInterfaceAnnotationRelations(interfaceAnnotationRelations);
    }

    /**
     * 批量建立方法與註解的關係
     * 
     * @param methodAnnotationRelations 方法ID到註解ID列表的映射
     * @return 成功建立的關係數量
     */
    public int bulkCreateMethodAnnotationRelations(Map<String, List<String>> methodAnnotationRelations) {
        return bulkNeo4jService.bulkCreateMethodAnnotationRelations(methodAnnotationRelations);
    }

    /**
     * 批量建立 EXTENDS 關係 (Class/AbstractClass 之間)
     * 
     * @param extendsRelations SubClassId 到 SuperClassId 的映射
     * @return 成功建立的關係數量
     */
    public int bulkCreateExtendsRelations(Map<String, String> extendsRelations) {
        return bulkNeo4jService.bulkCreateExtendsRelations(extendsRelations);
    }

    /**
     * 批量建立 IMPLEMENTS 關係 (Class/AbstractClass -> Interface)
     * 
     * @param implementsRelations ClassId 到 InterfaceId 列表的映射
     * @return 成功建立的關係數量
     */
    public int bulkCreateImplementsRelations(Map<String, List<String>> implementsRelations) {
        return bulkNeo4jService.bulkCreateImplementsRelations(implementsRelations);
    }

    /**
     * 批量保存介面節點，防止重複插入
     * 
     * @param interfaceNodes 介面節點列表
     * @return 成功插入的節點數量
     */
    public int bulkSaveInterfaces(List<Neo4jInterfaceNode> interfaceNodes) {
        return bulkNeo4jService.bulkInsertInterfaces(interfaceNodes);
    }

    /**
     * 批量保存註解節點，防止重複插入
     * 
     * @param annotationNodes 註解節點列表
     * @return 成功插入的節點數量
     */
    public int bulkSaveAnnotations(List<Neo4jAnnotationNode> annotationNodes) {
        return bulkNeo4jService.bulkInsertAnnotations(annotationNodes);
    }

    /**
     * 異步批量處理，適用於大量數據（超過100,000節點）
     * 
     * @param classNodes           類別節點列表
     * @param methodNodes          方法節點列表
     * @param classMethodRelations 類別方法關係
     * @param methodCallRelations  方法呼叫關係
     * @return 處理結果的 CompletableFuture
     */
    public CompletableFuture<BulkNeo4jService.BulkOperationResult> asyncBulkProcess(
            List<Neo4jClassNode> classNodes,
            List<Neo4jMethodNode> methodNodes,
            Map<String, List<String>> classMethodRelations,
            Map<String, List<String>> methodCallRelations) {
        return bulkNeo4jService.asyncBulkProcess(classNodes, methodNodes, classMethodRelations, methodCallRelations);
    }

    /**
     * 根據節點類型查找類別
     * 
     * @param nodeType 節點類型（Class/AbstractClass/Interface）
     * @return 類別列表
     */
    public List<Neo4jClassNode> findClassesByNodeType(String nodeType) {
        return classRepository.findByNodeType(nodeType);
    }

    /**
     * 查找所有抽象類別
     * 
     * @return 抽象類別列表
     */
    public List<Neo4jClassNode> findAllAbstractClasses() {
        return classRepository.findAllAbstractClassesByType();
    }

    /**
     * 查找所有介面
     * 
     * @return 介面列表
     */
    public List<Neo4jClassNode> findAllInterfaces() {
        return classRepository.findAllInterfaces();
    }

    /**
     * 檢查類別是否存在（防止重複插入）
     * 
     * @param ids 類別ID列表
     * @return 已存在的類別ID列表
     */
    public List<String> findExistingClassIds(List<String> ids) {
        return classRepository.findExistingClassIds(ids);
    }

    /**
     * 檢查方法是否存在（防止重複插入）
     * 
     * @param ids 方法ID列表
     * @return 已存在的方法ID列表
     */
    public List<String> findExistingMethodIds(List<String> ids) {
        return methodRepository.findExistingMethodIds(ids);
    }

    /**
     * 查找特定類別的所有方法
     * 
     * @param classId 類別ID
     * @return 方法列表
     */
    public List<Neo4jMethodNode> findMethodsByClassId(String classId) {
        return methodRepository.findMethodsByClassId(classId);
    }

    /**
     * 查找方法呼叫關係
     * 
     * @param callerId 呼叫者方法ID
     * @return 被呼叫的方法列表
     */
    public List<Neo4jMethodNode> findCalledMethodsByCallerId(String callerId) {
        return methodRepository.findCalledMethodsByCallerId(callerId);
    }

    /**
     * 查找被呼叫的方法
     * 
     * @param calleeId 被呼叫方法ID
     * @return 呼叫者方法列表
     */
    public List<Neo4jMethodNode> findCallingMethodsByCalleeId(String calleeId) {
        return methodRepository.findCallingMethodsByCalleeId(calleeId);
    }
}
