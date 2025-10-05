package kai.javaparser.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * AstToGraphController 的完整整合測試
 * 測試所有 AST 到圖轉換相關的 API 端點功能，使用真實的系統 beans
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AstToGraphControllerTest {

    private static final Logger logger = LoggerFactory.getLogger(AstToGraphControllerTest.class);

    @Value("${app.testTempDir}")
    private String testTempDir;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        logger.info("=== 開始設置 AstToGraphController 測試數據 ===");

        // 清理並重新建立測試目錄
        setupTestDirectory();

        logger.info("=== AstToGraphController 測試數據設置完成 ===");
    }

    /**
     * 測試所有 API 端點的整合流程
     * 模擬一個完整的 AST 到圖轉換工作流程
     */
    @Test
    void testCompleteAstToGraphWorkflow() throws Exception {
        logger.info("=== 開始測試完整 AST 到圖轉換工作流程 ===");

        // 1. 測試健康檢查
        logger.info("步驟 1: 測試健康檢查端點");
        String healthResponse = mockMvc.perform(get("/api/ast-graph/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("AstToGraphService"))
                .andExpect(jsonPath("$.status").value("UP"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        logger.info("健康檢查響應: {}", healthResponse);
        writeContentToFile("astGraphHealthResponse.txt", healthResponse);

        // 2. 測試清理數據
        logger.info("步驟 6: 測試清理 AST 數據");
        String clearResponse = mockMvc.perform(delete("/api/ast-graph/clear"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        logger.info("清理數據響應: {}", clearResponse);
        writeContentToFile("astGraphClearResponse.json", clearResponse);

        // 3. 測試轉換所有 AST 文件為圖數據庫
        logger.info("步驟 2: 測試轉換所有 AST 文件為圖數據庫");
        String convertResponse = mockMvc.perform(post("/api/ast-graph/convert"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        logger.info("轉換數據庫響應: {}", convertResponse);
        writeContentToFile("astGraphConvertResponse.json", convertResponse);

        // 4. 測試獲取統計信息
        logger.info("步驟 4: 測試獲取 AST 圖統計信息");
        String statisticsResponse = mockMvc.perform(get("/api/ast-graph/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.statistics").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        logger.info("統計信息響應: {}", statisticsResponse);
        writeContentToFile("astGraphStatisticsResponse.json", statisticsResponse);

        // 5. 測試執行自定義查詢
        logger.info("步驟 5: 測試執行自定義 Cypher 查詢");
        testCustomQuery();

        logger.info("=== 完整 AST 到圖轉換工作流程測試完成 ===");
    }

    /**
     * 測試自定義查詢的輔助方法
     */
    private void testCustomQuery() throws Exception {
        logger.info("=== 測試自定義 Cypher 查詢 ===");

        // 創建測試查詢
        Map<String, Object> queryRequest = new HashMap<>();
        queryRequest.put("cypher", "MATCH (n:Class) RETURN n.name as className LIMIT 10");
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("limit", 10);
        queryRequest.put("parameters", parameters);

        String queryRequestJson = objectMapper.writeValueAsString(queryRequest);
        logger.info("查詢請求 JSON: {}", queryRequestJson);
        writeContentToFile("customQueryRequest.json", queryRequestJson);

        String queryResponse = mockMvc.perform(post("/api/ast-graph/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content(queryRequestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.cypher").value("MATCH (n:Class) RETURN n.name as className LIMIT 10"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        logger.info("自定義查詢響應: {}", queryResponse);
        writeContentToFile("customQueryResponse.json", queryResponse);
    }

    /**
     * 設置測試目錄
     */
    private void setupTestDirectory() {
        try {
            File testDir = new File(testTempDir);

            // 如果目錄存在，先刪除
            if (testDir.exists()) {
                logger.info("清理現有測試目錄: {}", testTempDir);
                deleteDirectory(testDir);
            }

            // 重新建立目錄
            if (testDir.mkdirs()) {
                logger.info("成功建立測試目錄: {}", testTempDir);
            } else {
                logger.warn("無法建立測試目錄: {}", testTempDir);
            }

        } catch (Exception e) {
            logger.error("設置測試目錄失敗: {}", e.getMessage());
            throw new RuntimeException("設置測試目錄失敗: " + e.getMessage(), e);
        }
    }

    /**
     * 遞歸刪除目錄
     */
    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }

    /**
     * 將內容寫入檔案
     */
    private void writeContentToFile(String fileName, String content) {
        try {
            File file = new File(testTempDir, fileName);
            // 確保父目錄存在
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            FileWriter writer = new FileWriter(file);
            writer.write(content);
            writer.close();

            logger.info("成功寫入檔案: {}", fileName);

        } catch (Exception e) {
            logger.error("寫入檔案失敗: {}", e.getMessage());
            throw new RuntimeException("寫入檔案失敗: " + e.getMessage(), e);
        }
    }
}
