package kai.javaparser.ast.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import kai.javaparser.ast.entity.Neo4jMethodNode;

/**
 * Neo4j 方法節點 Repository
 * 提供方法節點的 CRUD 操作和查詢功能
 */
@Repository
public interface Neo4jMethodNodeRepository extends Neo4jRepository<Neo4jMethodNode, String> {

    /**
     * 根據名稱查找方法
     */
    List<Neo4jMethodNode> findByName(String name);

    /**
     * 根據類別名稱查找方法
     */
    List<Neo4jMethodNode> findByClassName(String className);

    /**
     * 根據名稱和類別名稱查找方法
     */
    Optional<Neo4jMethodNode> findByNameAndClassName(String name, String className);

    /**
     * 根據源文件查找方法
     */
    List<Neo4jMethodNode> findBySourceFile(String sourceFile);

    /**
     * 查找所有構造函數
     */
    @Query("MATCH (m:Method {isConstructor: true}) RETURN m")
    List<Neo4jMethodNode> findAllConstructors();

    /**
     * 查找所有 Getter 方法
     */
    @Query("MATCH (m:Method {isGetter: true}) RETURN m")
    List<Neo4jMethodNode> findAllGetters();

    /**
     * 查找所有 Setter 方法
     */
    @Query("MATCH (m:Method {isSetter: true}) RETURN m")
    List<Neo4jMethodNode> findAllSetters();

    /**
     * 查找所有抽象方法
     */
    @Query("MATCH (m:Method {isAbstract: true}) RETURN m")
    List<Neo4jMethodNode> findAllAbstractMethods();

    /**
     * 查找所有靜態方法
     */
    @Query("MATCH (m:Method {isStatic: true}) RETURN m")
    List<Neo4jMethodNode> findAllStaticMethods();

    /**
     * 查找特定方法調用的所有方法
     */
    @Query("MATCH (m:Method {id: $methodId})-[:CALLS]->(called:Method) RETURN called")
    List<Neo4jMethodNode> findCalledMethods(@Param("methodId") String methodId);

    /**
     * 查找調用特定方法的所有方法
     */
    @Query("MATCH (caller:Method)-[:CALLS]->(m:Method {id: $methodId}) RETURN caller")
    List<Neo4jMethodNode> findCallingMethods(@Param("methodId") String methodId);

    /**
     * 查找特定方法的所有註解
     */
    @Query("MATCH (m:Method {id: $methodId})-[:HAS_ANNOTATION]->(annotation:Annotation) RETURN annotation")
    List<Object> findAnnotations(@Param("methodId") String methodId);

    /**
     * 查找特定類別的所有方法
     */
    @Query("MATCH (c:Class {id: $classId})-[:CONTAINS]->(method:Method) RETURN method")
    List<Neo4jMethodNode> findByClassId(@Param("classId") String classId);

    /**
     * 統計方法數量
     */
    @Query("MATCH (m:Method) RETURN count(m) as count")
    Long countMethods();

    /**
     * 統計特定類別的方法數量
     */
    @Query("MATCH (c:Class {id: $classId})-[:CONTAINS]->(method:Method) RETURN count(method) as count")
    Long countByClassId(@Param("classId") String classId);

    /**
     * 刪除特定源文件的所有方法
     */
    @Query("MATCH (m:Method {sourceFile: $sourceFile}) DETACH DELETE m")
    void deleteBySourceFile(@Param("sourceFile") String sourceFile);

    /**
     * 刪除所有方法節點和關係
     */
    @Query("MATCH (m:Method) DETACH DELETE m")
    void deleteAllMethods();
}
