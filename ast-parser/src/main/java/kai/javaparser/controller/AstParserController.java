package kai.javaparser.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import kai.javaparser.service.AstParserService;
import kai.javaparser.service.CodeExtractorService;

/**
 * AST解析REST控制器
 * 提供AST解析的Web API接口
 */
@RestController
@RequestMapping("/api/ast")
@CrossOrigin(origins = "*")
public class AstParserController {

    @Autowired
    private AstParserService astParserService;

    @Autowired
    private CodeExtractorService codeExtractorService;

    /**
     * 解析單個源碼目錄
     * 
     * @param request 解析請求
     * @return 解析結果
     */
    @PostMapping("/parse")
    public ResponseEntity<String> parseSourceDirectory(@RequestBody ParseRequest request) {
        try {
            String result = astParserService.parseSourceDirectory(request.getSourceRoot(), request.getOutputDir());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    /**
     * 高級AST解析
     * 
     * @param request 高級解析請求
     * @return 解析結果
     */
    @PostMapping("/parse/advanced")
    public ResponseEntity<String> executeAdvancedParsing(@RequestBody AdvancedParseRequest request) {
        try {
            String result = astParserService.executeAstParsing(
                    request.getBaseFolder(),
                    request.getSourceRootDirs(),
                    request.getOutputDir(),
                    request.getClasspath(),
                    request.getJavaComplianceLevel());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    /**
     * 健康檢查端點
     * 
     * @return 服務狀態
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("AST Parser Service is running");
    }

    /**
     * 代碼提取端點 - 案例 #3
     * 能經由 Java Method 取出對應的程式碼，供AI Prompt使用
     * 
     * @param request 代碼提取請求
     * @return 提取結果
     */
    @PostMapping("/extract-code")
    public ResponseEntity<CodeExtractorService.CodeExtractionResult> extractCode(
            @RequestBody CodeExtractionRequest request) {
        try {
            CodeExtractorService.CodeExtractionRequest extractRequest = CodeExtractorService.CodeExtractionRequest
                    .builder()
                    .entryPointMethodFqn(request.getEntryPointMethodFqn())
                    .astDir(request.getAstDir())
                    .basePackage(request.getBasePackage())
                    .maxDepth(request.getMaxDepth())
                    .includeImports(request.isIncludeImports())
                    .includeComments(request.isIncludeComments())
                    .extractOnlyUsedMethods(request.isExtractOnlyUsedMethods())
                    .build();

            CodeExtractorService.CodeExtractionResult result = codeExtractorService.extractCode(extractRequest);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            CodeExtractorService.CodeExtractionResult errorResult = CodeExtractorService.CodeExtractionResult.builder()
                    .entryPointMethodFqn(request.getEntryPointMethodFqn())
                    .involvedClasses(new java.util.HashSet<>())
                    .mergedSourceCode("")
                    .totalClasses(0)
                    .totalLines(0)
                    .errorMessage("代碼提取失敗: " + e.getMessage())
                    .build();
            return ResponseEntity.badRequest().body(errorResult);
        }
    }

    /**
     * 簡單解析請求模型
     */
    public static class ParseRequest {
        private String sourceRoot;
        private String outputDir;

        // Getters and Setters
        public String getSourceRoot() {
            return sourceRoot;
        }

        public void setSourceRoot(String sourceRoot) {
            this.sourceRoot = sourceRoot;
        }

        public String getOutputDir() {
            return outputDir;
        }

        public void setOutputDir(String outputDir) {
            this.outputDir = outputDir;
        }
    }

    /**
     * 高級解析請求模型
     */
    public static class AdvancedParseRequest {
        private String baseFolder;
        private String sourceRootDirs;
        private String outputDir;
        private String classpath;
        private String javaComplianceLevel;

        // Getters and Setters
        public String getBaseFolder() {
            return baseFolder;
        }

        public void setBaseFolder(String baseFolder) {
            this.baseFolder = baseFolder;
        }

        public String getSourceRootDirs() {
            return sourceRootDirs;
        }

        public void setSourceRootDirs(String sourceRootDirs) {
            this.sourceRootDirs = sourceRootDirs;
        }

        public String getOutputDir() {
            return outputDir;
        }

        public void setOutputDir(String outputDir) {
            this.outputDir = outputDir;
        }

        public String getClasspath() {
            return classpath;
        }

        public void setClasspath(String classpath) {
            this.classpath = classpath;
        }

        public String getJavaComplianceLevel() {
            return javaComplianceLevel;
        }

        public void setJavaComplianceLevel(String javaComplianceLevel) {
            this.javaComplianceLevel = javaComplianceLevel;
        }
    }

    /**
     * 代碼提取請求模型
     */
    public static class CodeExtractionRequest {
        private String entryPointMethodFqn; // 進入點方法的完整限定名
        private String astDir; // AST 目錄路徑
        private String basePackage; // 基礎包名，用於過濾
        private int maxDepth; // 最大追蹤深度
        private boolean includeImports; // 是否包含 import 語句
        private boolean includeComments; // 是否包含註解
        private boolean extractOnlyUsedMethods; // 是否只提取實際使用的方法（但包含所有屬性）

        // Getters and Setters
        public String getEntryPointMethodFqn() {
            return entryPointMethodFqn;
        }

        public void setEntryPointMethodFqn(String entryPointMethodFqn) {
            this.entryPointMethodFqn = entryPointMethodFqn;
        }

        public String getAstDir() {
            return astDir;
        }

        public void setAstDir(String astDir) {
            this.astDir = astDir;
        }

        public String getBasePackage() {
            return basePackage;
        }

        public void setBasePackage(String basePackage) {
            this.basePackage = basePackage;
        }

        public int getMaxDepth() {
            return maxDepth;
        }

        public void setMaxDepth(int maxDepth) {
            this.maxDepth = maxDepth;
        }

        public boolean isIncludeImports() {
            return includeImports;
        }

        public void setIncludeImports(boolean includeImports) {
            this.includeImports = includeImports;
        }

        public boolean isIncludeComments() {
            return includeComments;
        }

        public void setIncludeComments(boolean includeComments) {
            this.includeComments = includeComments;
        }

        public boolean isExtractOnlyUsedMethods() {
            return extractOnlyUsedMethods;
        }

        public void setExtractOnlyUsedMethods(boolean extractOnlyUsedMethods) {
            this.extractOnlyUsedMethods = extractOnlyUsedMethods;
        }
    }
}
