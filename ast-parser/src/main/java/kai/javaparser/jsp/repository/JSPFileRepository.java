package kai.javaparser.jsp.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import kai.javaparser.jsp.entity.JSPFileNode;

/**
 * JSP 檔案節點 Repository
 */
@Repository
public interface JSPFileRepository extends Neo4jRepository<JSPFileNode, String> {

    /**
     * 根據檔案名稱查找 JSP 檔案節點
     */
    Optional<JSPFileNode> findByFileName(String fileName);

    /**
     * 根據頁面代碼類別查找節點
     */
    List<JSPFileNode> findByPagecodeClass(String pagecodeClass);

    /**
     * 查找特定 ID 的節點
     */
    Optional<JSPFileNode> findById(String id);

    /**
     * 查找包含特定函式的 JSP 檔案
     */
    @Query("MATCH (file:JSPFile)-[:CONTAINS]->(func:JspNode {id: $functionId}) RETURN file")
    Optional<JSPFileNode> findFileContainingFunction(@Param("functionId") String functionId);

    /**
     * 查找與特定節點有互動關係的所有 JSP 檔案
     */
    @Query("MATCH (n:JSPFile {id: $nodeId})-[:INTERACTS_WITH]->(target) RETURN target")
    List<JSPFileNode> findInteractingFiles(@Param("nodeId") String nodeId);

    /**
     * 刪除特定檔案的所有 JSP 檔案節點和關係
     */
    @Query("MATCH (n:JSPFile {fileName: $fileName}) DETACH DELETE n")
    void deleteByFileName(@Param("fileName") String fileName);

    /**
     * 統計 JSP 檔案節點數
     */
    @Query("MATCH (n:JSPFile) RETURN count(n) as count")
    Long countJSPFiles();

    /**
     * 刪除所有 JSP 檔案節點和關係
     */
    @Query("MATCH (n:JSPFile) DETACH DELETE n")
    void deleteAllJSPFiles();
}
