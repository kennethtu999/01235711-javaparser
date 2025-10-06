package kai.javaparser.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import kai.javaparser.service.AiSummaryService.SummaryData;

/**
 * AiSummaryService 的整合測試
 * 使用真實的 Ollama Qwen3:4b 模型進行測試
 * 需要 Ollama 服務運行在 localhost:11434
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
public class AiSummaryServiceTest {

        private static final Logger logger = LoggerFactory.getLogger(AiSummaryServiceTest.class);

        @Autowired
        private AiSummaryService aiSummaryService;

        /**
         * 測試與真實 Ollama Qwen3:4b 模型的整合
         * 只有在 OLLAMA_AVAILABLE 環境變數設為 true 時才會執行
         */
        @Test
        void testRealOllamaIntegration() {
                logger.info("=== 開始測試真實 Ollama Qwen3:4b 整合 ===");

                // 準備測試數據
                SummaryData testData = new SummaryData(
                                "成功", // healthStatus
                                "完成", // parseStatus
                                2500, // diagramResponseLength
                                3500, // extractResponseLength
                                5000 // mergedSourceLength
                );

                logger.info("測試數據: 健康檢查={}, 解析狀態={}, 圖表響應={}字元, 提取響應={}字元, 合併原始碼={}字元",
                                testData.getHealthStatus(),
                                testData.getParseStatus(),
                                testData.getDiagramResponseLength(),
                                testData.getExtractResponseLength(),
                                testData.getMergedSourceLength());

                // 執行 AI 摘要生成
                long startTime = System.currentTimeMillis();
                String summary = aiSummaryService.generateSummary(testData);
                long endTime = System.currentTimeMillis();

                long executionTime = endTime - startTime;
                logger.info("AI 摘要生成完成，耗時: {} 毫秒", executionTime);

                // 驗證結果
                assertNotNull(summary, "AI 摘要不應為 null");
                assertFalse(summary.isEmpty(), "AI 摘要不應為空");
                assertTrue(summary.length() > 50, "AI 摘要應有足夠的內容長度");

                // 驗證摘要內容包含預期的關鍵字
                String lowerSummary = summary.toLowerCase();
                assertTrue(lowerSummary.contains("測試") || lowerSummary.contains("test"),
                                "摘要應包含測試相關內容");
                assertTrue(lowerSummary.contains("成功") || lowerSummary.contains("完成") ||
                                lowerSummary.contains("success") || lowerSummary.contains("complete"),
                                "摘要應包含成功狀態相關內容");

                logger.info("=== AI 摘要內容 ===");
                logger.info(summary);
                logger.info("=== AI 摘要結束 ===");
                logger.info("摘要長度: {} 字元", summary.length());
        }
}
