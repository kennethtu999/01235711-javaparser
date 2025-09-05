package kai.javaparser.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import kai.javaparser.diagram.DiagramService;
import kai.javaparser.diagram.SequenceOutputConfig;
import kai.javaparser.service.CodeExtractorService;
import kai.javaparser.service.CodeExtractorService.CodeExtractionRequest;
import kai.javaparser.service.CodeExtractorService.CodeExtractionResult;

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

    @Autowired
    public AstParserController(DiagramService diagramService, CodeExtractorService codeExtractorService) {
        this.diagramService = diagramService;
        this.codeExtractorService = codeExtractorService;
    }

    /**
     * 健康檢查端點
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("AST Parser Service is running");
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
}
