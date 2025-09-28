package kai.javaparser.controller;

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
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.util.HashSet;
import java.util.Set;
import io.swagger.v3.oas.annotations.tags.Tag;
import kai.javaparser.configuration.AppConfig;
import kai.javaparser.diagram.DiagramService;
import kai.javaparser.diagram.SequenceOutputConfig;
import kai.javaparser.model.ProcessRequest;
import kai.javaparser.service.AstParserService;
import kai.javaparser.service.CodeExtractorService;
import kai.javaparser.service.CodeExtractorService.CodeExtractionRequest;
import kai.javaparser.service.CodeExtractorService.CodeExtractionResult;
import kai.javaparser.service.TaskManagementService;
import kai.javaparser.service.TaskManagementService.TaskInfo;

/**
 * AST解析器REST控制器
 * 提供AST解析、圖表生成和代碼提取的API端點
 */
@RestController
@RequestMapping("/api/ast")
@Tag(name = "AST Parser", description = "Java AST解析器API，提供代碼解析、序列圖生成和代碼提取功能")
public class AstParserController {
    private static final Logger logger = LoggerFactory.getLogger(AstParserController.class);

    private final AppConfig appConfig;
    private final DiagramService diagramService;
    private final CodeExtractorService codeExtractorService;
    private final AstParserService astParserService;
    private final TaskManagementService taskManagementService;

    @Autowired
    public AstParserController(AppConfig appConfig, DiagramService diagramService,
            CodeExtractorService codeExtractorService,
            AstParserService astParserService, TaskManagementService taskManagementService) {
        this.appConfig = appConfig;
        this.diagramService = diagramService;
        this.codeExtractorService = codeExtractorService;
        this.astParserService = astParserService;
        this.taskManagementService = taskManagementService;
    }

    /**
     * 健康檢查端點
     */
    @Operation(summary = "健康檢查", description = "檢查AST解析器服務是否正常運行")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "服務正常運行", content = @Content(mediaType = "text/plain", examples = @ExampleObject(value = "AST Parser Service is running")))
    })
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("AST Parser Service is running");
    }

    /**
     * 非同步解析專案
     */
    @Operation(summary = "解析Java專案", description = "非同步解析指定的Java專案，生成AST數據")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "解析任務已啟動", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ParseResponse.class))),
            @ApiResponse(responseCode = "500", description = "解析請求失敗", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ParseResponse.class)))
    })
    @PostMapping("/parse")
    public ResponseEntity<ParseResponse> parseProject(
            @Parameter(description = "解析請求參數，包含專案路徑等資訊", required = true, example = "{\"projectPath\": \"/path/to/java/project\"}") @RequestBody ProcessRequest request) {
        try {
            logger.info("收到解析請求: {}", request);

            // 創建臨時輸出目錄
            String tempOutputDir = createTempOutputDir(request.getProjectPath());

            // 啟動非同步解析任務
            var future = astParserService.parseSourceDirectoryAsync(request.getProjectPath(), tempOutputDir);

            // 創建任務並立即返回任務ID
            String taskId = taskManagementService.createTask(future);

            // 標記任務為處理中
            taskManagementService.markTaskAsProcessing(taskId);

            logger.info("解析任務已啟動，任務ID: {}", taskId);

            return ResponseEntity.accepted().body(new ParseResponse(taskId, "解析任務已啟動"));

        } catch (Exception e) {
            logger.error("解析請求失敗", e);
            return ResponseEntity.internalServerError()
                    .body(new ParseResponse(null, "解析請求失敗: " + e.getMessage()));
        }
    }

    /**
     * 查詢任務狀態
     */
    @Operation(summary = "查詢任務狀態", description = "根據任務ID查詢解析任務的當前狀態")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "任務狀態查詢成功", content = @Content(mediaType = "application/json", schema = @Schema(implementation = TaskStatusResponse.class))),
            @ApiResponse(responseCode = "404", description = "任務不存在"),
            @ApiResponse(responseCode = "500", description = "查詢任務狀態失敗", content = @Content(mediaType = "application/json", schema = @Schema(implementation = TaskStatusResponse.class)))
    })
    @GetMapping("/parse/status/{taskId}")
    public ResponseEntity<TaskStatusResponse> getTaskStatus(
            @Parameter(description = "解析任務的唯一識別碼，由解析端點返回", required = true, example = "task-12345-67890-abcdef") @PathVariable("taskId") String taskId) {
        try {
            if (!taskManagementService.taskExists(taskId)) {
                return ResponseEntity.notFound().build();
            }

            TaskInfo taskInfo = taskManagementService.getTask(taskId);
            TaskStatusResponse response = new TaskStatusResponse(
                    taskInfo.getTaskId(),
                    taskInfo.getStatus().toString(),
                    taskInfo.getResult(),
                    taskInfo.getErrorMessage(),
                    taskInfo.getCreatedAt(),
                    taskInfo.getUpdatedAt());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("查詢任務狀態失敗", e);
            return ResponseEntity.internalServerError()
                    .body(new TaskStatusResponse(taskId, "ERROR", null,
                            "查詢任務狀態失敗: " + e.getMessage(), 0, 0));
        }
    }

    /**
     * 生成序列圖
     */
    @Operation(summary = "生成序列圖", description = "根據指定的入口方法生成Mermaid序列圖")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "序列圖生成成功", content = @Content(mediaType = "text/plain", examples = @ExampleObject(value = "sequenceDiagram\n    participant A\n    participant B\n    A->>B: Hello"))),
            @ApiResponse(responseCode = "500", description = "序列圖生成失敗", content = @Content(mediaType = "text/plain", examples = @ExampleObject(value = "圖表生成失敗: 錯誤訊息")))
    })
    @PostMapping("/generate-diagram")
    public ResponseEntity<String> generateDiagram(
            @Parameter(description = "圖表生成請求參數，包含入口方法、基礎包名和深度設定", required = true, example = "{\"entryPointMethodFqn\": \"com.example.MyClass.myMethod\", \"basePackage\": \"com.example\", \"depth\": 5}") @RequestBody DiagramRequest request) {
        try {
            logger.info("收到圖表生成請求: {}", request);

            // 創建配置
            SequenceOutputConfig config = SequenceOutputConfig.builder()
                    .basePackages(request.getBasePackages())
                    .depth(request.getDepth())
                    .build();

            // 生成圖表
            String diagram = diagramService.generateDiagram(request.getEntryPointMethodFqn(), config);

            logger.info("圖表生成完成，格式: {}, 長度: {} 字元",
                    diagramService.getFormatName(), diagram.length());

            return ResponseEntity.ok(diagram);

        } catch (Exception e) {
            logger.error("圖表生成失敗", e);
            return ResponseEntity.internalServerError()
                    .body("圖表生成失敗: " + e.getMessage());
        }
    }

    /**
     * 提取代碼
     */
    @Operation(summary = "提取代碼", description = "根據指定的入口方法提取相關的代碼片段")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "代碼提取成功", content = @Content(mediaType = "application/json", schema = @Schema(implementation = CodeExtractionResult.class))),
            @ApiResponse(responseCode = "500", description = "代碼提取失敗", content = @Content(mediaType = "application/json", schema = @Schema(implementation = CodeExtractionResult.class)))
    })
    @PostMapping("/extract-code")
    public ResponseEntity<CodeExtractionResult> extractCode(
            @Parameter(description = "代碼提取請求參數，指定要提取的入口方法和相關設定", required = true, example = "{\"entryPointMethodFqn\": \"com.example.MyClass.myMethod\", \"includeFields\": true, \"includeUnusedMethods\": false}") @RequestBody CodeExtractionRequest request) {
        try {
            logger.info("收到代碼提取請求: {}", request);

            // 執行代碼提取
            CodeExtractionResult result = codeExtractorService.extractCode(request);

            logger.info("代碼提取完成，涉及類別數: {}, 總行數: {}",
                    result.getTotalClasses(), result.getTotalLines());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("代碼提取失敗", e);
            return ResponseEntity.internalServerError()
                    .body(CodeExtractionResult.builder()
                            .entryPointMethodFqn(request.getEntryPointMethodFqn())
                            .involvedClasses(new java.util.HashSet<>())
                            .mergedSourceCode("")
                            .totalClasses(0)
                            .totalLines(0)
                            .errorMessage("代碼提取失敗: " + e.getMessage())
                            .build());
        }
    }

    /**
     * 圖表生成請求DTO
     */
    @Schema(description = "圖表生成請求參數")
    public static class DiagramRequest {
        @Schema(description = "入口方法的完全限定名，格式為 包名.類名.方法名", example = "com.example.MyClass.myMethod", required = true)
        private String entryPointMethodFqn;

        @Schema(description = "基礎包名列表，用於過濾序列圖中的類別，只顯示此包下的類別", example = "[\"com.example\", \"com.other\"]", defaultValue = "[]")
        private Set<String> basePackages = new HashSet<>();

        @Schema(description = "序列圖的遞歸深度，控制方法調用的層級深度", example = "5", defaultValue = "5", minimum = "1", maximum = "10")
        private int depth = 5;

        // Getters and Setters
        public String getEntryPointMethodFqn() {
            return entryPointMethodFqn;
        }

        public void setEntryPointMethodFqn(String entryPointMethodFqn) {
            this.entryPointMethodFqn = entryPointMethodFqn;
        }

        public Set<String> getBasePackages() {
            return basePackages;
        }

        public void setBasePackages(Set<String> basePackages) {
            this.basePackages = basePackages != null ? basePackages : new HashSet<>();
        }

        public int getDepth() {
            return depth;
        }

        public void setDepth(int depth) {
            this.depth = depth;
        }

        @Override
        public String toString() {
            return "DiagramRequest{" +
                    "entryPointMethodFqn='" + entryPointMethodFqn + '\'' +
                    ", basePackages=" + basePackages +
                    ", depth=" + depth +
                    '}';
        }
    }

    /**
     * 解析響應DTO
     */
    @Schema(description = "解析任務響應")
    public static class ParseResponse {
        @Schema(description = "解析任務的唯一識別碼，用於後續查詢任務狀態", example = "task-12345-67890-abcdef")
        private String taskId;

        @Schema(description = "操作結果的說明訊息", example = "解析任務已啟動")
        private String message;

        public ParseResponse(String taskId, String message) {
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
     * 任務狀態響應DTO
     */
    @Schema(description = "任務狀態響應")
    public static class TaskStatusResponse {
        @Schema(description = "任務的唯一識別碼", example = "task-12345-67890-abcdef")
        private String taskId;

        @Schema(description = "任務的當前狀態", example = "COMPLETED", allowableValues = { "PENDING", "PROCESSING",
                "COMPLETED", "ERROR" })
        private String status;

        @Schema(description = "任務執行結果的詳細資訊，成功時包含解析結果", example = "解析完成，共處理 15 個 Java 檔案")
        private String result;

        @Schema(description = "任務執行失敗時的錯誤訊息", example = "解析失敗：找不到指定的專案路徑")
        private String errorMessage;

        @Schema(description = "任務創建時間的 Unix 時間戳（毫秒）", example = "1640995200000")
        private long createdAt;

        @Schema(description = "任務最後更新時間的 Unix 時間戳（毫秒）", example = "1640995200000")
        private long updatedAt;

        public TaskStatusResponse(String taskId, String status, String result, String errorMessage,
                long createdAt, long updatedAt) {
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

    /**
     * 創建臨時輸出目錄
     */
    private String createTempOutputDir(String projectPath) {
        try {
            java.nio.file.Path projectPathObj = java.nio.file.Paths.get(projectPath);
            String projectName = projectPathObj.getFileName().toString();
            String tempDirPath = appConfig.getFullAstOutputDir(projectName);
            java.nio.file.Path tempDir = java.nio.file.Paths.get(tempDirPath);

            java.nio.file.Files.createDirectories(tempDir);
            logger.info("創建臨時輸出目錄: {} (使用配置: {})", tempDir, appConfig.getAstDir());
            return tempDir.toString();
        } catch (Exception e) {
            logger.error("創建臨時輸出目錄失敗", e);
            throw new RuntimeException("創建臨時輸出目錄失敗: " + e.getMessage(), e);
        }
    }
}
