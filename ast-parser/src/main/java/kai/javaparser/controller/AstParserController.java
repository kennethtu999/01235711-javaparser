package kai.javaparser.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import kai.javaparser.service.AstParserService;

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
}
