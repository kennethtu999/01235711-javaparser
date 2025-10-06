package kai.javaparser.service;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.Getter;
import lombok.Setter;

/**
 * AI Summary 服務
 * 使用 Spring AI 和 Ollama 來生成測試結果的摘要
 */
@Service
public class AiSummaryService {

    private static final Logger logger = LoggerFactory.getLogger(AiSummaryService.class);

    @Autowired
    private ChatClient summaryChatClient;

    /**
     * 生成測試工作流程的摘要
     * 
     * @param testResults 測試結果數據
     * @return AI 生成的摘要
     */
    public String generateSummary(SummaryData testResults) {
        logger.info("開始生成測試摘要，數據長度: {} 字元", testResults.getTotalContentLength());

        try {
            // 創建提示模板
            String promptTemplate = """
                    請分析以下 Java AST Parser 測試結果，並生成一個簡潔的摘要報告。

                    測試概況：
                    - 健康檢查：{healthStatus}
                    - 解析任務狀態：{parseStatus}
                    - 圖表生成響應長度：{diagramResponseLength} 字元
                    - 代碼提取響應長度：{extractResponseLength} 字元
                    - 合併後原始碼長度：{mergedSourceLength} 字元

                    請提供：
                    1. 測試執行狀態總結
                    2. 主要功能驗證結果
                    3. 數據處理規模評估
                    4. 潛在問題或建議

                    請用繁體中文回答，保持專業且簡潔。
                    """;

            PromptTemplate template = new PromptTemplate(promptTemplate);

            // 準備模板變數
            Map<String, Object> variables = Map.of(
                    "healthStatus", testResults.getHealthStatus(),
                    "parseStatus", testResults.getParseStatus(),
                    "diagramResponseLength", testResults.getDiagramResponseLength(),
                    "extractResponseLength", testResults.getExtractResponseLength(),
                    "mergedSourceLength", testResults.getMergedSourceLength());

            Prompt prompt = template.create(variables);

            // 調用 AI 生成摘要
            ChatResponse response = summaryChatClient.prompt(prompt).call().chatResponse();
            String summary = response.getResult().getOutput().getText();

            logger.info("AI 摘要生成完成，長度: {} 字元", summary.length());
            return summary;

        } catch (Exception e) {
            logger.error("生成 AI 摘要時發生錯誤: {}", e.getMessage(), e);
            return "AI 摘要生成失敗: " + e.getMessage();
        }
    }

    /**
     * 測試結果數據類
     */
    @Getter
    @Setter
    public static class SummaryData {
        private String healthStatus;
        private String parseStatus;
        private int diagramResponseLength;
        private int extractResponseLength;
        private int mergedSourceLength;
        private int totalContentLength;

        // 建構子
        public SummaryData() {
        }

        public SummaryData(String healthStatus, String parseStatus,
                int diagramResponseLength, int extractResponseLength,
                int mergedSourceLength) {
            this.healthStatus = healthStatus;
            this.parseStatus = parseStatus;
            this.diagramResponseLength = diagramResponseLength;
            this.extractResponseLength = extractResponseLength;
            this.mergedSourceLength = mergedSourceLength;
            this.totalContentLength = diagramResponseLength + extractResponseLength + mergedSourceLength;
        }

    }
}