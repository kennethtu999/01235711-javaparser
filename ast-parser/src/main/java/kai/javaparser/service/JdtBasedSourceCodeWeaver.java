package kai.javaparser.service;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import kai.javaparser.diagram.AstClassUtil;
import kai.javaparser.diagram.idx.AstIndex;
import kai.javaparser.model.FieldInfo;
import kai.javaparser.model.FileAstData;
import kai.javaparser.model.MethodGroup;

/**
 * 基於JDT的原始碼編織器
 * 使用AST資訊進行精確的程式碼過濾與重組
 */
@Component
public class JdtBasedSourceCodeWeaver implements SourceCodeWeaver {
    private static final Logger logger = LoggerFactory.getLogger(JdtBasedSourceCodeWeaver.class);

    private final AstIndex astIndex;

    public JdtBasedSourceCodeWeaver(AstIndex astIndex) {
        this.astIndex = astIndex;
    }

    @Override
    public WeavingResult weave(String sourceCode, WeavingRules rules) {
        logger.debug("開始編織原始碼，類別: {}, 規則: {}", rules.getClassFqn(), rules);

        try {
            // 獲取AST資料
            FileAstData astData = astIndex.getAstDataByClassFqn(rules.getClassFqn());
            if (astData == null) {
                return new WeavingResultImpl("", false, "找不到類別的AST資料: " + rules.getClassFqn());
            }

            String wovenCode;
            if (rules.extractOnlyUsedMethods()) {
                // 只提取使用的方法和所有屬性
                wovenCode = extractUsedMethodsAndAllFields(sourceCode, astData, rules);
            } else {
                // 提取完整類別
                wovenCode = extractFullClass(sourceCode, astData, rules);
            }

            if (wovenCode == null || wovenCode.trim().isEmpty()) {
                return new WeavingResultImpl("", false, "編織後的原始碼為空");
            }

            logger.debug("原始碼編織完成，長度: {} 字元", wovenCode.length());
            return new WeavingResultImpl(wovenCode, true, null);

        } catch (Exception e) {
            logger.error("原始碼編織失敗", e);
            return new WeavingResultImpl("", false, "編織失敗: " + e.getMessage());
        }
    }

    @Override
    public String getWeaverName() {
        return "JDT";
    }

    /**
     * 提取完整類別
     */
    private String extractFullClass(String sourceCode, FileAstData astData, WeavingRules rules) {
        StringBuilder result = new StringBuilder();
        String[] lines = sourceCode.split("\n");

        boolean inClass = false;
        for (String line : lines) {
            String trimmedLine = line.trim();

            // 檢測類別開始
            if (trimmedLine.startsWith("public class ") || trimmedLine.startsWith("class ")) {
                inClass = true;
            }

            if (!inClass) {
                // 在類別定義之前，根據規則決定是否保留
                if (rules.includeImports() &&
                        (trimmedLine.startsWith("package ") || trimmedLine.startsWith("import "))) {
                    result.append(line).append("\n");
                }
                continue;
            }

            // 在類別內部，根據規則決定是否保留註解
            if (!rules.includeComments() && isCommentLine(trimmedLine)) {
                continue;
            }

            result.append(line).append("\n");
        }

        return result.toString();
    }

    /**
     * 提取使用的方法和所有屬性
     */
    private String extractUsedMethodsAndAllFields(String sourceCode, FileAstData astData, WeavingRules rules) {
        StringBuilder result = new StringBuilder();
        String[] lines = sourceCode.split("\n");

        // 獲取所有屬性的行號
        Set<Integer> fieldLines = getFieldLines(astData);

        // 獲取使用的方法的行號
        Set<Integer> usedMethodLines = getUsedMethodLines(astData, rules);

        // 獲取構造函數的行號（如果規則允許）
        Set<Integer> constructorLines = new HashSet<>();
        if (rules.includeConstructors()) {
            constructorLines = getConstructorLines(astData);
        }

        // 計算需要保留的所有行號
        Set<Integer> linesToKeep = new HashSet<>();
        linesToKeep.addAll(fieldLines);
        linesToKeep.addAll(usedMethodLines);
        linesToKeep.addAll(constructorLines);

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
                // 在類別定義之前，根據規則決定是否保留
                if (rules.includeImports() &&
                        (trimmedLine.startsWith("package ") || trimmedLine.startsWith("import "))) {
                    result.append(line).append("\n");
                }
                continue;
            }

            // 只保留計算出需要的行
            if (linesToKeep.contains(i)) {
                result.append(line).append("\n");
            }
        }

        result.append("}");
        return result.toString();
    }

    /**
     * 獲取所有屬性的行號
     */
    private Set<Integer> getFieldLines(FileAstData astData) {
        Set<Integer> fieldLines = new HashSet<>();
        if (astData.getFields() != null) {
            for (FieldInfo field : astData.getFields()) {
                for (int i = field.getStartLineNumber() - 1; i < field.getEndLineNumber(); i++) {
                    if (i >= 0) {
                        fieldLines.add(i);
                    }
                }
            }
        }
        return fieldLines;
    }

    /**
     * 獲取使用的方法的行號
     */
    private Set<Integer> getUsedMethodLines(FileAstData astData, WeavingRules rules) {
        Set<Integer> usedMethodLines = new HashSet<>();

        if (astData.getSequenceDiagramData() != null &&
                astData.getSequenceDiagramData().getMethodGroups() != null) {

            Set<String> usedMethodNames = rules.getUsedMethodNames();
            for (MethodGroup methodGroup : astData.getSequenceDiagramData().getMethodGroups()) {
                if (usedMethodNames.contains(methodGroup.getMethodName())) {
                    for (int i = methodGroup.getStartLineNumber() - 1; i < methodGroup.getEndLineNumber(); i++) {
                        if (i >= 0) {
                            usedMethodLines.add(i);
                        }
                    }
                }
            }
        }
        return usedMethodLines;
    }

    /**
     * 獲取構造函數的行號
     */
    private Set<Integer> getConstructorLines(FileAstData astData) {
        Set<Integer> constructorLines = new HashSet<>();

        if (astData.getSequenceDiagramData() != null &&
                astData.getSequenceDiagramData().getMethodGroups() != null) {

            for (MethodGroup methodGroup : astData.getSequenceDiagramData().getMethodGroups()) {
                // 檢查是否為構造函數（方法名與類名相同）
                String methodName = methodGroup.getMethodName();
                String className = AstClassUtil.getSimpleClassName(methodGroup.getClassName());

                if (methodName != null && className != null && methodName.equals(className)) {
                    for (int i = methodGroup.getStartLineNumber() - 1; i < methodGroup.getEndLineNumber(); i++) {
                        if (i >= 0) {
                            constructorLines.add(i);
                        }
                    }
                }
            }
        }
        return constructorLines;
    }

    /**
     * 檢查是否為註解行
     */
    private boolean isCommentLine(String line) {
        return line.startsWith("//") || line.startsWith("/*") || line.startsWith("*");
    }

    /**
     * 編織結果實現
     */
    private static class WeavingResultImpl implements WeavingResult {
        private final String wovenSourceCode;
        private final boolean success;
        private final String errorMessage;

        public WeavingResultImpl(String wovenSourceCode, boolean success, String errorMessage) {
            this.wovenSourceCode = wovenSourceCode;
            this.success = success;
            this.errorMessage = errorMessage;
        }

        @Override
        public String getWovenSourceCode() {
            return wovenSourceCode;
        }

        @Override
        public boolean isSuccess() {
            return success;
        }

        @Override
        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
