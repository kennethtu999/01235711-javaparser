package kai.javaparser;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.File;
import java.io.FileWriter;
import java.util.Set;

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

import kai.javaparser.controller.AstParserController.DiagramRequest;
import kai.javaparser.model.ProcessRequest;
import kai.javaparser.service.CodeExtractorService;
import kai.javaparser.service.CodeExtractorService.CodeExtractionRequest;
import kai.javaparser.service.CodeExtractorService.CodeExtractionResult;

/**
 * AstParserController 的完整整合測試
 * 測試所有 API 端點的功能，使用真實的系統 beans
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AstParserControllerTest {

    private static final Logger logger = LoggerFactory.getLogger(AstParserControllerTest.class);

    @Value("${app.testTempDir}")
    private String testTempDir;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private ProcessRequest processRequest;
    private DiagramRequest diagramRequest;
    private CodeExtractionRequest codeExtractionRequest;

    @BeforeEach
    void setUp() {
        logger.info("=== 開始設置測試數據 ===");

        // 清理並重新建立測試目錄
        setupTestDirectory();

        // 初始化測試數據 - 使用真實的測試專案路徑
        processRequest = new ProcessRequest();
        processRequest.setProjectPath("/Users/kenneth/git/sc");
        logger.info("設置 processRequest: projectPath={}", processRequest.getProjectPath());

        diagramRequest = new DiagramRequest();
        diagramRequest.setEntryPointMethodFqn("pagecode.cac.cacq001.CACQ001_1.initViewForm()");
        diagramRequest.setBasePackages(Set.of("pagecode", "com", "tw"));
        diagramRequest.setDepth(5);
        logger.info("設置 diagramRequest: entryPoint={}, basePackages={}, depth={}",
                diagramRequest.getEntryPointMethodFqn(),
                diagramRequest.getBasePackages(),
                diagramRequest.getDepth());

        codeExtractionRequest = CodeExtractorService.CodeExtractionRequest.builder()
                .entryPointMethodFqn("pagecode.cac.cacq001.CACQ001_1.initViewForm()")
                .astDir("/Users/kenneth/git/01235711/01235711-javaparser/ast-parser/parsed-ast")
                .basePackages(Set.of("pagecode", "com", "tw"))
                .maxDepth(5)
                .includeImports(true)
                .includeComments(false)
                .extractOnlyUsedMethods(true)
                .includeConstructors(true)
                .build();
        logger.info("設置 codeExtractionRequest: entryPoint={}, astDir={}, basePackages={}",
                codeExtractionRequest.getEntryPointMethodFqn(),
                codeExtractionRequest.getAstDir(),
                codeExtractionRequest.getBasePackages());

        logger.info("=== 測試數據設置完成 ===");
    }

    /**
     * 測試所有 API 端點的整合流程
     * 模擬一個完整的用戶使用流程，包含非同步任務等待
     */
    @Test
    void testCompleteApiWorkflow() throws Exception {
        logger.info("=== 開始測試完整 API 工作流程 ===");

        // 1. 測試健康檢查
        logger.info("步驟 1: 測試健康檢查端點");
        String healthResponse = mockMvc.perform(get("/api/ast/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("AST Parser Service is running"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        logger.info("健康檢查響應: {}", healthResponse);
        writeContentToFile("healthResponse.txt", healthResponse);

        // 2. 測試解析專案 - 非同步啟動
        // testParseProject();

        // 3. 測試生成序列圖
        logger.info("步驟 4: 測試生成序列圖端點");
        String diagramRequestJson = objectMapper.writeValueAsString(diagramRequest);
        logger.info("圖表請求 JSON: {}", diagramRequestJson);
        writeContentToFile("diagramRequest.json", diagramRequestJson);

        String diagramResponse = mockMvc.perform(post("/api/ast/generate-diagram")
                .contentType(MediaType.APPLICATION_JSON)
                .content(diagramRequestJson))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        logger.info("圖表生成響應長度: {} 字元", diagramResponse.length());
        writeContentToFile("diagramResponse.txt", diagramResponse);

        // 4. 測試代碼提取
        logger.info("步驟 5: 測試代碼提取端點");
        String extractRequestJson = objectMapper.writeValueAsString(codeExtractionRequest);
        logger.info("代碼提取請求 JSON: {}", extractRequestJson);
        writeContentToFile("extractRequest.json", extractRequestJson);

        String extractResponse = mockMvc.perform(post("/api/ast/extract-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content(extractRequestJson))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        logger.info("代碼提取響應長度: {} 字元", extractResponse.length());
        writeContentToFile("extractResponse.json", extractResponse);

        CodeExtractionResult result = objectMapper.readValue(extractResponse, CodeExtractionResult.class);
        logger.info("合併後的原始碼: {} 字元", result.getMergedSourceCode().length());
        writeContentToFile("mergedSourceCode.md", result.getMergedSourceCode());

        logger.info("=== 完整 API 工作流程測試完成 ===");
    }

    /**
     * 測試解析專案端點 - 非同步啟動
     * 
     * @return 任務ID
     * @throws Exception 如果測試失敗
     */
    private void testParseProject() throws Exception {
        logger.info("步驟 2: 測試解析專案端點");
        String parseRequestJson = objectMapper.writeValueAsString(processRequest);
        logger.info("解析請求 JSON: {}", parseRequestJson);
        writeContentToFile("parseRequest.json", parseRequestJson);

        String parseResponse = mockMvc.perform(post("/api/ast/parse")
                .contentType(MediaType.APPLICATION_JSON)
                .content(parseRequestJson))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.taskId").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();
        logger.info("解析響應: {}", parseResponse);
        writeContentToFile("parseResponse.json", parseResponse);

        // 提取任務ID
        String taskId = extractTaskIdFromResponse(parseResponse);
        logger.info("提取到的任務ID: {}", taskId);
        writeContentToFile("taskId.txt", taskId);

        // 3. 等待解析任務完成
        logger.info("步驟 3: 等待解析任務完成");
        waitForTaskCompletion(taskId);
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

            String response = mockMvc.perform(get("/api/ast/parse/status/{taskId}", taskId))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            logger.info("任務狀態響應: {}", response);

            // 檢查任務是否完成 - 修復狀態檢查邏輯
            if (response.contains("\"status\" : \"COMPLETED\"")) {
                logger.info("任務已完成！");
                return; // 任務完成
            } else if (response.contains("\"status\" : \"ERROR\"")) {
                logger.error("任務執行失敗: {}", response);
                throw new RuntimeException("任務執行失敗: " + response);
            } else if (response.contains("\"status\" : \"PROCESSING\"")) {
                logger.info("任務正在處理中...");
            } else if (response.contains("\"status\" : \"PENDING\"")) {
                logger.info("任務等待中...");
            } else {
                logger.warn("未知的任務狀態: {}", response);
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

        } catch (Exception e) {
            logger.error("寫入檔案失敗: {}", e.getMessage());
            throw new RuntimeException("寫入檔案失敗: " + e.getMessage(), e);
        }
    }
}