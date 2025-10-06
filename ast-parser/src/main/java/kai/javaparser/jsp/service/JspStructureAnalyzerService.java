package kai.javaparser.jsp.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import lombok.Getter;
import lombok.Setter;

/**
 * JSP 結構化分析服務
 * 層次一：確定性結構化資訊提取
 * 使用正規表示式精確提取 JSF 元件、JavaScript 函式等結構化資料
 */
@Service
public class JspStructureAnalyzerService {

    private static final Logger logger = LoggerFactory.getLogger(JspStructureAnalyzerService.class);

    // 移除未使用的依賴

    // 正規表示式模式定義
    private static final Pattern JSF_ACTION_PATTERN = Pattern.compile(
            "<(jc:commandLinkEx|jc:download)\\s+[^>]*?action=\"#\\{([^}]+)\\}",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern JSF_DECODE_PATTERN = Pattern.compile(
            "<hx:scriptCollector\\s+[^>]*?decode=\"#\\{([^}]+)\\}",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern JSF_ID_PATTERN = Pattern.compile(
            "id=\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PAGECODE_PATTERN = Pattern.compile(
            "<%--\\s*jsf:pagecode[^>]*location=\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SCRIPT_BLOCK_PATTERN = Pattern.compile(
            "<script[^>]*>(.*?)</script>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern JS_FUNCTION_PATTERN = Pattern.compile(
            "function\\s+([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*\\(",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern AJAX_CALL_PATTERN = Pattern.compile(
            "ajaxActionCall\\s*\\([^,]+,\\s*\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern WINOPEN_PATTERN = Pattern.compile(
            "winopen\\s*\\([^,]+,\\s*[^,]+,\\s*[^)]+\\)",
            Pattern.CASE_INSENSITIVE);

    /**
     * 分析 JSP 檔案（從檔案路徑讀取）
     * 
     * @param filePath JSP 檔案路徑
     * @param fileName 檔案名稱
     * @return 分析結果
     */
    public JspAnalysisResult analyzeJspFile(String filePath, String fileName) {
        try {
            String jspContent = Files.readString(Paths.get(filePath));
            return analyzeJspContent(jspContent, fileName);
        } catch (IOException e) {
            logger.error("讀取 JSP 檔案失敗: {}", e.getMessage(), e);
            JspAnalysisResult result = new JspAnalysisResult();
            result.setFileName(fileName);
            result.setError("讀取檔案失敗: " + e.getMessage());
            return result;
        }
    }

    /**
     * 分析 JSP 檔案並提取結構化資訊
     * 
     * @param jspContent JSP 檔案內容
     * @param fileName   檔案名稱
     * @return 分析結果
     */
    public JspAnalysisResult analyzeJspContent(String jspContent, String fileName) {
        logger.info("開始分析 JSP 檔案: {}", fileName);

        JspAnalysisResult result = new JspAnalysisResult();
        result.setFileName(fileName);
        result.setAnalysisTimestamp(new Date());

        try {
            // 1. 提取 Pagecode 類別路徑
            extractPagecodeClass(jspContent, result);

            // 2. 提取 JSF 元件與後端呼叫
            extractJsfComponents(jspContent, result);

            // 3. 提取 JavaScript 區塊和函式
            extractJavaScriptFunctions(jspContent, result);

            // 4. 提取外部 JS 引用
            extractExternalJsReferences(jspContent, result);

            // 5. 使用 AI 分析 JavaScript 函式 (暫時註解，等待 AI 服務整合)
            // analyzeJavaScriptWithAI(result);

            logger.info("JSP 檔案分析完成: {} - 找到 {} 個 JSF 元件, {} 個 JS 函式",
                    fileName, result.getJsfComponents().size(), result.getJavascriptFunctions().size());

        } catch (Exception e) {
            logger.error("分析 JSP 檔案時發生錯誤: {}", e.getMessage(), e);
            result.setError("分析失敗: " + e.getMessage());
        }

        return result;
    }

    /**
     * 提取 Pagecode 類別路徑
     */
    private void extractPagecodeClass(String content, JspAnalysisResult result) {
        Matcher matcher = PAGECODE_PATTERN.matcher(content);
        if (matcher.find()) {
            String location = matcher.group(1);
            // 將路徑轉換為類別名稱
            String className = location.replace("/src/", "").replace(".java", "").replace("/", ".");
            result.setPagecodeClass(className);
            logger.debug("找到 Pagecode 類別: {}", className);
        }
    }

    /**
     * 提取 JSF 元件與後端呼叫
     */
    private void extractJsfComponents(String content, JspAnalysisResult result) {
        Matcher actionMatcher = JSF_ACTION_PATTERN.matcher(content);

        while (actionMatcher.find()) {
            String componentType = actionMatcher.group(1);
            String action = actionMatcher.group(2);

            // 提取元件 ID
            String componentId = extractIdFromContext(content, actionMatcher.start());

            JsfComponent component = new JsfComponent();
            component.setId(componentId);
            component.setType(componentType);
            component.setAction(action);
            component.setLineNumber(findLineNumber(content, actionMatcher.start()));

            result.getJsfComponents().add(component);
            logger.debug("找到 JSF 元件: {} - {} -> {}", componentId, componentType, action);
        }

        // 提取 scriptCollector 的 decode 屬性
        Matcher decodeMatcher = JSF_DECODE_PATTERN.matcher(content);
        while (decodeMatcher.find()) {
            String decodeAction = decodeMatcher.group(1);
            String componentId = extractIdFromContext(content, decodeMatcher.start());

            JsfComponent component = new JsfComponent();
            component.setId(componentId);
            component.setType("scriptCollector");
            component.setAction(decodeAction);
            component.setEventType("onPagePost");
            component.setLineNumber(findLineNumber(content, decodeMatcher.start()));

            result.getJsfComponents().add(component);
            logger.debug("找到 ScriptCollector: {} -> {}", componentId, decodeAction);
        }
    }

    /**
     * 提取 JavaScript 函式
     */
    private void extractJavaScriptFunctions(String content, JspAnalysisResult result) {
        Matcher scriptMatcher = SCRIPT_BLOCK_PATTERN.matcher(content);

        while (scriptMatcher.find()) {
            String scriptContent = scriptMatcher.group(1);
            int scriptStartLine = findLineNumber(content, scriptMatcher.start());

            // 提取函式定義
            Matcher functionMatcher = JS_FUNCTION_PATTERN.matcher(scriptContent);
            while (functionMatcher.find()) {
                String functionName = functionMatcher.group(1);

                JavaScriptFunction jsFunction = new JavaScriptFunction();
                jsFunction.setName(functionName);
                jsFunction.setLineNumber(scriptStartLine + findLineNumber(scriptContent, functionMatcher.start()));

                // 提取函式完整內容
                String fullFunction = extractFullFunction(scriptContent, functionMatcher.start());
                jsFunction.setContent(fullFunction);

                // 檢查是否包含 AJAX 呼叫
                if (AJAX_CALL_PATTERN.matcher(fullFunction).find()) {
                    jsFunction.setContainsAjaxCall(true);
                    extractAjaxCalls(fullFunction, jsFunction);
                }

                // 檢查是否包含 winopen 呼叫
                if (WINOPEN_PATTERN.matcher(fullFunction).find()) {
                    jsFunction.setContainsNavigation(true);
                }

                result.getJavascriptFunctions().add(jsFunction);
                logger.debug("找到 JS 函式: {} (行數: {})", functionName, jsFunction.getLineNumber());
            }
        }
    }

    /**
     * 提取外部 JS 引用
     */
    private void extractExternalJsReferences(String content, JspAnalysisResult result) {
        Pattern jsRefPattern = Pattern.compile(
                "<jc:script[^>]*src=\"([^\"]+)\"",
                Pattern.CASE_INSENSITIVE);

        Matcher matcher = jsRefPattern.matcher(content);
        while (matcher.find()) {
            String jsFile = matcher.group(1);
            result.getExternalJsReferences().add(jsFile);
            logger.debug("找到外部 JS 引用: {}", jsFile);
        }
    }

    // AI 分析相關方法暫時註解，等待 AI 服務整合
    /*
     * private void analyzeJavaScriptWithAI(JspAnalysisResult result) {
     * // AI 分析實作
     * }
     */

    // 輔助方法
    private String extractIdFromContext(String content, int position) {
        // 尋找包含匹配位置的完整標籤
        int start = position;
        int end = position;

        // 向前找到標籤開始
        while (start > 0 && content.charAt(start) != '<') {
            start--;
        }

        // 向後找到標籤結束
        while (end < content.length() && content.charAt(end) != '>') {
            end++;
        }

        if (start < end && end < content.length()) {
            String tagContent = content.substring(start, end + 1);
            Matcher idMatcher = JSF_ID_PATTERN.matcher(tagContent);
            if (idMatcher.find()) {
                return idMatcher.group(1);
            }
        }

        return "unknown";
    }

    private int findLineNumber(String content, int position) {
        return content.substring(0, position).split("\n").length;
    }

    private String extractFullFunction(String scriptContent, int startPos) {
        int braceCount = 0;
        int pos = startPos;
        boolean inFunction = false;

        while (pos < scriptContent.length()) {
            char c = scriptContent.charAt(pos);
            if (c == '{') {
                braceCount++;
                inFunction = true;
            } else if (c == '}') {
                braceCount--;
                if (inFunction && braceCount == 0) {
                    return scriptContent.substring(startPos, pos + 1);
                }
            }
            pos++;
        }

        return scriptContent.substring(startPos, Math.min(startPos + 500, scriptContent.length()));
    }

    private void extractAjaxCalls(String functionContent, JavaScriptFunction jsFunction) {
        Matcher ajaxMatcher = AJAX_CALL_PATTERN.matcher(functionContent);
        while (ajaxMatcher.find()) {
            jsFunction.getAjaxCalls().add(ajaxMatcher.group(1));
        }
    }

    // 資料模型類別
    @Getter
    @Setter
    public static class JspAnalysisResult {
        private String fileName;
        private Date analysisTimestamp;
        private String pagecodeClass;
        private List<JsfComponent> jsfComponents = new ArrayList<>();
        private List<JavaScriptFunction> javascriptFunctions = new ArrayList<>();
        private List<String> externalJsReferences = new ArrayList<>();
        private String error;
    }

    @Getter
    @Setter
    public static class JsfComponent {
        private String id;
        private String type;
        private String action;
        private String eventType;
        private int lineNumber;
    }

    @Getter
    @Setter
    public static class JavaScriptFunction {
        private String name;
        private String content;
        private int lineNumber;
        private boolean containsAjaxCall = false;
        private boolean containsNavigation = false;
        private List<String> ajaxCalls = new ArrayList<>();
        private String aiAnalysis;
    }
}
