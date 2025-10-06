package kai.javaparser.jsp.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import kai.javaparser.jsp.entity.JSPBackendMethodNode;

/**
 * JSP 後端方法節點 Repository
 */
@Repository
public interface JSPBackendMethodRepository extends Neo4jRepository<JSPBackendMethodNode, String> {

    /**
     * 根據檔案名稱查找所有後端方法節點
     */
    List<JSPBackendMethodNode> findByFileName(String fileName);

    /**
     * 根據類別名稱查找節點
     */
    List<JSPBackendMethodNode> findByClassName(String className);

    /**
     * 根據方法名稱查找節點
     */
    List<JSPBackendMethodNode> findByMethodName(String methodName);

    /**
     * 根據類別名稱和方法名稱查找節點
     */
    List<JSPBackendMethodNode> findByClassNameAndMethodName(String className, String methodName);

    /**
     * 查找特定 ID 的節點
     */
    Optional<JSPBackendMethodNode> findById(String id);

    /**
     * 查找與特定節點有互動關係的所有後端方法
     */
    @Query("MATCH (n:JSPBackendMethod {id: $nodeId})-[:INTERACTS_WITH]->(target) RETURN target")
    List<JSPBackendMethodNode> findInteractingMethods(@Param("nodeId") String nodeId);

    /**
     * 刪除特定檔案的所有後端方法節點和關係
     */
    @Query("MATCH (n:JSPBackendMethod {fileName: $fileName}) DETACH DELETE n")
    void deleteByFileName(@Param("fileName") String fileName);

    /**
     * 統計後端方法節點數
     */
    @Query("MATCH (n:JSPBackendMethod) RETURN count(n) as count")
    Long countJSPBackendMethods();

    /**
     * 刪除所有後端方法節點和關係
     */
    @Query("MATCH (n:JSPBackendMethod) DETACH DELETE n")
    void deleteAllJSPBackendMethods();
}
