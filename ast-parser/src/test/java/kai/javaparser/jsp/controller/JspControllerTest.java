package kai.javaparser.jsp.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Paths;

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

import kai.javaparser.jsp.model.JspAnalysisRequest;

/**
 * JspController 的完整整合測試
 * 測試所有 JSP 分析相關的 API 端點功能，使用真實的系統 beans
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class JspControllerTest {

    private static final Logger logger = LoggerFactory.getLogger(JspControllerTest.class);

    @Value("${app.testTempDir}")
    private String testTempDir;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private JspAnalysisRequest jspAnalysisRequest;

    @BeforeEach
    void setUp() {
        logger.info("=== 開始設置 JspController 測試數據 ===");

        // 清理並重新建立測試目錄
        setupTestDirectory();

        // 初始化測試數據 - 使用真實的測試 JSP 檔案
        String currentProjectDir = Paths.get("").toAbsolutePath().toString();
        String jspFilePath = currentProjectDir + "/../temp/cacq002/CACQ002_1.jsp";
        String jspFileName = "CACQ002_1.jsp";

        jspAnalysisRequest = new JspAnalysisRequest();
        jspAnalysisRequest.setFilePath(jspFilePath);
        jspAnalysisRequest.setFileName(jspFileName);

        logger.info("設置 jspAnalysisRequest: filePath={}, fileName={}",
                jspAnalysisRequest.getFilePath(),
                jspAnalysisRequest.getFileName());

        logger.info("=== JspController 測試數據設置完成 ===");
    }

    /**
     * 測試所有 API 端點的整合流程
     * 模擬一個完整的 JSP 分析工作流程
     */
    @Test
    void testCompleteJspAnalysisWorkflow() throws Exception {
        logger.info("=== 開始測試完整 JSP 分析工作流程 ===");

        // 1. 測試非同步解析 JSP 檔案
        logger.info("步驟 1: 測試非同步解析 JSP 檔案");
        String parseRequestJson = objectMapper.writeValueAsString(jspAnalysisRequest);
        logger.info("解析請求 JSON: {}", parseRequestJson);
        writeContentToFile("jspParseRequest.json", parseRequestJson);

        String parseResponse = mockMvc.perform(post("/api/jsp/parse")
                .contentType(MediaType.APPLICATION_JSON)
                .content(parseRequestJson))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.taskId").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();
        logger.info("解析響應: {}", parseResponse);
        writeContentToFile("jspParseResponse.json", parseResponse);

        // 提取任務ID
        String taskId = extractTaskIdFromResponse(parseResponse);
        logger.info("提取到的任務ID: {}", taskId);
        writeContentToFile("jspTaskId.txt", taskId);

        // 2. 等待解析任務完成
        logger.info("步驟 2: 等待解析任務完成");
        waitForTaskCompletion(taskId);

        // 3. 測試建立 JSP-AST 連結
        logger.info("步驟 3: 測試建立 JSP-AST 連結");
        String linkResponse = mockMvc.perform(post("/api/jsp/link-ast"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        logger.info("連結響應: {}", linkResponse);
        writeContentToFile("jspLinkResponse.json", linkResponse);

        logger.info("=== 完整 JSP 分析工作流程測試完成 ===");
    }

    /**
     * 等待任務完成的輔助方法
     */
    private void waitForTaskCompletion(String taskId) throws Exception {
        logger.info("開始等待任務完成，任務ID: {}", taskId);
        int maxAttempts = 30; // 最多等待 30 秒
        int attempt = 0;

        while (attempt < maxAttempts) {
            logger.info("第 {} 次檢查任務狀態 (最多 {} 次)", attempt + 1, maxAttempts);

            String response = mockMvc.perform(get("/api/jsp/parse/status/{taskId}", taskId))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            logger.info("任務狀態響應: {}", response);

            // 檢查任務是否完成
            if (response.contains("\"status\" : \"COMPLETED\"")) {
                logger.info("任務已完成！");
                return; // 任務完成
            } else if (response.contains("\"status\" : \"FAILED\"")) {
                logger.error("任務執行失敗: {}", response);
                throw new RuntimeException("任務執行失敗: " + response);
            } else if (response.contains("\"status\" : \"PROCESSING\"")) {
                logger.info("任務正在處理中...");
            } else if (response.contains("\"status\" : \"PENDING\"")) {
                logger.info("任務等待中...");
            } else {
                logger.warn("未知的任務狀態: {}", response);
                // 如果是未知狀態，也停止重複檢查，避免無限循環
                throw new RuntimeException("未知的任務狀態，停止檢查: " + response);
            }

            // 等待 1 秒後重試
            logger.info("等待 1 秒後重試...");
            Thread.sleep(1000);
            attempt++;
        }

        logger.error("任務等待超時，任務ID: {}", taskId);
        throw new RuntimeException("任務等待超時，任務ID: " + taskId);
    }

    /**
     * 從響應中提取任務ID的輔助方法
     */
    private String extractTaskIdFromResponse(String response) {
        logger.info("開始從響應中提取任務ID");
        logger.debug("原始響應: {}", response);

        try {
            // 使用 ObjectMapper 來解析 JSON 響應
            com.fasterxml.jackson.databind.JsonNode jsonNode = objectMapper.readTree(response);
            String taskId = jsonNode.get("taskId").asText();
            logger.info("成功提取任務ID: {}", taskId);
            return taskId;
        } catch (Exception e) {
            logger.warn("JSON 解析失敗，嘗試字串解析: {}", e.getMessage());
            // 如果 JSON 解析失敗，使用簡單的字串解析
            int startIndex = response.indexOf("\"taskId\":\"") + 10;
            int endIndex = response.indexOf("\"", startIndex);
            if (startIndex > 9 && endIndex > startIndex) {
                String taskId = response.substring(startIndex, endIndex);
                logger.info("字串解析成功，任務ID: {}", taskId);
                return taskId;
            }
            logger.error("無法從響應中提取任務ID: {}", response);
            throw new RuntimeException("無法從響應中提取任務ID: " + response, e);
        }
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
