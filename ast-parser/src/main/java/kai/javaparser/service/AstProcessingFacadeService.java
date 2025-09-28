package kai.javaparser.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import kai.javaparser.diagram.SequenceOutputConfig;
import kai.javaparser.diagram.DiagramService;
import kai.javaparser.model.ProcessRequest;

/**
 * AST處理門面服務
 * 作為Controller和後端各模組之間的高層門面(Facade)
 * 統一處理AST解析、序列圖生成和代碼提取
 */
@Service
public class AstProcessingFacadeService {

    private static final Logger logger = LoggerFactory.getLogger(AstProcessingFacadeService.class);

    @Autowired
    private AstParserService astParserService;

    @Autowired
    private CodeExtractorService codeExtractorService;

    @Autowired
    private DiagramService diagramService;

    /**
     * 統一處理方法
     * 
     * @param request 處理請求
     * @return 處理結果字串
     */
    public String process(ProcessRequest request) {
        logger.info("開始處理AST請求: {}", request);

        try {
            // 步驟1: 解析 (Parse) - 呼叫astParserService執行AST解析
            String astOutputDir = parseProject(request);

            // 步驟2: 處理 (Process) - 根據request.outputType決定下一步
            String result = processByOutputType(request, astOutputDir);

            logger.info("AST處理完成，結果長度: {} 字元", result.length());
            return result;

        } catch (Exception e) {
            logger.error("AST處理失敗", e);
            throw new RuntimeException("AST處理失敗: " + e.getMessage(), e);
        }
    }

    /**
     * 解析專案，生成AST
     */
    private String parseProject(ProcessRequest request) throws IOException {
        logger.info("開始解析專案: {}", request.getProjectPath());

        // 創建臨時輸出目錄
        Path tempOutputDir = createTempOutputDir(request.getProjectPath());
        String tempOutputDirStr = tempOutputDir.toString();

        // 使用AstParserService進行解析
        // 這裡需要根據專案類型自動檢測源碼目錄和classpath
        String result = astParserService.parseSourceDirectory(
                request.getProjectPath(),
                tempOutputDirStr);

        logger.info("專案解析完成: {}", result);
        return tempOutputDirStr;
    }

    /**
     * 根據輸出類型進行處理
     */
    private String processByOutputType(ProcessRequest request, String astOutputDir) {
        switch (request.getOutputType()) {
            case MERMAID:
                return generateMermaidDiagram(request, astOutputDir);
            case EXTRACTED_CODE:
                return extractCode(request, astOutputDir);
            default:
                throw new IllegalArgumentException("不支援的輸出類型: " + request.getOutputType());
        }
    }

    /**
     * 生成Mermaid序列圖
     */
    private String generateMermaidDiagram(ProcessRequest request, String astOutputDir) {
        logger.info("開始生成Mermaid序列圖");

        // 從params中提取配置參數
        Map<String, Object> params = request.getParams();
        Set<String> basePackages = getStringSetParam(params, "basePackages", new HashSet<>());
        // 向後相容：如果沒有basePackages，嘗試使用basePackage
        if (basePackages.isEmpty()) {
            String basePackage = getStringParam(params, "basePackage", "");
            if (!basePackage.isEmpty()) {
                basePackages.add(basePackage);
            }
        }
        int depth = getIntParam(params, "depth", 5);

        // 創建SequenceOutputConfig
        SequenceOutputConfig config = SequenceOutputConfig.builder()
                .basePackages(basePackages)
                .depth(depth)
                .build();

        // 使用注入的DiagramService生成
        String mermaidResult = diagramService.generateDiagram(request.getEntryPointMethodFqn(), config);
        logger.info("Mermaid序列圖生成完成");
        return mermaidResult;
    }

    /**
     * 提取代碼
     */
    private String extractCode(ProcessRequest request, String astOutputDir) {
        logger.info("開始提取代碼");

        // 從params中提取配置參數
        Map<String, Object> params = request.getParams();
        Set<String> basePackages = getStringSetParam(params, "basePackages", new HashSet<>());
        // 向後相容：如果沒有basePackages，嘗試使用basePackage
        if (basePackages.isEmpty()) {
            String basePackage = getStringParam(params, "basePackage", "");
            if (!basePackage.isEmpty()) {
                basePackages.add(basePackage);
            }
        }
        int maxDepth = getIntParam(params, "maxDepth", 5);
        boolean includeImports = getBooleanParam(params, "includeImports", true);
        boolean includeComments = getBooleanParam(params, "includeComments", true);
        boolean extractOnlyUsedMethods = getBooleanParam(params, "extractOnlyUsedMethods", false);
        boolean includeConstructors = getBooleanParam(params, "includeConstructors", false);

        // 創建CodeExtractionRequest
        CodeExtractorService.CodeExtractionRequest extractRequest = CodeExtractorService.CodeExtractionRequest.builder()
                .entryPointMethodFqn(request.getEntryPointMethodFqn())
                .astDir(astOutputDir)
                .basePackages(basePackages)
                .maxDepth(maxDepth)
                .includeImports(includeImports)
                .includeComments(includeComments)
                .extractOnlyUsedMethods(extractOnlyUsedMethods)
                .includeConstructors(includeConstructors)
                .build();

        // 執行代碼提取
        CodeExtractorService.CodeExtractionResult result = codeExtractorService.extractCode(extractRequest);

        if (result.getErrorMessage() != null) {
            throw new RuntimeException("代碼提取失敗: " + result.getErrorMessage());
        }

        logger.info("代碼提取完成，涉及類別數: {}, 總行數: {}",
                result.getTotalClasses(), result.getTotalLines());
        return result.getMergedSourceCode();
    }

    /**
     * 創建臨時輸出目錄
     */
    private Path createTempOutputDir(String projectPath) throws IOException {
        Path projectPathObj = Paths.get(projectPath);
        String projectName = projectPathObj.getFileName().toString();
        Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "ast-parser", projectName);

        Files.createDirectories(tempDir);
        logger.info("創建臨時輸出目錄: {}", tempDir);
        return tempDir;
    }

    /**
     * 從參數Map中獲取字串參數
     */
    private String getStringParam(Map<String, Object> params, String key, String defaultValue) {
        if (params == null) {
            return defaultValue;
        }
        Object value = params.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    /**
     * 從參數Map中獲取整數參數
     */
    private int getIntParam(Map<String, Object> params, String key, int defaultValue) {
        if (params == null) {
            return defaultValue;
        }
        Object value = params.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                logger.warn("無法解析整數參數 {}: {}, 使用預設值: {}", key, value, defaultValue);
            }
        }
        return defaultValue;
    }

    /**
     * 從參數Map中獲取布林參數
     */
    private boolean getBooleanParam(Map<String, Object> params, String key, boolean defaultValue) {
        if (params == null) {
            return defaultValue;
        }
        Object value = params.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return defaultValue;
    }

    /**
     * 從參數Map中獲取字串集合參數
     * 支援單一字串（逗號分隔）或字串陣列
     */
    private Set<String> getStringSetParam(Map<String, Object> params, String key, Set<String> defaultValue) {
        if (params == null) {
            return defaultValue;
        }
        Object value = params.get(key);
        if (value == null) {
            return defaultValue;
        }

        Set<String> result = new HashSet<>();
        if (value instanceof String) {
            String strValue = (String) value;
            if (!strValue.trim().isEmpty()) {
                // 支援逗號分隔的字串
                String[] parts = strValue.split(",");
                for (String part : parts) {
                    String trimmed = part.trim();
                    if (!trimmed.isEmpty()) {
                        result.add(trimmed);
                    }
                }
            }
        } else if (value instanceof String[]) {
            // 支援字串陣列
            String[] arrayValue = (String[]) value;
            for (String item : arrayValue) {
                if (item != null && !item.trim().isEmpty()) {
                    result.add(item.trim());
                }
            }
        } else if (value instanceof java.util.Collection) {
            // 支援其他集合類型
            @SuppressWarnings("unchecked")
            java.util.Collection<Object> collection = (java.util.Collection<Object>) value;
            for (Object item : collection) {
                if (item != null) {
                    String strItem = item.toString().trim();
                    if (!strItem.isEmpty()) {
                        result.add(strItem);
                    }
                }
            }
        }

        return result.isEmpty() ? defaultValue : result;
    }
}
