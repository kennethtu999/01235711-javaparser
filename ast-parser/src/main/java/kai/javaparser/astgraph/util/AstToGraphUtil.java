package kai.javaparser.astgraph.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import kai.javaparser.ast.entity.Neo4jAnnotationNode;
import kai.javaparser.ast.entity.Neo4jMethodNode;
import kai.javaparser.configuration.AppConfig;
import lombok.extern.slf4j.Slf4j;

/**
 * AST 到圖數據庫轉換工具類
 * 包含所有輔助方法和工具函數
 */
@Slf4j
@Component
public class AstToGraphUtil {

    @Autowired
    private AppConfig appConfig;

    /**
     * 提取包名
     */
    public String extractPackageName(JsonNode rootNode) {
        JsonNode packageNode = rootNode.path("packageName");
        if (packageNode.isTextual()) {
            return packageNode.asText();
        }
        return "";
    }

    /**
     * 從 JSON 中提取類別名稱
     */
    public String extractClassNameFromJson(JsonNode rootNode) {
        JsonNode sequenceData = rootNode.path("sequenceDiagramData");
        if (sequenceData.isObject()) {
            String classFqn = sequenceData.path("classFqn").asText();
            if (!classFqn.isEmpty()) {
                int lastDotIndex = classFqn.lastIndexOf('.');
                if (lastDotIndex >= 0) {
                    return classFqn.substring(lastDotIndex + 1);
                }
                return classFqn;
            }
        }
        return "UnknownClass";
    }

    /**
     * 創建方法實體
     */
    public Neo4jMethodNode createMethodEntityFromSequenceData(JsonNode methodGroup, String className,
            String sourceFile, String packageName) {
        Neo4jMethodNode methodNode = new Neo4jMethodNode();
        String methodName = methodGroup.path("methodName").asText();
        String methodSignature = methodGroup.path("methodSignature").asText();
        int startLine = methodGroup.path("startLineNumber").asInt(0);
        int endLine = methodGroup.path("endLineNumber").asInt(0);

        methodNode.setId(String.format("method_%s_%s", className, methodName));
        methodNode.setName(methodName);
        methodNode.setClassName(className);
        methodNode.setPackageName(packageName);
        methodNode.setReturnType(methodSignature.isEmpty() ? "void" : methodSignature);
        methodNode.setSourceFile(sourceFile);
        methodNode.setLineNumber(startLine);
        methodNode.setColumnNumber(0);
        methodNode.setBodyLength(endLine - startLine + 1);
        methodNode.setIsConstructor(isConstructor(methodName, className));
        methodNode.setIsGetter(isGetter(methodName));
        methodNode.setIsSetter(isSetter(methodName));
        methodNode.setParameters(new ArrayList<>());
        methodNode.setModifiers(new ArrayList<>());

        return methodNode;
    }

    /**
     * 創建註解實體
     */
    public Neo4jAnnotationNode createAnnotationEntityFromData(JsonNode annotation, String targetType,
            String sourceFile, String packageName) {
        Neo4jAnnotationNode annotationNode = new Neo4jAnnotationNode();
        String annotationName = annotation.path("annotationName").asText();
        if (annotationName.isEmpty()) {
            annotationName = annotation.path("simpleName").asText();
        }
        int lineNumber = annotation.path("startLineNumber").asInt(0);
        JsonNode parameters = annotation.path("parameters");

        annotationNode.setId(String.format("annotation_%s_%s", targetType, annotationName));
        annotationNode.setName(annotationName);
        annotationNode.setTargetType(targetType);
        annotationNode.setSourceFile(sourceFile);
        annotationNode.setLineNumber(lineNumber);
        annotationNode.setColumnNumber(0);
        annotationNode.setParameters(convertParametersToList(parameters));

        return annotationNode;
    }

    /**
     * 創建錯誤響應
     */
    public Map<String, Object> createErrorResponse(String message) {
        return Map.of(
                "success", false,
                "message", message,
                "totalFiles", 0,
                "successFiles", 0,
                "errorFiles", 0,
                "totalNodes", 0,
                "totalRelationships", 0);
    }

    /**
     * 檢查是否應該排除註解
     */
    public boolean shouldExcludeAnnotation(String annotationName) {
        if (annotationName == null || annotationName.trim().isEmpty()) {
            return false;
        }

        return appConfig.getGraph().getExclude().getExcludedAnnotations().contains(annotationName.trim());
    }

    /**
     * 檢查是否應該過濾方法
     */
    public boolean shouldFilterMethod(String methodName, String className, List<Neo4jMethodNode> allMethods) {
        if (isConstructor(methodName, className)) {
            return true;
        }

        if (isJavaStandardMethod(methodName, className)) {
            return true;
        }

        if (isGetter(methodName) || isSetter(methodName)) {
            if (hasMatchingGetterSetter(methodName, allMethods)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 檢查是否為建構子
     */
    public boolean isConstructor(String methodName, String className) {
        if (methodName == null || methodName.trim().isEmpty()) {
            return false;
        }

        String trimmedName = methodName.trim();
        return trimmedName.contains("Constructor") ||
                trimmedName.equals("init") ||
                trimmedName.equals("<init>") ||
                trimmedName.equals("<clinit>") ||
                (className != null && trimmedName.equals(className));
    }

    /**
     * 檢查是否為 Getter 方法
     */
    public boolean isGetter(String methodName) {
        if (methodName == null || methodName.trim().isEmpty()) {
            return false;
        }

        String trimmedName = methodName.trim();
        return (trimmedName.startsWith("get") && trimmedName.length() > 3) ||
                (trimmedName.startsWith("is") && trimmedName.length() > 2) ||
                (trimmedName.startsWith("has") && trimmedName.length() > 3);
    }

    /**
     * 檢查是否為 Setter 方法
     */
    public boolean isSetter(String methodName) {
        if (methodName == null || methodName.trim().isEmpty()) {
            return false;
        }

        String trimmedName = methodName.trim();
        return trimmedName.startsWith("set") && trimmedName.length() > 3;
    }

    /**
     * 檢查是否為 Java 標準方法
     */
    public boolean isJavaStandardMethod(String methodName, String className) {
        if (methodName == null || methodName.trim().isEmpty()) {
            return false;
        }

        if (isObjectStandardMethod(methodName)) {
            return true;
        }

        return isCommonJavaStandardMethod(methodName);
    }

    /**
     * 檢查是否為 Object 標準方法
     */
    public boolean isObjectStandardMethod(String methodName) {
        return "toString".equals(methodName) ||
                "equals".equals(methodName) ||
                "hashCode".equals(methodName) ||
                "clone".equals(methodName) ||
                "finalize".equals(methodName) ||
                "getClass".equals(methodName) ||
                "notify".equals(methodName) ||
                "notifyAll".equals(methodName) ||
                "wait".equals(methodName);
    }

    /**
     * 檢查是否為常見的 Java 標準方法
     */
    public boolean isCommonJavaStandardMethod(String methodName) {
        return "main".equals(methodName) ||
                "run".equals(methodName) ||
                "start".equals(methodName) ||
                "stop".equals(methodName) ||
                "destroy".equals(methodName) ||
                "dispose".equals(methodName) ||
                "close".equals(methodName) ||
                "open".equals(methodName) ||
                "load".equals(methodName) ||
                "validate".equals(methodName) ||
                "process".equals(methodName) ||
                "handle".equals(methodName) ||
                "execute".equals(methodName);
    }

    /**
     * 檢查是否有匹配的 Getter/Setter 對
     */
    public boolean hasMatchingGetterSetter(String methodName, List<Neo4jMethodNode> allMethods) {
        if (methodName == null || methodName.trim().isEmpty()) {
            return false;
        }

        String trimmedName = methodName.trim();

        if (isGetter(trimmedName)) {
            String expectedSetter = "set" + trimmedName.substring(3);
            return allMethods.stream().anyMatch(m -> m.getName().equals(expectedSetter));
        }

        if (isSetter(trimmedName)) {
            String expectedGetter = "get" + trimmedName.substring(3);
            String expectedIsGetter = "is" + trimmedName.substring(3);
            return allMethods.stream()
                    .anyMatch(m -> m.getName().equals(expectedGetter) || m.getName().equals(expectedIsGetter));
        }

        return false;
    }

    /**
     * 將 JSON 參數轉換為字符串列表
     */
    public List<String> convertParametersToList(JsonNode parameters) {
        List<String> paramList = new ArrayList<>();
        if (parameters.isArray()) {
            for (JsonNode param : parameters) {
                paramList.add(param.asText());
            }
        }
        return paramList;
    }
}
