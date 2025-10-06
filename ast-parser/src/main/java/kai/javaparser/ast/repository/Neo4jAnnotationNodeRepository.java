package kai.javaparser.ast.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import kai.javaparser.ast.entity.Neo4jAnnotationNode;

/**
 * Neo4j 註解節點 Repository
 * 提供註解節點的 CRUD 操作和查詢功能
 */
@Repository
public interface Neo4jAnnotationNodeRepository extends Neo4jRepository<Neo4jAnnotationNode, String> {

    /**
     * 根據名稱查找註解
     */
    List<Neo4jAnnotationNode> findByName(String name);

    /**
     * 根據目標類型查找註解
     */
    List<Neo4jAnnotationNode> findByTargetType(String targetType);

    /**
     * 根據名稱和目標類型查找註解
     */
    Optional<Neo4jAnnotationNode> findByNameAndTargetType(String name, String targetType);

    /**
     * 根據源文件查找註解
     */
    List<Neo4jAnnotationNode> findBySourceFile(String sourceFile);

    /**
     * 查找所有類別註解
     */
    @Query("MATCH (a:Annotation {targetType: 'Class'}) RETURN a")
    List<Neo4jAnnotationNode> findAllClassAnnotations();

    /**
     * 查找所有方法註解
     */
    @Query("MATCH (a:Annotation {targetType: 'Method'}) RETURN a")
    List<Neo4jAnnotationNode> findAllMethodAnnotations();

    /**
     * 查找所有介面註解
     */
    @Query("MATCH (a:Annotation {targetType: 'Interface'}) RETURN a")
    List<Neo4jAnnotationNode> findAllInterfaceAnnotations();

    /**
     * 查找特定註解標註的所有類別
     */
    @Query("MATCH (c:Class)-[:HAS_ANNOTATION]->(a:Annotation {id: $annotationId}) RETURN c")
    List<Object> findAnnotatedClasses(@Param("annotationId") String annotationId);

    /**
     * 查找特定註解標註的所有方法
     */
    @Query("MATCH (m:Method)-[:HAS_ANNOTATION]->(a:Annotation {id: $annotationId}) RETURN m")
    List<Object> findAnnotatedMethods(@Param("annotationId") String annotationId);

    /**
     * 查找特定註解標註的所有介面
     */
    @Query("MATCH (i:Interface)-[:HAS_ANNOTATION]->(a:Annotation {id: $annotationId}) RETURN i")
    List<Object> findAnnotatedInterfaces(@Param("annotationId") String annotationId);

    /**
     * 查找特定類別的所有註解
     */
    @Query("MATCH (c:Class {id: $classId})-[:HAS_ANNOTATION]->(annotation:Annotation) RETURN annotation")
    List<Neo4jAnnotationNode> findByClassId(@Param("classId") String classId);

    /**
     * 查找特定方法的所有註解
     */
    @Query("MATCH (m:Method {id: $methodId})-[:HAS_ANNOTATION]->(annotation:Annotation) RETURN annotation")
    List<Neo4jAnnotationNode> findByMethodId(@Param("methodId") String methodId);

    /**
     * 查找特定介面的所有註解
     */
    @Query("MATCH (i:Interface {id: $interfaceId})-[:HAS_ANNOTATION]->(annotation:Annotation) RETURN annotation")
    List<Neo4jAnnotationNode> findByInterfaceId(@Param("interfaceId") String interfaceId);

    /**
     * 統計註解數量
     */
    @Query("MATCH (a:Annotation) RETURN count(a) as count")
    Long countAnnotations();

    /**
     * 統計特定目標類型的註解數量
     */
    @Query("MATCH (a:Annotation {targetType: $targetType}) RETURN count(a) as count")
    Long countByTargetType(@Param("targetType") String targetType);

    /**
     * 統計特定名稱的註解使用次數
     */
    @Query("MATCH (a:Annotation {name: $annotationName})<-[:HAS_ANNOTATION]-() RETURN count(a) as count")
    Long countUsagesByName(@Param("annotationName") String annotationName);

    /**
     * 刪除特定源文件的所有註解
     */
    @Query("MATCH (a:Annotation {sourceFile: $sourceFile}) DETACH DELETE a")
    void deleteBySourceFile(@Param("sourceFile") String sourceFile);

    /**
     * 刪除所有註解節點和關係
     */
    @Query("MATCH (a:Annotation) DETACH DELETE a")
    void deleteAllAnnotations();
}
