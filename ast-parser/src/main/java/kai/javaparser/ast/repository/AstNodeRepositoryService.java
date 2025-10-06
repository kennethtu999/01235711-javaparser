package kai.javaparser.ast.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import kai.javaparser.ast.entity.Neo4jAnnotationNode;
import kai.javaparser.ast.entity.Neo4jClassNode;
import kai.javaparser.ast.entity.Neo4jInterfaceNode;
import kai.javaparser.ast.entity.Neo4jMethodNode;
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

    public List<Neo4jMethodNode> findMethodsByClassId(String classId) {
        return methodRepository.findByClassId(classId);
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
}
