package kai.javaparser.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import kai.javaparser.ast.entity.Neo4jInterfaceNode;

/**
 * Neo4j 介面節點 Repository
 * 提供介面節點的 CRUD 操作和查詢功能
 */
@Repository
public interface Neo4jInterfaceNodeRepository extends Neo4jRepository<Neo4jInterfaceNode, String> {

    /**
     * 根據名稱查找介面
     */
    List<Neo4jInterfaceNode> findByName(String name);

    /**
     * 根據包名查找介面
     */
    List<Neo4jInterfaceNode> findByPackageName(String packageName);

    /**
     * 根據名稱和包名查找介面
     */
    Optional<Neo4jInterfaceNode> findByNameAndPackageName(String name, String packageName);

    /**
     * 根據源文件查找介面
     */
    List<Neo4jInterfaceNode> findBySourceFile(String sourceFile);

    /**
     * 查找所有公共介面
     */
    @Query("MATCH (i:Interface {isPublic: true}) RETURN i")
    List<Neo4jInterfaceNode> findAllPublicInterfaces();

    /**
     * 查找特定介面繼承的所有介面
     */
    @Query("MATCH (i:Interface {id: $interfaceId})-[:EXTENDS]->(parent:Interface) RETURN parent")
    List<Neo4jInterfaceNode> findExtendedInterfaces(@Param("interfaceId") String interfaceId);

    /**
     * 查找繼承特定介面的所有介面
     */
    @Query("MATCH (child:Interface)-[:EXTENDS]->(i:Interface {id: $interfaceId}) RETURN child")
    List<Neo4jInterfaceNode> findExtendingInterfaces(@Param("interfaceId") String interfaceId);

    /**
     * 查找特定介面包含的所有方法
     */
    @Query("MATCH (i:Interface {id: $interfaceId})-[:CONTAINS]->(method:Method) RETURN method")
    List<Object> findContainedMethods(@Param("interfaceId") String interfaceId);

    /**
     * 查找特定介面的所有註解
     */
    @Query("MATCH (i:Interface {id: $interfaceId})-[:HAS_ANNOTATION]->(annotation:Annotation) RETURN annotation")
    List<Object> findAnnotations(@Param("interfaceId") String interfaceId);

    /**
     * 查找實現特定介面的所有類別
     */
    @Query("MATCH (c:Class)-[:IMPLEMENTS]->(i:Interface {id: $interfaceId}) RETURN c")
    List<Object> findImplementingClasses(@Param("interfaceId") String interfaceId);

    /**
     * 統計介面數量
     */
    @Query("MATCH (i:Interface) RETURN count(i) as count")
    Long countInterfaces();

    /**
     * 統計包中的介面數量
     */
    @Query("MATCH (i:Interface {package: $packageName}) RETURN count(i) as count")
    Long countByPackage(@Param("packageName") String packageName);

    /**
     * 刪除特定源文件的所有介面
     */
    @Query("MATCH (i:Interface {sourceFile: $sourceFile}) DETACH DELETE i")
    void deleteBySourceFile(@Param("sourceFile") String sourceFile);

    /**
     * 刪除所有介面節點和關係
     */
    @Query("MATCH (i:Interface) DETACH DELETE i")
    void deleteAllInterfaces();
}
