package kai.javaparser.jsp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

import kai.javaparser.jsp.model.JspKnowledgeGraph;

/**
 * Neo4j 整合測試
 */
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
public class JspStructureAnalyzerServiceTest {

    @Autowired
    private JspStructureAnalyzerService analyzerService;

    @Autowired
    private JspKnowledgeGraphBuilder graphBuilder;

    @Autowired
    private Neo4jJspStorageService storageService;

    @Autowired
    private JspAstLinkService jspAstLinkService;

    @BeforeEach
    void setUp() {
        // 清理測試數據
        try {
            storageService.clearAllJspData();
        } catch (Exception e) {
            // 忽略清理錯誤
        }
    }

    @Test
    public void testStep2AddAll() {
        // 分析 JSP 內容
        var analysisResult = analyzerService.analyzeJspFile(
                "/Users/kenneth/git/01235711/01235711-javaparser/temp/cacq002/CACQ002_1.jsp", "CACQ002_1.jsp");
        assertNotNull(analysisResult);
        assertFalse(analysisResult.getJsfComponents().isEmpty());

        // 建構知識圖譜
        JspKnowledgeGraph graph = graphBuilder.buildKnowledgeGraph(analysisResult);
        assertNotNull(graph);
        assertFalse(graph.getNodes().isEmpty());
    }

    @Test
    public void testStep3AddJspAstLink() {
        jspAstLinkService.linkAllJspBackendMethods();
    }

    @Test
    void testStep1CleanAll() {
        // 獲取清理前的統計信息
        Map<String, Object> beforeStats = storageService.getDatabaseStatistics();
        long beforeNodes = beforeStats.get("totalNodes") != null ? ((Number) beforeStats.get("totalNodes")).longValue()
                : 0L;

        // 清理數據
        storageService.clearAllJspData();

        // 獲取清理後的統計信息
        Map<String, Object> afterStats = storageService.getDatabaseStatistics();
        long afterNodes = afterStats.get("totalNodes") != null ? ((Number) afterStats.get("totalNodes")).longValue()
                : 0L;

        // 如果之前有數據，清理後應該為 0
        if (beforeNodes > 0) {
            assertEquals(0, afterNodes, "清理後節點數量應該為 0");
        }

        System.out.println("清理前節點數: " + beforeNodes + ", 清理後節點數: " + afterNodes);
    }
}
