package kai.javaparser.jsp.service;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.Getter;
import lombok.Setter;

/**
 * JSP AI 分析服務
 * 層次二：關聯性摘要與標籤化
 * 使用 LLM 對 JavaScript 函式進行深度分析
 */
@Service
public class JspAiAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(JspAiAnalysisService.class);

    @Autowired
    private ChatClient summaryChatClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 用於提取 DOM 元素 ID 的正規表示式
    private static final Pattern DOM_ID_PATTERN = Pattern.compile(
            "document\\.getElementById\\([\"']([^\"']+)[\"']\\)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern FORM_ELEMENT_PATTERN = Pattern.compile(
            "form1:([a-zA-Z0-9_]+)",
            Pattern.CASE_INSENSITIVE);

    /**
     * 分析 JavaScript 函式並生成結構化摘要
     * 
     * @param functionName    函式名稱
     * @param functionContent 函式內容
     * @param lineNumber      行數
     * @return AI 分析結果
     */
    public JavaScriptAnalysisResult analyzeJavaScriptFunction(String functionName, String functionContent,
            int lineNumber) {
        logger.info("開始 AI 分析 JavaScript 函式: {} (行數: {})", functionName, lineNumber);

        try {
            // 1. 先進行確定性提取
            JavaScriptAnalysisResult result = performDeterministicExtraction(functionName, functionContent, lineNumber);

            // 2. 使用 AI 進行深度分析
            String aiAnalysis = performAiAnalysis(functionName, functionContent, lineNumber, result);
            result.setAiAnalysis(aiAnalysis);

            // 3. 解析 AI 回應為結構化資料
            parseAiResponse(aiAnalysis, result);

            logger.info("JavaScript 函式 AI 分析完成: {}", functionName);
            return result;

        } catch (Exception e) {
            logger.error("AI 分析 JavaScript 函式時發生錯誤: {}", e.getMessage(), e);
            return createErrorResult(functionName, e.getMessage());
        }
    }

    /**
     * 確定性提取 - 使用正規表示式提取可確定的資訊
     */
    private JavaScriptAnalysisResult performDeterministicExtraction(String functionName, String functionContent,
            int lineNumber) {
        JavaScriptAnalysisResult result = new JavaScriptAnalysisResult();
        result.setFunctionName(functionName);
        result.setLineNumber(lineNumber);
        result.setFunctionContent(functionContent);

        // 提取 DOM 元素 ID
        Matcher domMatcher = DOM_ID_PATTERN.matcher(functionContent);
        while (domMatcher.find()) {
            String elementId = domMatcher.group(1);
            result.getInteractsWithIds().add(elementId);
        }

        // 提取表單元素
        Matcher formMatcher = FORM_ELEMENT_PATTERN.matcher(functionContent);
        while (formMatcher.find()) {
            String formElement = formMatcher.group(1);
            result.getFormElements().add(formElement);
        }

        // 檢查是否包含 AJAX 呼叫
        if (functionContent.contains("ajaxActionCall")) {
            result.setContainsAjaxCall(true);
            extractAjaxCalls(functionContent, result);
        }

        // 檢查是否包含導航
        if (functionContent.contains("winopen")) {
            result.setContainsNavigation(true);
        }

        // 檢查是否包含表單驗證
        if (functionContent.contains("check") || functionContent.contains("validate")) {
            result.getPurposeTags().add("Data_Validation");
        }

        // 檢查是否包含 UI 操作
        if (functionContent.contains("style.display") || functionContent.contains("value")) {
            result.getPurposeTags().add("UI_Manipulation");
        }

        return result;
    }

    /**
     * 使用 AI 進行深度分析
     */
    private String performAiAnalysis(String functionName, String functionContent, int lineNumber,
            JavaScriptAnalysisResult deterministicResult) {
        String promptTemplate = """
                你是一位資深 JSF 開發專家，請分析以下 JavaScript 函式並提供詳細的結構化摘要。

                函式資訊：
                - 函式名稱: {functionName}
                - 行數: {lineNumber}
                - 檔案類型: JSP (JavaServer Pages)

                函式原始碼：
                ```javascript
                {functionContent}
                ```

                已提取的確定性資訊：
                - 互動的 DOM 元素: {interactsWithIds}
                - 表單元素: {formElements}
                - 包含 AJAX 呼叫: {containsAjaxCall}
                - 包含導航: {containsNavigation}

                請提供以下格式的 JSON 回應：
                {{
                  "summary": "函式功能的詳細摘要，說明其主要用途和執行流程",
                  "purpose_tags": ["標籤1", "標籤2", "標籤3"],
                  "interacts_with_ids": ["完整的元素ID列表"],
                  "potential_backend_calls": [
                    {{"type": "ajax", "target": "方法名稱", "parameters": ["參數1", "參數2"], "line_number": 行數}},
                    {{"type": "form_submit", "target": "action方法", "parameters": ["參數1"], "line_number": 行數}}
                  ],
                  "ui_manipulation": [
                    "顯示/隱藏元素操作",
                    "設定元素值操作",
                    "樣式變更操作"
                  ],
                  "data_flow": "詳細的資料流向描述，包括輸入來源、處理過程、輸出目標",
                  "business_logic": "業務邏輯說明",
                  "error_handling": "錯誤處理機制",
                  "dependencies": ["依賴的其他函式或外部資源"],
                  "complexity_score": 1-10,
                  "maintainability_notes": "維護性建議"
                }}

                請用繁體中文回答，保持專業且詳細。特別注意：
                1. 識別所有與 JSF 元件的互動
                2. 找出隱藏的後端呼叫（如 ajaxActionCall）
                3. 分析資料驗證邏輯
                4. 評估函式的複雜度和維護性
                """;

        PromptTemplate template = new PromptTemplate(promptTemplate);

        Map<String, Object> variables = Map.of(
                "functionName", functionName,
                "lineNumber", lineNumber,
                "functionContent", functionContent,
                "interactsWithIds", String.join(", ", deterministicResult.getInteractsWithIds()),
                "formElements", String.join(", ", deterministicResult.getFormElements()),
                "containsAjaxCall", deterministicResult.isContainsAjaxCall(),
                "containsNavigation", deterministicResult.isContainsNavigation());

        Prompt prompt = template.create(variables);
        ChatResponse response = summaryChatClient.prompt(prompt).call().chatResponse();
        return response.getResult().getOutput().getText();
    }

    /**
     * 解析 AI 回應為結構化資料
     */
    private void parseAiResponse(String aiResponse, JavaScriptAnalysisResult result) {
        try {
            // 嘗試提取 JSON 部分
            String jsonPart = extractJsonFromResponse(aiResponse);
            JsonNode jsonNode = objectMapper.readTree(jsonPart);

            // 解析各個欄位
            if (jsonNode.has("summary")) {
                result.setSummary(jsonNode.get("summary").asText());
            }

            if (jsonNode.has("purpose_tags")) {
                jsonNode.get("purpose_tags").forEach(tag -> result.getPurposeTags().add(tag.asText()));
            }

            if (jsonNode.has("interacts_with_ids")) {
                jsonNode.get("interacts_with_ids").forEach(id -> result.getInteractsWithIds().add(id.asText()));
            }

            if (jsonNode.has("ui_manipulation")) {
                jsonNode.get("ui_manipulation")
                        .forEach(manipulation -> result.getUiManipulation().add(manipulation.asText()));
            }

            if (jsonNode.has("data_flow")) {
                result.setDataFlow(jsonNode.get("data_flow").asText());
            }

            if (jsonNode.has("business_logic")) {
                result.setBusinessLogic(jsonNode.get("business_logic").asText());
            }

            if (jsonNode.has("complexity_score")) {
                result.setComplexityScore(jsonNode.get("complexity_score").asInt());
            }

            if (jsonNode.has("maintainability_notes")) {
                result.setMaintainabilityNotes(jsonNode.get("maintainability_notes").asText());
            }

            // 解析後端呼叫
            if (jsonNode.has("potential_backend_calls")) {
                jsonNode.get("potential_backend_calls").forEach(call -> {
                    BackendCall backendCall = new BackendCall();
                    if (call.has("type"))
                        backendCall.setType(call.get("type").asText());
                    if (call.has("target"))
                        backendCall.setTarget(call.get("target").asText());
                    if (call.has("line_number"))
                        backendCall.setLineNumber(call.get("line_number").asInt());
                    if (call.has("parameters")) {
                        call.get("parameters").forEach(param -> backendCall.getParameters().add(param.asText()));
                    }
                    result.getPotentialBackendCalls().add(backendCall);
                });
            }

        } catch (Exception e) {
            logger.warn("解析 AI 回應失敗: {}", e.getMessage());
            result.setAiAnalysis(aiResponse); // 保留原始回應
        }
    }

    /**
     * 從 AI 回應中提取 JSON 部分
     */
    private String extractJsonFromResponse(String response) {
        // 尋找 JSON 開始和結束位置
        int jsonStart = response.indexOf("{");
        int jsonEnd = response.lastIndexOf("}");

        if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
            return response.substring(jsonStart, jsonEnd + 1);
        }

        return response; // 如果找不到 JSON，返回完整回應
    }

    /**
     * 提取 AJAX 呼叫
     */
    private void extractAjaxCalls(String functionContent, JavaScriptAnalysisResult result) {
        Pattern ajaxPattern = Pattern.compile(
                "ajaxActionCall\\s*\\([^,]+,\\s*\"([^\"]+)\"",
                Pattern.CASE_INSENSITIVE);

        Matcher matcher = ajaxPattern.matcher(functionContent);
        while (matcher.find()) {
            BackendCall ajaxCall = new BackendCall();
            ajaxCall.setType("ajax");
            ajaxCall.setTarget(matcher.group(1));
            ajaxCall.setLineNumber(findLineNumber(functionContent, matcher.start()));
            result.getPotentialBackendCalls().add(ajaxCall);
        }
    }

    /**
     * 計算行數
     */
    private int findLineNumber(String content, int position) {
        return content.substring(0, position).split("\n").length;
    }

    /**
     * 建立錯誤結果
     */
    private JavaScriptAnalysisResult createErrorResult(String functionName, String errorMessage) {
        JavaScriptAnalysisResult result = new JavaScriptAnalysisResult();
        result.setFunctionName(functionName);
        result.setError("AI 分析失敗: " + errorMessage);
        return result;
    }

    // 資料模型類別
    @Getter
    @Setter
    public static class JavaScriptAnalysisResult {
        private String functionName;
        private String functionContent;
        private int lineNumber;
        private String summary;
        private java.util.List<String> purposeTags = new java.util.ArrayList<>();
        private java.util.List<String> interactsWithIds = new java.util.ArrayList<>();
        private java.util.List<String> formElements = new java.util.ArrayList<>();
        private java.util.List<BackendCall> potentialBackendCalls = new java.util.ArrayList<>();
        private java.util.List<String> uiManipulation = new java.util.ArrayList<>();
        private String dataFlow;
        private String businessLogic;
        private boolean containsAjaxCall = false;
        private boolean containsNavigation = false;
        private int complexityScore = 0;
        private String maintainabilityNotes;
        private String aiAnalysis;
        private String error;
    }

    @Getter
    @Setter
    public static class BackendCall {
        private String type;
        private String target;
        private java.util.List<String> parameters = new java.util.ArrayList<>();
        private int lineNumber;
    }
}
