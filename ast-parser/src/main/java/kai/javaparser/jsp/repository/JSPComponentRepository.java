package kai.javaparser.jsp.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import kai.javaparser.jsp.entity.JSPComponentNode;

/**
 * JSP 元件節點 Repository
 */
@Repository
public interface JSPComponentRepository extends Neo4jRepository<JSPComponentNode, String> {

    /**
     * 根據檔案名稱查找所有 JSP 元件節點
     */
    List<JSPComponentNode> findByFileName(String fileName);

    /**
     * 根據元件類型查找節點
     */
    List<JSPComponentNode> findByComponentType(String componentType);

    /**
     * 根據標籤名稱查找節點
     */
    List<JSPComponentNode> findByTagName(String tagName);

    /**
     * 根據檔案名稱和元件類型查找節點
     */
    List<JSPComponentNode> findByFileNameAndComponentType(String fileName, String componentType);

    /**
     * 查找特定 ID 的節點
     */
    Optional<JSPComponentNode> findById(String id);

    /**
     * 查找觸發特定後端方法的元件
     */
    @Query("MATCH (component:JSPComponent)-[:TRIGGERS]->(method:JspNode {id: $methodId}) RETURN component")
    List<JSPComponentNode> findComponentsTriggeringMethod(@Param("methodId") String methodId);

    /**
     * 查找與特定節點有互動關係的所有 JSP 元件
     */
    @Query("MATCH (n:JSPComponent {id: $nodeId})-[:INTERACTS_WITH]->(target) RETURN target")
    List<JSPComponentNode> findInteractingComponents(@Param("nodeId") String nodeId);

    /**
     * 刪除特定檔案的所有 JSP 元件節點和關係
     */
    @Query("MATCH (n:JSPComponent {fileName: $fileName}) DETACH DELETE n")
    void deleteByFileName(@Param("fileName") String fileName);

    /**
     * 統計 JSP 元件節點數
     */
    @Query("MATCH (n:JSPComponent) RETURN count(n) as count")
    Long countJSPComponents();

    /**
     * 刪除所有 JSP 元件節點和關係
     */
    @Query("MATCH (n:JSPComponent) DETACH DELETE n")
    void deleteAllJSPComponents();
}
