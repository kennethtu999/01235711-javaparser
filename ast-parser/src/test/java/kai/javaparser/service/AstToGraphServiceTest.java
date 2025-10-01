package kai.javaparser.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import kai.javaparser.repository.FileSystemAstRepository;

/**
 * AST 到圖數據庫轉換服務測試類
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class AstToGraphServiceTest {

    @Autowired
    private AstToGraphService astToGraphService;

    @Autowired
    private Neo4jService neo4jService;

    @Autowired
    private FileSystemAstRepository astRepository;

    @BeforeEach
    void setUp() {
        // 清理測試數據
        try {
            astToGraphService.clearAstData();
        } catch (Exception e) {
            // 忽略清理錯誤
        }
    }

    @Test
    void testConvertAllAstToGraph() {
        // 測試轉換所有 AST 文件
        Map<String, Object> result = astToGraphService.convertAllAstToGraph();

        assertNotNull(result, "轉換結果不應為空");
        assertTrue(result.containsKey("success"), "應該包含成功狀態");
        assertTrue(result.containsKey("totalFiles"), "應該包含總文件數");
        assertTrue(result.containsKey("successFiles"), "應該包含成功文件數");
        assertTrue(result.containsKey("totalNodes"), "應該包含總節點數");
        assertTrue(result.containsKey("totalRelationships"), "應該包含總關係數");

        System.out.println("轉換結果: " + result);
    }

    @Test
    void testClearAstData() {
        // 先轉換一些數據
        astToGraphService.convertAllAstToGraph();

        // 獲取清理前的統計信息
        Map<String, Object> beforeStats = astToGraphService.getAstStatistics();
        int beforeClasses = (Integer) beforeStats.get("classes");

        // 清理數據
        astToGraphService.clearAstData();

        // 獲取清理後的統計信息
        Map<String, Object> afterStats = astToGraphService.getAstStatistics();
        int afterClasses = (Integer) afterStats.get("classes");

        // 如果之前有數據，清理後應該為 0
        if (beforeClasses > 0) {
            assertEquals(0, afterClasses, "清理後類別數量應該為 0");
        }

        System.out.println("清理前類別數: " + beforeClasses + ", 清理後類別數: " + afterClasses);
    }
}