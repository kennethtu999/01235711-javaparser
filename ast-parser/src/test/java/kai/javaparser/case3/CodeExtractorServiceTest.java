package kai.javaparser.case3;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;

import kai.javaparser.service.CodeExtractorService;
import kai.javaparser.service.CodeExtractorService.CodeExtractionRequest;
import kai.javaparser.service.CodeExtractorService.CodeExtractionResult;

/**
 * 案例 #3 測試：能經由 Java Method 取出對應的程式碼，供AI Prompt使用
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class CodeExtractorServiceTest {

    private static final Logger logger = LoggerFactory.getLogger(CodeExtractorServiceTest.class);

    private CodeExtractorService codeExtractorService;
    private Path astDir;
    private Path outputDir;

    @BeforeEach
    void setUp() throws IOException {
        codeExtractorService = new CodeExtractorService();

        // 使用現有的測試 AST 目錄
        astDir = Paths.get("build/parsed-ast");
        outputDir = Paths.get("build/case3-output");

        // 確保輸出目錄存在
        Files.createDirectories(outputDir);

        logger.info("測試設置完成，AST 目錄: {}", astDir.toAbsolutePath());
        logger.info("輸出目錄: {}", outputDir.toAbsolutePath());
    }

    @AfterEach
    void tearDown() {
        // 清理測試產生的檔案
    }

    /**
     * 測試代碼提取功能
     * 使用現有的測試專案中的方法作為進入點
     */
    @Test
    void testExtractCode() {
        // Arrange: 準備測試資料
        String entryPointMethodFqn = "com.example.case2.LoginUser.getLevel1()";
        String basePackage = "com.example";
        int maxDepth = 4;

        CodeExtractionRequest request = CodeExtractionRequest.builder()
                .entryPointMethodFqn(entryPointMethodFqn)
                .astDir(astDir.toAbsolutePath().toString())
                .basePackage(basePackage)
                .maxDepth(maxDepth)
                .includeImports(true)
                .includeComments(true)
                .build();

        // Act: 執行代碼提取
        CodeExtractionResult result = codeExtractorService.extractCode(request);

        // Assert: 驗證結果
        assertNotNull(result, "提取結果不應為 null");
        assertNotNull(result.getEntryPointMethodFqn(), "進入點方法 FQN 不應為 null");
        assertEquals(entryPointMethodFqn, result.getEntryPointMethodFqn(), "進入點方法 FQN 應匹配");

        assertNotNull(result.getInvolvedClasses(), "涉及的類別集合不應為 null");
        assertTrue(result.getTotalClasses() > 0, "應該識別到至少一個類別");

        assertNotNull(result.getMergedSourceCode(), "合併後的原始碼不應為 null");
        assertFalse(result.getMergedSourceCode().isEmpty(), "合併後的原始碼不應為空");
        assertTrue(result.getTotalLines() > 0, "總行數應大於 0");

        // 驗證合併後的原始碼包含預期的格式
        String mergedCode = result.getMergedSourceCode();
        assertTrue(mergedCode.contains("--- START OF FILE"), "應包含檔案開始標記");
        assertTrue(mergedCode.contains("--- END OF FILE"), "應包含檔案結束標記");
        assertTrue(mergedCode.contains("代碼提取結果"), "應包含標題");
        assertTrue(mergedCode.contains(entryPointMethodFqn), "應包含進入點方法");

        // 輸出結果供檢查
        logger.info("=== 代碼提取結果 ===");
        logger.info("進入點方法: {}", result.getEntryPointMethodFqn());
        logger.info("涉及類別數: {}", result.getTotalClasses());
        logger.info("總行數: {}", result.getTotalLines());
        logger.info("涉及的類別: {}", result.getInvolvedClasses());

        // 將結果寫入檔案供檢查
        try {
            Path outputFile = outputDir.resolve("extracted-code.txt");
            Files.writeString(outputFile, result.getMergedSourceCode());
            logger.info("提取結果已寫入: {}", outputFile.toAbsolutePath());
        } catch (IOException e) {
            logger.warn("無法寫入輸出檔案", e);
        }

        // 輸出部分合併後的原始碼
        String[] lines = mergedCode.split("\n");
        int previewLines = Math.min(50, lines.length);
        logger.info("=== 合併後原始碼預覽 (前 {} 行) ===", previewLines);
        for (int i = 0; i < previewLines; i++) {
            logger.info("{}: {}", i + 1, lines[i]);
        }
        if (lines.length > previewLines) {
            logger.info("... (還有 {} 行)", lines.length - previewLines);
        }
    }

    /**
     * 測試只提取使用的方法和所有屬性
     */
    @Test
    void testExtractOnlyUsedMethodsAndAllFields() {
        // Arrange: 準備測試資料
        String entryPointMethodFqn = "com.example.case2.LoginUser.getLevel1(com.example.case2.Company)";
        String basePackage = "com.example";
        int maxDepth = 10;

        CodeExtractionRequest request = CodeExtractionRequest.builder()
                .entryPointMethodFqn(entryPointMethodFqn)
                .astDir(astDir.toAbsolutePath().toString())
                .basePackage(basePackage)
                .maxDepth(maxDepth)
                .includeImports(true)
                .includeComments(true)
                .extractOnlyUsedMethods(true) // 啟用只提取使用的方法
                .build();

        logger.info("測試請求設置: extractOnlyUsedMethods = {}", request.isExtractOnlyUsedMethods());

        // Act: 執行代碼提取
        CodeExtractionResult result = codeExtractorService.extractCode(request);

        // Assert: 驗證結果
        assertNotNull(result, "提取結果不應為 null");
        assertNotNull(result.getEntryPointMethodFqn(), "進入點方法 FQN 不應為 null");
        assertEquals(entryPointMethodFqn, result.getEntryPointMethodFqn(), "進入點方法 FQN 應匹配");

        assertNotNull(result.getInvolvedClasses(), "涉及的類別集合不應為 null");
        assertTrue(result.getTotalClasses() > 0, "應該識別到至少一個類別");

        assertNotNull(result.getMergedSourceCode(), "合併後的原始碼不應為 null");
        assertFalse(result.getMergedSourceCode().isEmpty(), "合併後的原始碼不應為空");
        assertTrue(result.getTotalLines() > 0, "總行數應大於 0");

        // 驗證合併後的原始碼包含預期的格式
        String mergedCode = result.getMergedSourceCode();
        assertTrue(mergedCode.contains("--- START OF FILE"), "應包含檔案開始標記");
        assertTrue(mergedCode.contains("--- END OF FILE"), "應包含檔案結束標記");
        assertTrue(mergedCode.contains("代碼提取結果"), "應包含標題");
        assertTrue(mergedCode.contains(entryPointMethodFqn), "應包含進入點方法");

        // 輸出結果供檢查
        logger.info("=== 只提取使用的方法和所有屬性 - 結果 ===");
        logger.info("進入點方法: {}", result.getEntryPointMethodFqn());
        logger.info("涉及類別數: {}", result.getTotalClasses());
        logger.info("總行數: {}", result.getTotalLines());
        logger.info("涉及的類別: {}", result.getInvolvedClasses());

        // 將結果寫入檔案供檢查
        try {
            Path usedMethodsOutputDir = Paths.get("build/case3-used-methods-output");
            Files.createDirectories(usedMethodsOutputDir);
            Path outputFile = usedMethodsOutputDir.resolve("extracted-used-methods.txt");
            Files.writeString(outputFile, result.getMergedSourceCode());
            logger.info("提取結果已寫入: {}", outputFile.toAbsolutePath());
        } catch (IOException e) {
            logger.warn("無法寫入輸出檔案", e);
        }
    }

    /**
     * 測試比較：完整提取 vs 只提取使用的方法
     */
    @Test
    void testCompareFullVsUsedMethodsExtraction() {
        String entryPointMethodFqn = "com.example.case2.LoginUser.getLevel1(com.example.case2.Company)";
        String basePackage = "com.example";
        int maxDepth = 2;

        // 1. 完整提取
        CodeExtractionRequest fullRequest = CodeExtractionRequest.builder()
                .entryPointMethodFqn(entryPointMethodFqn)
                .astDir(astDir.toAbsolutePath().toString())
                .basePackage(basePackage)
                .maxDepth(maxDepth)
                .includeImports(true)
                .includeComments(true)
                .extractOnlyUsedMethods(false) // 完整提取
                .build();

        CodeExtractionResult fullResult = codeExtractorService.extractCode(fullRequest);

        // 2. 只提取使用的方法
        CodeExtractionRequest usedRequest = CodeExtractionRequest.builder()
                .entryPointMethodFqn(entryPointMethodFqn)
                .astDir(astDir.toAbsolutePath().toString())
                .basePackage(basePackage)
                .maxDepth(maxDepth)
                .includeImports(true)
                .includeComments(true)
                .extractOnlyUsedMethods(true) // 只提取使用的方法
                .build();

        CodeExtractionResult usedResult = codeExtractorService.extractCode(usedRequest);

        // 比較結果
        logger.info("=== 比較結果 ===");
        logger.info("完整提取行數: {}", fullResult.getTotalLines());
        logger.info("只提取使用的方法行數: {}", usedResult.getTotalLines());
        logger.info("減少行數: {}", fullResult.getTotalLines() - usedResult.getTotalLines());
        logger.info("減少比例: {:.1f}%",
                (double) (fullResult.getTotalLines() - usedResult.getTotalLines()) / fullResult.getTotalLines() * 100);

        // 驗證只提取使用的方法應該比完整提取的行數少
        assertTrue(usedResult.getTotalLines() < fullResult.getTotalLines(),
                "只提取使用的方法應該比完整提取的行數少");

        // 將兩個結果都寫入檔案供比較
        try {
            Path usedMethodsOutputDir = Paths.get("build/case3-used-methods-output");
            Files.createDirectories(usedMethodsOutputDir);
            Files.writeString(usedMethodsOutputDir.resolve("full-extraction.txt"), fullResult.getMergedSourceCode());
            Files.writeString(usedMethodsOutputDir.resolve("used-methods-only.txt"), usedResult.getMergedSourceCode());
            logger.info("比較結果已寫入檔案");
        } catch (IOException e) {
            logger.warn("無法寫入比較檔案", e);
        }
    }

    /**
     * 測試錯誤處理
     */
    @Test
    void testExtractCodeWithInvalidInput() {
        // Arrange: 準備無效的請求
        CodeExtractionRequest request = CodeExtractionRequest.builder()
                .entryPointMethodFqn("invalid.method.name()")
                .astDir("/nonexistent/path")
                .basePackage("com.example")
                .maxDepth(1)
                .includeImports(true)
                .includeComments(true)
                .build();

        // Act: 執行代碼提取
        CodeExtractionResult result = codeExtractorService.extractCode(request);

        // Assert: 驗證錯誤處理
        assertNotNull(result, "提取結果不應為 null");
        assertNotNull(result.getErrorMessage(), "應包含錯誤訊息");
        assertTrue(result.getErrorMessage().contains("代碼提取失敗"), "錯誤訊息應包含失敗描述");
        assertEquals(0, result.getTotalClasses(), "無效輸入應返回 0 個類別");
        assertEquals(0, result.getTotalLines(), "無效輸入應返回 0 行");
    }
}
