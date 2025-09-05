package kai.javaparser.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import kai.javaparser.model.FieldInfo;
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
     * 提取每個類別的原始碼
     */
    private List<ClassSourceCode> extractClassSources(Set<String> classFqns, AstIndex astIndex,
            CodeExtractionRequest request) {
        List<ClassSourceCode> classSources = new ArrayList<>();

        for (String classFqn : classFqns) {
            FileAstData astData = astIndex.getAstDataByClassFqn(classFqn);
            if (astData != null) {
                String sourceCode = null;
                String relativePath = astData.getRelativePath() != null ? astData.getRelativePath()
                        : classFqn.replace('.', '/') + ".java";

                // 嘗試從 fileContent 獲取原始碼
                if (astData.getFileContent() != null) {
                    sourceCode = new String(astData.getFileContent());
                } else {
                    // 如果 fileContent 為 null，嘗試從絕對路徑讀取檔案
                    if (astData.getAbsolutePath() != null) {
                        try {
                            Path sourceFilePath = Path.of(astData.getAbsolutePath());
                            if (Files.exists(sourceFilePath)) {
                                sourceCode = Files.readString(sourceFilePath);
                                logger.debug("從檔案系統讀取原始碼: {}", sourceFilePath);
                            }
                        } catch (IOException e) {
                            logger.warn("無法讀取原始碼檔案: {}", astData.getAbsolutePath(), e);
                        }
                    }
                }

                if (sourceCode != null && !sourceCode.isEmpty()) {
                    classSources.add(ClassSourceCode.builder()
                            .classFqn(classFqn)
                            .relativePath(relativePath)
                            .sourceCode(sourceCode)
                            .build());

                    logger.debug("提取類別原始碼: {} -> {} 行", classFqn, sourceCode.split("\n").length);
                } else {
                    logger.warn("無法找到類別的原始碼: {}", classFqn);
                }
            } else {
                logger.warn("無法找到類別的 AST 資料: {}", classFqn);
            }
        }

        return classSources;
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
     * 提取使用的方法和所有屬性
     */
    private List<ClassSourceCode> extractUsedMethodsAndAllFields(Set<String> classFqns, AstIndex astIndex,
            CodeExtractionRequest request) {
        List<ClassSourceCode> classSources = new ArrayList<>();

        for (String classFqn : classFqns) {
            FileAstData astData = astIndex.getAstDataByClassFqn(classFqn);
            if (astData != null) {
                String sourceCode = null;
                String relativePath = astData.getRelativePath() != null ? astData.getRelativePath()
                        : classFqn.replace('.', '/') + ".java";

                // 嘗試從 fileContent 獲取原始碼
                if (astData.getFileContent() != null) {
                    sourceCode = new String(astData.getFileContent());
                } else {
                    // 如果 fileContent 為 null，嘗試從絕對路徑讀取檔案
                    if (astData.getAbsolutePath() != null) {
                        try {
                            Path sourceFilePath = Path.of(astData.getAbsolutePath());
                            if (Files.exists(sourceFilePath)) {
                                sourceCode = Files.readString(sourceFilePath);
                                logger.debug("從檔案系統讀取原始碼: {}", sourceFilePath);
                            }
                        } catch (IOException e) {
                            logger.warn("無法讀取原始碼檔案: {}", astData.getAbsolutePath(), e);
                        }
                    }
                }

                if (sourceCode != null && !sourceCode.isEmpty()) {
                    // 使用行號精確提取使用的方法和所有屬性
                    String filteredSourceCode = extractUsedMethodsAndAllFieldsByLineNumbers(
                            sourceCode, astData, request);

                    if (filteredSourceCode != null && !filteredSourceCode.isEmpty()) {
                        classSources.add(ClassSourceCode.builder()
                                .classFqn(classFqn)
                                .relativePath(relativePath)
                                .sourceCode(filteredSourceCode)
                                .build());

                        logger.info("提取類別原始碼 (使用的方法和所有屬性): {} -> {} 行",
                                classFqn, filteredSourceCode.split("\n").length);
                    } else {
                        logger.warn("過濾後的原始碼為空: {}", classFqn);
                    }
                } else {
                    logger.warn("無法找到類別的原始碼: {}", classFqn);
                }
            } else {
                logger.warn("無法找到類別的 AST 資料: {}", classFqn);
            }
        }

        return classSources;
    }

    /**
     * 使用行號精確提取使用的方法和所有屬性
     */
    private String extractUsedMethodsAndAllFieldsByLineNumbers(String sourceCode, FileAstData astData,
            CodeExtractionRequest request) {
        try {
            StringBuilder result = new StringBuilder();
            String[] lines = sourceCode.split("\n");

            // 獲取所有屬性的行號
            Set<Integer> fieldLines = new HashSet<>();
            if (astData.getFields() != null) {
                for (FieldInfo field : astData.getFields()) {
                    for (int i = field.getStartLineNumber() - 1; i < field.getEndLineNumber(); i++) {
                        if (i >= 0 && i < lines.length) {
                            fieldLines.add(i);
                        }
                    }
                }
            }

            // 獲取使用的方法的行號
            Set<Integer> usedMethodLines = new HashSet<>();
            if (astData.getSequenceDiagramData() != null &&
                    astData.getSequenceDiagramData().getMethodGroups() != null) {

                // 收集所有被使用的方法名稱
                Set<String> usedMethodNames = collectUsedMethodNames(astData, request);

                for (MethodGroup methodGroup : astData.getSequenceDiagramData().getMethodGroups()) {
                    if (usedMethodNames.contains(methodGroup.getMethodName())) {
                        for (int i = methodGroup.getStartLineNumber() - 1; i < methodGroup.getEndLineNumber(); i++) {
                            if (i >= 0 && i < lines.length) {
                                usedMethodLines.add(i);
                            }
                        }
                    }
                }
            }

            // 計算需要保留的所有行號
            Set<Integer> linesToKeep = new HashSet<>();

            // 1. 添加所有屬性行
            linesToKeep.addAll(fieldLines);

            // 2. 添加所有使用的方法行
            linesToKeep.addAll(usedMethodLines);

            // 3. 為屬性和方法添加相關註解
            addRelevantComments(linesToKeep, fieldLines, usedMethodLines, lines);

            // 4. 添加必要的結構行（類別結尾等）
            addStructuralLines(linesToKeep, lines);

            // 構建結果
            boolean inClass = false;
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                String trimmedLine = line.trim();

                // 檢測類別開始
                if (trimmedLine.startsWith("public class ") || trimmedLine.startsWith("class ")) {
                    inClass = true;
                    result.append(line).append("\n");
                    continue;
                }

                if (!inClass) {
                    // 在類別定義之前，保留 package 和 import
                    if (trimmedLine.startsWith("package ") || trimmedLine.startsWith("import ")) {
                        result.append(line).append("\n");
                    }
                    continue;
                }

                // 只保留計算出需要的行
                if (linesToKeep.contains(i)) {
                    result.append(line).append("\n");
                }
            }

            return result.toString();

        } catch (Exception e) {
            logger.warn("使用行號提取原始碼失敗: {}",
                    astData.getSequenceDiagramData() != null ? astData.getSequenceDiagramData().getClassFqn()
                            : "unknown",
                    e);
            return sourceCode; // 如果解析失敗，返回原始碼
        }
    }

    /**
     * 為屬性和方法添加相關註解和空白行
     */
    private void addRelevantComments(Set<Integer> linesToKeep, Set<Integer> fieldLines,
            Set<Integer> usedMethodLines, String[] lines) {

        // 為每個屬性添加前面的註解和空白行（最多2行）
        for (Integer fieldLine : fieldLines) {
            for (int i = Math.max(0, fieldLine - 2); i < fieldLine; i++) {
                String trimmedLine = lines[i].trim();
                if (isComment(trimmedLine) || trimmedLine.isEmpty()) {
                    linesToKeep.add(i);
                }
            }
        }

        // 為每個方法添加前面的註解和空白行（最多3行）
        for (Integer methodLine : usedMethodLines) {
            for (int i = Math.max(0, methodLine - 3); i < methodLine; i++) {
                String trimmedLine = lines[i].trim();
                if (isComment(trimmedLine) || trimmedLine.isEmpty()) {
                    linesToKeep.add(i);
                }
            }
        }
    }

    /**
     * 添加必要的結構行（如類別結尾大括號）
     */
    private void addStructuralLines(Set<Integer> linesToKeep, String[] lines) {
        // 添加類別結尾的大括號
        for (int i = lines.length - 1; i >= 0; i--) {
            String trimmedLine = lines[i].trim();
            if (trimmedLine.equals("}")) {
                // 檢查前面是否有內容被保留
                boolean hasContentBefore = false;
                for (int j = i - 1; j >= 0; j--) {
                    if (linesToKeep.contains(j)) {
                        hasContentBefore = true;
                        break;
                    }
                }
                if (hasContentBefore) {
                    linesToKeep.add(i);
                    break; // 只添加最後一個大括號
                }
            }
        }
    }

    /**
     * 判斷是否為註解行
     */
    private boolean isComment(String trimmedLine) {
        return trimmedLine.startsWith("//") ||
                trimmedLine.startsWith("/*") ||
                trimmedLine.startsWith("*") ||
                trimmedLine.contains("*/");
    }

    /**
     * 收集被使用的方法名稱
     */
    private Set<String> collectUsedMethodNames(FileAstData astData, CodeExtractionRequest request) {
        Set<String> usedMethodNames = new HashSet<>();

        // 添加進入點方法
        String entryPointClass = AstClassUtil.getClassFqnFromMethodFqn(request.getEntryPointMethodFqn());
        if (astData.getSequenceDiagramData() != null &&
                astData.getSequenceDiagramData().getClassFqn() != null &&
                astData.getSequenceDiagramData().getClassFqn().equals(entryPointClass)) {

            String entryPointMethod = AstClassUtil.getMethodSignature(request.getEntryPointMethodFqn());
            String simpleMethodName = entryPointMethod.split("\\(")[0];
            usedMethodNames.add(simpleMethodName);
            logger.info("添加進入點方法: {}", simpleMethodName);
        }

        // 從追蹤結果中收集被調用的方法
        if (astData.getSequenceDiagramData() != null &&
                astData.getSequenceDiagramData().getMethodGroups() != null) {

            for (MethodGroup methodGroup : astData.getSequenceDiagramData().getMethodGroups()) {
                if (methodGroup.getInteractions() != null) {
                    for (InteractionModel interaction : methodGroup.getInteractions()) {
                        if (interaction.getMethodName() != null) {
                            usedMethodNames.add(interaction.getMethodName());
                            logger.info("添加被調用方法: {}", interaction.getMethodName());
                        }
                    }
                }
            }
        }

        logger.info("收集到的使用的方法: {}", usedMethodNames);
        return usedMethodNames;
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
