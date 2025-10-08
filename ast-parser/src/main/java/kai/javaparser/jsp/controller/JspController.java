package kai.javaparser.jsp.controller;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import kai.javaparser.jsp.model.JspAnalysisRequest;
import kai.javaparser.jsp.model.JspAnalysisResult;
import kai.javaparser.jsp.service.JspStructureAnalyzerService;
import kai.javaparser.jsp.service.Neo4jJspStorageService;
import kai.javaparser.jsp.service.JspAstLinkService;
import kai.javaparser.ast.service.TaskManagementService;
import kai.javaparser.ast.service.TaskManagementService.TaskInfo;

/**
 * JSP 分析控制器
 * 提供 JSP 檔案分析和 AST 連結的 REST API
 */
@RestController
@RequestMapping("/api/jsp")
@Tag(name = "JSP 分析", description = "JSP 檔案分析和 AST 連結相關 API")
public class JspController {

    private static final Logger logger = LoggerFactory.getLogger(JspController.class);

    @Autowired
    private JspStructureAnalyzerService analyzerService;

    @Autowired
    private Neo4jJspStorageService storageService;

    @Autowired
    private JspAstLinkService jspAstLinkService;

    @Autowired
    private TaskManagementService taskManagementService;

    /**
     * 非同步解析 JSP 檔案
     */
    @Operation(summary = "解析 JSP 檔案", description = "非同步解析 JSP 檔案內容並存儲到圖數據庫")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "解析任務已啟動", content = @Content(mediaType = "application/json", schema = @Schema(implementation = JspParseResponse.class))),
            @ApiResponse(responseCode = "500", description = "解析請求失敗", content = @Content(mediaType = "application/json", schema = @Schema(implementation = JspParseResponse.class)))
    })
    @PostMapping("/parse")
    public ResponseEntity<JspParseResponse> parseJspFile(
            @Parameter(description = "JSP 分析請求，包含檔案路徑和檔案名稱", required = true, example = "{\"filePath\": \"/path/to/file.jsp\", \"fileName\": \"file.jsp\"}") @RequestBody JspAnalysisRequest request) {
        try {
            logger.info("收到 JSP 解析請求: {}", request);

            // 啟動非同步解析任務
            var future = parseJspFileAsync(request.getFilePath(), request.getFileName());

            // 創建任務並立即返回任務ID
            String taskId = taskManagementService.createTask(future);

            // 標記任務為處理中
            taskManagementService.markTaskAsProcessing(taskId);

            logger.info("JSP 解析任務已啟動，任務ID: {}", taskId);

            return ResponseEntity.accepted().body(new JspParseResponse(taskId, "JSP 解析任務已啟動"));

        } catch (Exception e) {
            logger.error("JSP 解析請求失敗", e);
            return ResponseEntity.internalServerError()
                    .body(new JspParseResponse(null, "JSP 解析請求失敗: " + e.getMessage()));
        }
    }

    /**
     * 查詢 JSP 解析任務狀態
     */
    @Operation(summary = "查詢任務狀態", description = "根據任務ID查詢 JSP 解析任務的當前狀態")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "任務狀態查詢成功", content = @Content(mediaType = "application/json", schema = @Schema(implementation = JspTaskStatusResponse.class))),
            @ApiResponse(responseCode = "404", description = "任務不存在"),
            @ApiResponse(responseCode = "500", description = "查詢任務狀態失敗", content = @Content(mediaType = "application/json", schema = @Schema(implementation = JspTaskStatusResponse.class)))
    })
    @GetMapping("/parse/status/{taskId}")
    public ResponseEntity<JspTaskStatusResponse> getTaskStatus(
            @Parameter(description = "JSP 解析任務的唯一識別碼，由解析端點返回", required = true, example = "task-12345-67890-abcdef") @PathVariable("taskId") String taskId) {
        try {
            if (!taskManagementService.taskExists(taskId)) {
                return ResponseEntity.notFound().build();
            }

            TaskInfo taskInfo = taskManagementService.getTask(taskId);
            JspTaskStatusResponse response = new JspTaskStatusResponse(
                    taskInfo.getTaskId(),
                    taskInfo.getStatus().toString(),
                    taskInfo.getResult(),
                    taskInfo.getErrorMessage(),
                    taskInfo.getCreatedAt(),
                    taskInfo.getUpdatedAt());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("查詢 JSP 任務狀態失敗", e);
            return ResponseEntity.internalServerError()
                    .body(new JspTaskStatusResponse(taskId, "FAILED", null,
                            "查詢任務狀態失敗: " + e.getMessage(), 0, 0));
        }
    }

    /**
     * 建立 JSP-AST 連結
     */
    @PostMapping("/link-ast")
    @Operation(summary = "建立 JSP-AST 連結", description = "為所有 JSP 後端方法建立與 AST 方法的連結")
    public ResponseEntity<Map<String, Object>> linkJspToAst() {
        try {
            logger.info("開始建立 JSP-AST 連結");

            int linkedCount = jspAstLinkService.linkAllJspBackendMethods();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "JSP-AST 連結建立完成");
            response.put("linkedCount", linkedCount);

            logger.info("JSP-AST 連結建立完成: {} 個方法成功連結", linkedCount);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("建立 JSP-AST 連結失敗", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "連結建立失敗: " + e.getMessage());
            response.put("error", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 非同步解析 JSP 檔案
     */
    @org.springframework.scheduling.annotation.Async
    public java.util.concurrent.CompletableFuture<String> parseJspFileAsync(String filePath, String fileName) {
        try {
            logger.info("開始解析 JSP 檔案: {}", fileName);

            // 1. 分析 JSP 檔案
            JspAnalysisResult result = analyzerService.analyzeJspFile(filePath, fileName);
            logger.info("JSP 分析完成: {} - 找到 {} 個 JSF 元件, {} 個 JS 函式",
                    fileName,
                    result.getJsfComponents().size(),
                    result.getJavascriptFunctions().size());

            // 2. 建構知識圖譜並存儲
            var graphBuilder = new kai.javaparser.jsp.service.JspKnowledgeGraphBuilder();
            var knowledgeGraph = graphBuilder.buildKnowledgeGraph(result);
            int savedNodes = storageService.saveKnowledgeGraph(knowledgeGraph);

            String resultMessage = String.format("JSP 解析完成: %s - 存儲了 %d 個節點, %d 個關係, %d 個 JSF 元件, %d 個 JS 函式",
                    fileName,
                    savedNodes,
                    knowledgeGraph.getRelationships().size(),
                    result.getJsfComponents().size(),
                    result.getJavascriptFunctions().size());

            logger.info(resultMessage);
            return java.util.concurrent.CompletableFuture.completedFuture(resultMessage);

        } catch (Exception e) {
            logger.error("非同步解析 JSP 檔案失敗", e);
            return java.util.concurrent.CompletableFuture.failedFuture(e);
        }
    }

    /**
     * JSP 解析響應 DTO
     */
    @Schema(description = "JSP 解析任務響應")
    public static class JspParseResponse {
        @Schema(description = "解析任務的唯一識別碼，用於後續查詢任務狀態", example = "task-12345-67890-abcdef")
        private String taskId;

        @Schema(description = "操作結果的說明訊息", example = "JSP 解析任務已啟動")
        private String message;

        public JspParseResponse(String taskId, String message) {
            this.taskId = taskId;
            this.message = message;
        }

        // Getters and Setters
        public String getTaskId() {
            return taskId;
        }

        public void setTaskId(String taskId) {
            this.taskId = taskId;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    /**
     * JSP 任務狀態響應 DTO
     */
    @Schema(description = "JSP 任務狀態響應")
    public static class JspTaskStatusResponse {
        @Schema(description = "任務的唯一識別碼", example = "task-12345-67890-abcdef")
        private String taskId;

        @Schema(description = "任務的當前狀態", example = "COMPLETED", allowableValues = { "PENDING", "PROCESSING",
                "COMPLETED", "FAILED" })
        private String status;

        @Schema(description = "任務執行結果的詳細資訊，成功時包含解析結果", example = "JSP 解析完成，共處理 1 個 JSP 檔案")
        private String result;

        @Schema(description = "任務執行失敗時的錯誤訊息", example = "解析失敗：找不到指定的檔案路徑")
        private String errorMessage;

        @Schema(description = "任務創建時間的 Unix 時間戳（毫秒）", example = "1640995200000")
        private long createdAt;

        @Schema(description = "任務最後更新時間的 Unix 時間戳（毫秒）", example = "1640995200000")
        private long updatedAt;

        public JspTaskStatusResponse(String taskId, String status, String result, String errorMessage, long createdAt,
                long updatedAt) {
            this.taskId = taskId;
            this.status = status;
            this.result = result;
            this.errorMessage = errorMessage;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        // Getters and Setters
        public String getTaskId() {
            return taskId;
        }

        public void setTaskId(String taskId) {
            this.taskId = taskId;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getResult() {
            return result;
        }

        public void setResult(String result) {
            this.result = result;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public long getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(long createdAt) {
            this.createdAt = createdAt;
        }

        public long getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(long updatedAt) {
            this.updatedAt = updatedAt;
        }
    }
}
