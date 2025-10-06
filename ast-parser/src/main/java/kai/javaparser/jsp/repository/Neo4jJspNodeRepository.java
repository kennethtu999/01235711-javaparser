package kai.javaparser.jsp.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import kai.javaparser.jsp.entity.Neo4jJspNode;
import kai.javaparser.jsp.repository.vo.NodeTypeCount;

/**
 * Neo4j JSP 節點 Repository
 * 提供圖譜資料的 CRUD 操作和查詢功能
 */
@Repository
public interface Neo4jJspNodeRepository extends Neo4jRepository<Neo4jJspNode, String> {

    /**
     * 根據檔案名稱查找所有節點
     */
    List<Neo4jJspNode> findByFileName(String fileName);

    /**
     * 根據節點類型查找節點
     */
    List<Neo4jJspNode> findByType(String type);

    /**
     * 根據檔案名稱和節點類型查找節點
     */
    List<Neo4jJspNode> findByFileNameAndType(String fileName, String type);

    /**
     * 查找特定 ID 的節點
     */
    Optional<Neo4jJspNode> findById(String id);

    /**
     * 查找所有 JSF 元件節點
     */
    @Query("MATCH (n:JspNode {type: 'JSFComponent'}) RETURN n")
    List<Neo4jJspNode> findAllJsfComponents();

    /**
     * 查找所有 JavaScript 函式節點
     */
    @Query("MATCH (n:JspNode {type: 'JSFunction'}) RETURN n")
    List<Neo4jJspNode> findAllJavaScriptFunctions();

    /**
     * 查找所有後端方法節點
     */
    @Query("MATCH (n:JspNode {type: 'BackendMethod'}) RETURN n")
    List<Neo4jJspNode> findAllBackendMethods();

    /**
     * 查找特定檔案的頁面節點
     */
    @Query("MATCH (n:JspNode {type: 'Page', fileName: $fileName}) RETURN n")
    Optional<Neo4jJspNode> findPageByFileName(@Param("fileName") String fileName);

    /**
     * 查找觸發特定後端方法的元件
     */
    @Query("MATCH (component:JspNode {type: 'JSFComponent'})-[:TRIGGERS]->(method:JspNode {id: $methodId}) RETURN component")
    List<Neo4jJspNode> findComponentsTriggeringMethod(@Param("methodId") String methodId);

    /**
     * 查找與特定節點有互動關係的所有節點
     */
    @Query("MATCH (n:JspNode {id: $nodeId})-[:INTERACTS_WITH]->(target) RETURN target")
    List<Neo4jJspNode> findInteractingNodes(@Param("nodeId") String nodeId);

    /**
     * 查找特定節點的所有關係
     */
    @Query("MATCH (n:JspNode {id: $nodeId})-[r]->(target) RETURN n, r, target")
    List<Object[]> findNodeRelationships(@Param("nodeId") String nodeId);

    /**
     * 查找包含特定函式的頁面
     */
    @Query("MATCH (page:JspNode {type: 'Page'})-[:CONTAINS]->(func:JspNode {id: $functionId}) RETURN page")
    Optional<Neo4jJspNode> findPageContainingFunction(@Param("functionId") String functionId);

    /**
     * 查找特定按鈕的工作流程
     */
    @Query("""
            MATCH (button:JspNode {id: $buttonId})
            OPTIONAL MATCH (button)-[:TRIGGERS]->(method:JspNode)
            OPTIONAL MATCH (button)-[:INTERACTS_WITH]->(element:JspNode)
            OPTIONAL MATCH (func:JspNode {type: 'JSFunction'})-[:INTERACTS_WITH]->(button)
            RETURN button, method, element, func
            """)
    List<Object[]> findButtonWorkflow(@Param("buttonId") String buttonId);

    /**
     * 查找所有包含 AJAX 呼叫的函式
     */
    @Query("MATCH (n:JspNode {type: 'JSFunction', containsAjaxCall: true}) RETURN n")
    List<Neo4jJspNode> findFunctionsWithAjaxCalls();

    /**
     * 查找所有包含導航的函式
     */
    @Query("MATCH (n:JspNode {type: 'JSFunction', containsNavigation: true}) RETURN n")
    List<Neo4jJspNode> findFunctionsWithNavigation();

    /**
     * 根據複雜度評分查找函式
     */
    @Query("MATCH (n:JspNode {type: 'JSFunction'}) WHERE n.complexityScore >= $minScore RETURN n ORDER BY n.complexityScore DESC")
    List<Neo4jJspNode> findFunctionsByComplexity(@Param("minScore") Integer minScore);

    /**
     * 查找特定標籤的函式
     */
    @Query("MATCH (n:JspNode {type: 'JSFunction'}) WHERE $tag IN n.purposeTags RETURN n")
    List<Neo4jJspNode> findFunctionsByTag(@Param("tag") String tag);

    /**
     * 刪除特定檔案的所有節點和關係
     */
    @Query("MATCH (n:JspNode {fileName: $fileName}) DETACH DELETE n")
    void deleteByFileName(@Param("fileName") String fileName);

    /**
     * 統計節點類型分布
     */
    @Query("MATCH (n:JspNode) RETURN n.type as type, count(n) as count ORDER BY count DESC")
    List<NodeTypeCount> getNodeTypeStatistics();

    /**
     * 統計關係類型分布
     */
    @Query("MATCH ()-[r]->() RETURN type(r) as relationshipType, count(r) as count ORDER BY count DESC")
    List<NodeTypeCount> getRelationshipTypeStatistics();

    /**
     * 檢查是否有任何 JSP 節點
     */
    @Query("MATCH (n:JspNode) RETURN count(n) > 0 as hasNodes")
    Boolean hasJspNodes();

    /**
     * 統計總節點數
     */
    @Query("MATCH (n:JspNode) RETURN count(n) as count")
    Long countJspNodes();

    /**
     * 統計總關係數
     */
    @Query("MATCH ()-[r]->() RETURN count(r) as count")
    Long countJspRelationships();

    /**
     * 刪除所有 JSP 節點和關係
     */
    @Query("MATCH (n:JspNode) DETACH DELETE n")
    void deleteAllJspData();
}