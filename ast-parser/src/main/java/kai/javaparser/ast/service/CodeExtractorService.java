package kai.javaparser.ast.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import kai.javaparser.ast.model.FileAstData;
import kai.javaparser.ast.model.InteractionModel;
import kai.javaparser.ast.model.MethodGroup;
import kai.javaparser.ast.model.TraceResult;
import kai.javaparser.diagram.AstClassUtil;
import kai.javaparser.diagram.SequenceOutputConfig;
import kai.javaparser.diagram.idx.AstIndex;
import kai.javaparser.service.SourceCodeWeaver;
import kai.javaparser.service.SourceProvider;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CodeExtractionRequest {
        private String entryPointMethodFqn; // 進入點方法的完整限定名

        private String astDir; // AST 目錄路徑

        private Set<String> basePackages; // 基礎包名列表，用於過濾

        private int maxDepth; // 最大追蹤深度

        private boolean includeImports; // 是否包含 import 語句

        private boolean includeComments; // 是否包含註解

        private boolean extractOnlyUsedMethods; // 是否只提取實際使用的方法（但包含所有屬性）

        private boolean includeConstructors; // 是否包含構造函數
    }

    /**
     * 代碼提取結果
     */
    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
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
            Set<String> involvedMethodFqns = traceDependencies(request);
            logger.info("識別到 {} 個相關方法: {}", involvedMethodFqns.size(), involvedMethodFqns);

            // 2. 使用新的抽象層提取原始碼
            List<ClassSourceCode> classSources = extractClassSourcesWithNewAbstractions(involvedMethodFqns, request);

            // 3. 合併代碼
            String mergedCode = mergeSourceCode(classSources, request);

            // 4. 計算統計資訊
            int totalLines = mergedCode.split("\n").length;

            // 5. 提取涉及的類別FQN
            Set<String> involvedClasses = new HashSet<>();
            for (ClassSourceCode classSource : classSources) {
                involvedClasses.add(classSource.getClassFqn());
            }

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
        Set<String> involvedMethodFqns = new HashSet<>();

        // 檢查進入點方法是否在 basePackages 範圍內
        String entryPointClassFqn = AstClassUtil.getClassFqnFromMethodFqn(request.getEntryPointMethodFqn());
        if (!isClassInBasePackages(entryPointClassFqn, request.getBasePackages())) {
            logger.warn("進入點方法 {} 的類別 {} 不在 basePackages 範圍內",
                    request.getEntryPointMethodFqn(), entryPointClassFqn);
            return involvedMethodFqns; // 返回空的結果
        }

        // 使用 SequenceTraceService 來追蹤方法呼叫
        SequenceOutputConfig config = SequenceOutputConfig.builder()
                .basePackages(request.getBasePackages())
                .depth(request.getMaxDepth())
                .build();

        // 執行追蹤
        TraceResult traceResult = sequenceTraceService.trace(request.getEntryPointMethodFqn(), config);

        // 從追蹤結果中提取所有涉及的類別
        extractClassesFromTraceResult(traceResult, involvedMethodFqns);

        // 確保進入點方法的類別也被包含
        involvedMethodFqns.add(request.getEntryPointMethodFqn());

        return involvedMethodFqns;
    }

    /**
     * 使用新的抽象層提取類別原始碼
     */
    private List<ClassSourceCode> extractClassSourcesWithNewAbstractions(Set<String> methodFqns,
            CodeExtractionRequest request) {
        logger.info("使用新的抽象層提取原始碼，提供者: {}, 編織器: {}",
                sourceProvider.getProviderName(), sourceCodeWeaver.getWeaverName());

        List<ClassSourceCode> classSources = new ArrayList<>();

        // 1. Group method FQNs by class FQN
        Map<String, Set<String>> classToMethodsMap = groupMethodsByClass(methodFqns);
        logger.info("分組結果: {} 個類別", classToMethodsMap.size());

        // 2. Process each class
        for (Map.Entry<String, Set<String>> entry : classToMethodsMap.entrySet()) {
            // 移除泛型資訊
            String classFqn = entry.getKey();
            classFqn = classFqn.replaceAll("<.*>", "");

            Set<String> classMethods = entry.getValue();

            // 檢查類別是否在 basePackages 範圍內
            if (!isClassInBasePackages(classFqn, request.getBasePackages())) {
                logger.debug("跳過不在 basePackages 範圍內的類別: {}", classFqn);
                continue;
            }

            logger.debug("處理類別: {} 包含 {} 個方法", classFqn, classMethods.size());

            // 3. 使用SourceProvider獲取原始碼
            String sourceCode = sourceProvider.getSourceCode(classFqn);

            if (sourceCode == null) {
                logger.warn("無法獲取類別原始碼: {}", classFqn);
                continue;
            }

            // 4. 創建編織規則，傳入該類別的方法集合
            SourceCodeWeaver.WeavingRules rules = createWeavingRules(classFqn, classMethods, request);

            // 5. 使用SourceCodeWeaver編織原始碼
            SourceCodeWeaver.WeavingResult weavingResult = sourceCodeWeaver.weave(sourceCode, rules);

            if (!weavingResult.isSuccess()) {
                logger.warn("原始碼編織失敗: {} - {}", classFqn, weavingResult.getErrorMessage());
                // 如果編織失敗，保持原始碼不變
            } else {
                sourceCode = weavingResult.getWovenSourceCode();
            }

            // 6. 創建ClassSourceCode
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
     * 將方法FQN按類別分組
     */
    private Map<String, Set<String>> groupMethodsByClass(Set<String> methodFqns) {
        Map<String, Set<String>> classToMethodsMap = new HashMap<>();

        for (String methodFqn : methodFqns) {
            String classFqn = AstClassUtil.getClassFqnFromMethodFqn(methodFqn);
            if (classFqn.isEmpty()) {
                logger.warn("無法從方法FQN中提取類別FQN: {}", methodFqn);
                continue;
            }

            classToMethodsMap.computeIfAbsent(classFqn, k -> new HashSet<>()).add(methodFqn);
        }

        return classToMethodsMap;
    }

    /**
     * 創建編織規則
     */
    private SourceCodeWeaver.WeavingRules createWeavingRules(String classFqn, Set<String> classMethods,
            CodeExtractionRequest request) {
        // 收集使用的方法名稱
        Set<String> usedMethodNames = collectUsedMethodNames(classFqn, classMethods, request);

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
            public boolean includeConstructors() {
                return request.isIncludeConstructors();
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
    private Set<String> collectUsedMethodNames(String classFqn, Set<String> classMethods,
            CodeExtractionRequest request) {
        Set<String> usedMethodNames = new HashSet<>();

        if (request.isExtractOnlyUsedMethods()) {
            // 如果只提取使用的方法，則從classMethods中提取方法名稱
            for (String methodFqn : classMethods) {
                String methodSignature = AstClassUtil.getMethodSignature(methodFqn);
                // 提取方法名稱（去掉參數部分）
                String methodName = extractMethodNameFromSignature(methodSignature);
                if (!methodName.isEmpty()) {
                    usedMethodNames.add(methodName);
                }
            }
            logger.debug("類別 {} 使用的方法: {}", classFqn, usedMethodNames);
        } else {
            // 如果提取所有方法，則從AST資料中獲取所有方法資訊
            FileAstData astData = astIndex.getAstDataByClassFqn(classFqn);
            if (astData != null && astData.getSequenceDiagramData() != null &&
                    astData.getSequenceDiagramData().getMethodGroups() != null) {

                for (MethodGroup methodGroup : astData.getSequenceDiagramData().getMethodGroups()) {
                    usedMethodNames.add(methodGroup.getMethodName());
                }
            }
        }

        return usedMethodNames;
    }

    /**
     * 從方法簽名中提取方法名稱
     */
    private String extractMethodNameFromSignature(String methodSignature) {
        if (methodSignature == null || methodSignature.isEmpty()) {
            return "";
        }

        // 找到第一個 '(' 的位置，方法名稱在其前面
        int parenIndex = methodSignature.indexOf('(');
        if (parenIndex == -1) {
            return methodSignature;
        }

        String beforeParen = methodSignature.substring(0, parenIndex);

        // 找到最後一個 '.' 的位置，方法名稱在其後面
        int lastDotIndex = beforeParen.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return beforeParen;
        }

        return beforeParen.substring(lastDotIndex + 1);
    }

    /**
     * 檢查類別是否在 basePackages 範圍內
     */
    private boolean isClassInBasePackages(String classFqn, Set<String> basePackages) {
        if (basePackages == null || basePackages.isEmpty()) {
            return true; // 如果沒有指定 basePackages，則包含所有類別
        }

        return basePackages.stream()
                .anyMatch(basePackage -> classFqn.startsWith(basePackage));
    }

    /**
     * 從追蹤結果中遞迴提取所有涉及的類別
     */
    private void extractClassesFromTraceResult(TraceResult traceResult, Set<String> involvedMethodFqns) {
        extractClassesFromTraceResult(traceResult, involvedMethodFqns, new HashSet<>());
    }

    /**
     * 從追蹤結果中遞迴提取所有涉及的類別（帶訪問追蹤）
     */
    private void extractClassesFromTraceResult(TraceResult traceResult, Set<String> involvedMethodFqns,
            Set<Object> visitedNodes) {
        if (traceResult == null || traceResult.getSequenceNodes() == null) {
            return;
        }

        for (var node : traceResult.getSequenceNodes()) {
            if (node instanceof InteractionModel) {
                InteractionModel interaction = (InteractionModel) node;

                // 檢查是否已經訪問過此節點，避免循環引用
                if (visitedNodes.contains(interaction)) {
                    continue;
                }
                visitedNodes.add(interaction);

                // 添加被呼叫者的類別
                if (interaction.getCallee() != null) {
                    involvedMethodFqns
                            .add(AstClassUtil.getMethodFqn(interaction.getCallee(), interaction.getMethodName()));
                }

                // 遞迴處理內部呼叫
                if (interaction.getInternalCalls() != null) {
                    for (var internalNode : interaction.getInternalCalls()) {
                        if (internalNode instanceof InteractionModel) {
                            extractClassesFromTraceResult(
                                    new TraceResult("", List.of(internalNode)),
                                    involvedMethodFqns,
                                    visitedNodes);
                        }
                    }
                }

                // 處理鏈式呼叫
                if (interaction.getNextChainedCall() != null) {
                    extractClassesFromTraceResult(
                            new TraceResult("", List.of(interaction.getNextChainedCall())),
                            involvedMethodFqns,
                            visitedNodes);
                }
            }
        }
    }

    /**
     * 合併原始碼
     */
    private String mergeSourceCode(List<ClassSourceCode> classSources, CodeExtractionRequest request) {
        StringBuilder mergedCode = new StringBuilder();

        // 添加 Markdown 標題
        mergedCode.append("# 代碼提取結果\n\n");
        mergedCode.append("## 提取資訊\n\n");
        mergedCode.append("- **進入點**: `").append(request.getEntryPointMethodFqn()).append("`\n");
        mergedCode.append("- **提取時間**: ").append(java.time.LocalDateTime.now()).append("\n");
        mergedCode.append("- **總類別數**: ").append(classSources.size()).append("\n\n");

        // 按類別名稱排序
        classSources.sort((a, b) -> a.getClassFqn().compareTo(b.getClassFqn()));

        // 合併每個類別的原始碼
        for (ClassSourceCode classSource : classSources) {
            mergedCode.append("## ").append(classSource.getRelativePath()).append("\n\n");
            mergedCode.append("**類別**: `").append(classSource.getClassFqn()).append("`\n\n");
            mergedCode.append("```java\n");
            mergedCode.append(classSource.getSourceCode());
            mergedCode.append("\n```\n\n");
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

    @Data
    private static class EntryPointMethodInfo {
        private String classFqn;
        private String methodName;
    }
}
