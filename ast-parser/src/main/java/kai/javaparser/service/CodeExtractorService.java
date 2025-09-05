package kai.javaparser.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import kai.javaparser.diagram.AstClassUtil;
import kai.javaparser.diagram.SequenceOutputConfig;
import kai.javaparser.diagram.idx.AstIndex;
import kai.javaparser.model.FileAstData;
import kai.javaparser.model.InteractionModel;
import kai.javaparser.model.MethodGroup;
import kai.javaparser.model.TraceResult;
import lombok.Builder;
import lombok.Getter;

/**
 * 代碼提取服務
 * 
 * 實現案例 #3：能經由 Java Method 取出對應的程式碼，供AI Prompt使用
 * 
 * 功能：
 * 1. 從一個進入點開始追蹤呼叫層級
 * 2. 收集所有涉及到的不重複類別
 * 3. 取得這些類別的完整原始碼
 * 4. 將它們整合成一個單一、合併後的文字檔案
 */
@Service
public class CodeExtractorService {

    private static final Logger logger = LoggerFactory.getLogger(CodeExtractorService.class);

    private final SequenceTraceService sequenceTraceService;
    private final AstIndex astIndex;
    private final SourceProvider sourceProvider;
    private final SourceCodeWeaver sourceCodeWeaver;

    @Autowired
    public CodeExtractorService(SequenceTraceService sequenceTraceService, AstIndex astIndex,
            SourceProvider sourceProvider, SourceCodeWeaver sourceCodeWeaver) {
        this.sequenceTraceService = sequenceTraceService;
        this.astIndex = astIndex;
        this.sourceProvider = sourceProvider;
        this.sourceCodeWeaver = sourceCodeWeaver;
    }

    /**
     * 代碼提取請求
     */
    @Builder
    @Getter
    public static class CodeExtractionRequest {
        private String entryPointMethodFqn; // 進入點方法的完整限定名
        private String astDir; // AST 目錄路徑
        private String basePackage; // 基礎包名，用於過濾
        private int maxDepth; // 最大追蹤深度
        private boolean includeImports; // 是否包含 import 語句
        private boolean includeComments; // 是否包含註解
        private boolean extractOnlyUsedMethods; // 是否只提取實際使用的方法（但包含所有屬性）
    }

    /**
     * 代碼提取結果
     */
    @Builder
    @Getter
    public static class CodeExtractionResult {
        private String entryPointMethodFqn;
        private Set<String> involvedClasses; // 涉及的所有類別 FQN
        private String mergedSourceCode; // 合併後的原始碼
        private int totalClasses; // 總類別數
        private int totalLines; // 總行數
        private String errorMessage; // 錯誤訊息（如果有）
    }

    /**
     * 提取代碼
     * 
     * @param request 提取請求
     * @return 提取結果
     */
    public CodeExtractionResult extractCode(CodeExtractionRequest request) {
        logger.info("開始代碼提取，進入點: {}", request.getEntryPointMethodFqn());

        try {
            // 1. 追蹤依賴關係，識別所有涉及的類別
            Set<String> involvedClasses = traceDependencies(request);
            logger.info("識別到 {} 個相關類別: {}", involvedClasses.size(), involvedClasses);

            // 2. 使用新的抽象層提取原始碼
            List<ClassSourceCode> classSources = extractClassSourcesWithNewAbstractions(involvedClasses, request);

            // 3. 合併代碼
            String mergedCode = mergeSourceCode(classSources, request);

            // 4. 計算統計資訊
            int totalLines = mergedCode.split("\n").length;

            return CodeExtractionResult.builder()
                    .entryPointMethodFqn(request.getEntryPointMethodFqn())
                    .involvedClasses(involvedClasses)
                    .mergedSourceCode(mergedCode)
                    .totalClasses(involvedClasses.size())
                    .totalLines(totalLines)
                    .build();

        } catch (Exception e) {
            logger.error("代碼提取失敗", e);
            return CodeExtractionResult.builder()
                    .entryPointMethodFqn(request.getEntryPointMethodFqn())
                    .involvedClasses(new HashSet<>())
                    .mergedSourceCode("")
                    .totalClasses(0)
                    .totalLines(0)
                    .errorMessage("代碼提取失敗: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 追蹤依賴關係，識別所有涉及的類別
     */
    private Set<String> traceDependencies(CodeExtractionRequest request) {
        Set<String> involvedClasses = new HashSet<>();

        // 使用 SequenceTraceService 來追蹤方法呼叫
        SequenceOutputConfig config = SequenceOutputConfig.builder()
                .basePackage(request.getBasePackage())
                .depth(request.getMaxDepth())
                .build();

        // 執行追蹤
        TraceResult traceResult = sequenceTraceService.trace(request.getEntryPointMethodFqn(), config);

        // 從追蹤結果中提取所有涉及的類別
        extractClassesFromTraceResult(traceResult, involvedClasses);

        // 確保進入點方法的類別也被包含
        String entryPointClass = AstClassUtil.getClassFqnFromMethodFqn(request.getEntryPointMethodFqn());
        if (!entryPointClass.isEmpty()) {
            involvedClasses.add(entryPointClass);
        }

        return involvedClasses;
    }

    /**
     * 使用新的抽象層提取類別原始碼
     */
    private List<ClassSourceCode> extractClassSourcesWithNewAbstractions(Set<String> classFqns,
            CodeExtractionRequest request) {
        logger.info("使用新的抽象層提取原始碼，提供者: {}, 編織器: {}",
                sourceProvider.getProviderName(), sourceCodeWeaver.getWeaverName());

        List<ClassSourceCode> classSources = new ArrayList<>();

        // 1. 使用SourceProvider獲取原始碼
        Map<String, String> sourceCodes = sourceProvider.getSourceCodes(classFqns);

        for (String classFqn : classFqns) {
            String sourceCode = sourceCodes.get(classFqn);
            if (sourceCode == null) {
                logger.warn("無法獲取類別原始碼: {}", classFqn);
                continue;
            }

            // 2. 創建編織規則
            SourceCodeWeaver.WeavingRules rules = createWeavingRules(classFqn, request);

            // 3. 使用SourceCodeWeaver編織原始碼
            SourceCodeWeaver.WeavingResult weavingResult = sourceCodeWeaver.weave(sourceCode, rules);

            if (!weavingResult.isSuccess()) {
                logger.warn("原始碼編織失敗: {} - {}", classFqn, weavingResult.getErrorMessage());
                // 如果編織失敗，保持原始碼不變
            } else {
                sourceCode = weavingResult.getWovenSourceCode();
            }

            // 4. 創建ClassSourceCode
            String relativePath = classFqn.replace('.', '/') + ".java";
            classSources.add(ClassSourceCode.builder()
                    .classFqn(classFqn)
                    .relativePath(relativePath)
                    .sourceCode(sourceCode)
                    .build());

            logger.debug("提取類別原始碼: {} -> {} 行", classFqn, sourceCode.split("\n").length);
        }

        return classSources;
    }

    /**
     * 創建編織規則
     */
    private SourceCodeWeaver.WeavingRules createWeavingRules(String classFqn, CodeExtractionRequest request) {
        // 收集使用的方法名稱
        Set<String> usedMethodNames = collectUsedMethodNames(classFqn, request);

        return new SourceCodeWeaver.WeavingRules() {
            @Override
            public boolean includeImports() {
                return request.isIncludeImports();
            }

            @Override
            public boolean includeComments() {
                return request.isIncludeComments();
            }

            @Override
            public boolean extractOnlyUsedMethods() {
                return request.isExtractOnlyUsedMethods();
            }

            @Override
            public Set<String> getUsedMethodNames() {
                return usedMethodNames;
            }

            @Override
            public String getClassFqn() {
                return classFqn;
            }
        };
    }

    /**
     * 收集使用的方法名稱
     */
    private Set<String> collectUsedMethodNames(String classFqn, CodeExtractionRequest request) {
        Set<String> usedMethodNames = new HashSet<>();

        // 從AST資料中獲取方法資訊
        FileAstData astData = astIndex.getAstDataByClassFqn(classFqn);
        if (astData != null && astData.getSequenceDiagramData() != null &&
                astData.getSequenceDiagramData().getMethodGroups() != null) {

            for (MethodGroup methodGroup : astData.getSequenceDiagramData().getMethodGroups()) {
                usedMethodNames.add(methodGroup.getMethodName());
            }
        }

        return usedMethodNames;
    }

    /**
     * 從追蹤結果中遞迴提取所有涉及的類別
     */
    private void extractClassesFromTraceResult(TraceResult traceResult, Set<String> involvedClasses) {
        if (traceResult == null || traceResult.getSequenceNodes() == null) {
            return;
        }

        for (var node : traceResult.getSequenceNodes()) {
            if (node instanceof InteractionModel) {
                InteractionModel interaction = (InteractionModel) node;

                // 添加被呼叫者的類別
                if (interaction.getCallee() != null) {
                    involvedClasses.add(interaction.getCallee());
                }

                // 遞迴處理內部呼叫
                if (interaction.getInternalCalls() != null) {
                    for (var internalNode : interaction.getInternalCalls()) {
                        if (internalNode instanceof InteractionModel) {
                            extractClassesFromTraceResult(
                                    new TraceResult("", List.of(internalNode)),
                                    involvedClasses);
                        }
                    }
                }

                // 處理鏈式呼叫
                if (interaction.getNextChainedCall() != null) {
                    extractClassesFromTraceResult(
                            new TraceResult("", List.of(interaction.getNextChainedCall())),
                            involvedClasses);
                }
            }
        }
    }

    /**
     * 合併原始碼
     */
    private String mergeSourceCode(List<ClassSourceCode> classSources, CodeExtractionRequest request) {
        StringBuilder mergedCode = new StringBuilder();

        // 添加標題
        mergedCode.append("// ============================================\n");
        mergedCode.append("// 代碼提取結果 - 進入點: ").append(request.getEntryPointMethodFqn()).append("\n");
        mergedCode.append("// 提取時間: ").append(java.time.LocalDateTime.now()).append("\n");
        mergedCode.append("// 總類別數: ").append(classSources.size()).append("\n");
        mergedCode.append("// ============================================\n\n");

        // 按類別名稱排序
        classSources.sort((a, b) -> a.getClassFqn().compareTo(b.getClassFqn()));

        // 合併每個類別的原始碼
        for (ClassSourceCode classSource : classSources) {
            mergedCode.append("--- START OF FILE [").append(classSource.getRelativePath()).append("] ---\n");
            mergedCode.append("// Class: ").append(classSource.getClassFqn()).append("\n");
            mergedCode.append(classSource.getSourceCode());
            mergedCode.append("\n--- END OF FILE [").append(classSource.getRelativePath()).append("] ---\n\n");
        }

        return mergedCode.toString();
    }

    /**
     * 類別原始碼資料結構
     */
    @Builder
    @Getter
    private static class ClassSourceCode {
        private String classFqn;
        private String relativePath;
        private String sourceCode;
    }
}
