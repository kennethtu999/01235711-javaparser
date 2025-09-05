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
public class AstParserController {
    private static final Logger logger = LoggerFactory.getLogger(AstParserController.class);

    private final DiagramService diagramService;
    private final CodeExtractorService codeExtractorService;
    private final AstParserService astParserService;
    private final TaskManagementService taskManagementService;

    @Autowired
    public AstParserController(DiagramService diagramService, CodeExtractorService codeExtractorService,
            AstParserService astParserService, TaskManagementService taskManagementService) {
        this.diagramService = diagramService;
        this.codeExtractorService = codeExtractorService;
        this.astParserService = astParserService;
        this.taskManagementService = taskManagementService;
    }

    /**
     * 健康檢查端點
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("AST Parser Service is running");
    }

    /**
     * 非同步解析專案
     */
    @PostMapping("/parse")
    public ResponseEntity<ParseResponse> parseProject(@RequestBody ProcessRequest request) {
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
    @GetMapping("/parse/status/{taskId}")
    public ResponseEntity<TaskStatusResponse> getTaskStatus(@PathVariable String taskId) {
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
    @PostMapping("/generate-diagram")
    public ResponseEntity<String> generateDiagram(@RequestBody DiagramRequest request) {
        try {
            logger.info("收到圖表生成請求: {}", request);

            // 創建配置
            SequenceOutputConfig config = SequenceOutputConfig.builder()
                    .basePackage(request.getBasePackage())
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
    @PostMapping("/extract-code")
    public ResponseEntity<CodeExtractionResult> extractCode(@RequestBody CodeExtractionRequest request) {
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
    public static class DiagramRequest {
        private String entryPointMethodFqn;
        private String basePackage = "";
        private int depth = 5;

        // Getters and Setters
        public String getEntryPointMethodFqn() {
            return entryPointMethodFqn;
        }

        public void setEntryPointMethodFqn(String entryPointMethodFqn) {
            this.entryPointMethodFqn = entryPointMethodFqn;
        }

        public String getBasePackage() {
            return basePackage;
        }

        public void setBasePackage(String basePackage) {
            this.basePackage = basePackage;
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
                    ", basePackage='" + basePackage + '\'' +
                    ", depth=" + depth +
                    '}';
        }
    }

    /**
     * 解析響應DTO
     */
    public static class ParseResponse {
        private String taskId;
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
    public static class TaskStatusResponse {
        private String taskId;
        private String status;
        private String result;
        private String errorMessage;
        private long createdAt;
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
            java.nio.file.Path tempDir = java.nio.file.Paths.get(System.getProperty("java.io.tmpdir"),
                    "ast-parser", projectName);
            java.nio.file.Files.createDirectories(tempDir);
            logger.info("創建臨時輸出目錄: {}", tempDir);
            return tempDir.toString();
        } catch (Exception e) {
            logger.error("創建臨時輸出目錄失敗", e);
            throw new RuntimeException("創建臨時輸出目錄失敗: " + e.getMessage(), e);
        }
    }
}
