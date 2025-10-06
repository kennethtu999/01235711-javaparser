package kai.javaparser.ast.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import kai.javaparser.ast.entity.Neo4jClassNode;

/**
 * Neo4j 類別節點 Repository
 * 提供類別節點的 CRUD 操作和查詢功能
 */
@Repository
public interface Neo4jClassNodeRepository extends Neo4jRepository<Neo4jClassNode, String> {

    /**
     * 根據名稱查找類別
     */
    List<Neo4jClassNode> findByName(String name);

    /**
     * 根據包名查找類別
     */
    List<Neo4jClassNode> findByPackageName(String packageName);

    /**
     * 根據名稱和包名查找類別
     */
    Optional<Neo4jClassNode> findByNameAndPackageName(String name, String packageName);

    /**
     * 根據源文件查找類別
     */
    List<Neo4jClassNode> findBySourceFile(String sourceFile);

    /**
     * 查找所有抽象類別
     */
    @Query("MATCH (c:Class {isAbstract: true}) RETURN c")
    List<Neo4jClassNode> findAllAbstractClasses();

    /**
     * 查找所有最終類別
     */
    @Query("MATCH (c:Class {isFinal: true}) RETURN c")
    List<Neo4jClassNode> findAllFinalClasses();

    /**
     * 查找所有公共類別
     */
    @Query("MATCH (c:Class {isPublic: true}) RETURN c")
    List<Neo4jClassNode> findAllPublicClasses();

    /**
     * 查找特定類別的所有子類別
     */
    @Query("MATCH (c:Class {id: $classId})<-[:EXTENDS]-(subclass:Class) RETURN subclass")
    List<Neo4jClassNode> findSubClasses(@Param("classId") String classId);

    /**
     * 查找特定類別的所有父類別
     */
    @Query("MATCH (c:Class {id: $classId})-[:EXTENDS]->(superclass:Class) RETURN superclass")
    List<Neo4jClassNode> findSuperClasses(@Param("classId") String classId);

    /**
     * 查找特定類別實現的所有介面
     */
    @Query("MATCH (c:Class {id: $classId})-[:IMPLEMENTS]->(interface:Interface) RETURN interface")
    List<Object> findImplementedInterfaces(@Param("classId") String classId);

    /**
     * 查找特定類別包含的所有方法
     */
    @Query("MATCH (c:Class {id: $classId})-[:CONTAINS]->(method:Method) RETURN method")
    List<Object> findContainedMethods(@Param("classId") String classId);

    /**
     * 查找特定類別的所有註解
     */
    @Query("MATCH (c:Class {id: $classId})-[:HAS_ANNOTATION]->(annotation:Annotation) RETURN annotation")
    List<Object> findAnnotations(@Param("classId") String classId);

    /**
     * 統計類別數量
     */
    @Query("MATCH (c:Class) RETURN count(c) as count")
    Long countClasses();

    /**
     * 統計包中的類別數量
     */
    @Query("MATCH (c:Class {package: $packageName}) RETURN count(c) as count")
    Long countByPackage(@Param("packageName") String packageName);

    /**
     * 刪除特定源文件的所有類別
     */
    @Query("MATCH (c:Class {sourceFile: $sourceFile}) DETACH DELETE c")
    void deleteBySourceFile(@Param("sourceFile") String sourceFile);

    /**
     * 刪除所有類別節點和關係
     */
    @Query("MATCH (c:Class) DETACH DELETE c")
    void deleteAllClasses();
}
