package kai.javaparser.astgraph.service.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.JsonNode;

import kai.javaparser.ast.entity.Neo4jAnnotationNode;
import kai.javaparser.ast.entity.Neo4jClassNode;
import kai.javaparser.ast.entity.Neo4jInterfaceNode;
import kai.javaparser.ast.entity.Neo4jMethodNode;
import kai.javaparser.configuration.AppConfig;
import lombok.extern.slf4j.Slf4j;

/**
 * AST 圖轉換處理器基類
 * 提供共用的處理邏輯和輔助方法
 */
@Slf4j
public abstract class BaseAstGraphHandler implements IAstGraphHandler {

    @Autowired
    protected AppConfig appConfig;

    /**
     * 提取包名
     */
    protected String extractPackageName(JsonNode rootNode) {
        JsonNode packageNode = rootNode.path("packageName");
        if (packageNode.isTextual()) {
            return packageNode.asText();
        }
        return "";
    }

    /**
     * 從 JSON 節點中提取類名
     */
    protected String extractClassNameFromJson(JsonNode rootNode) {
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
     * 檢查註解是否應該被排除
     */
    protected boolean shouldExcludeAnnotation(String annotationName) {
        if (annotationName == null || annotationName.trim().isEmpty()) {
            return false;
        }

        Set<String> excludedAnnotations = appConfig.getGraph().getExclude().getExcludedAnnotations();
        return excludedAnnotations.contains(annotationName.trim());
    }

    /**
     * 將參數節點轉換為字符串列表
     */
    protected List<String> convertParametersToList(JsonNode parameters) {
        List<String> paramList = new ArrayList<>();
        if (parameters.isArray()) {
            for (JsonNode param : parameters) {
                paramList.add(param.asText());
            }
        }
        return paramList;
    }

    /**
     * 生成類別 ID
     */
    protected String generateClassId(String className, String packageName) {
        return String.format("class_%s_%s", packageName.replace(".", "_"), className);
    }

    /**
     * 生成介面 ID
     */
    protected String generateInterfaceId(String interfaceName, String packageName) {
        return String.format("interface_%s_%s", packageName.replace(".", "_"), interfaceName);
    }

    /**
     * 生成方法 ID
     */
    protected String generateMethodId(String methodName, String className) {
        return String.format("method_%s_%s", className, methodName);
    }

    /**
     * 生成註解 ID
     */
    protected String generateAnnotationId(String annotationName, String targetType) {
        return String.format("annotation_%s_%s", targetType, annotationName);
    }

    /**
     * 創建類別實體（共用邏輯）
     */
    protected Neo4jClassNode createClassEntityFromSequenceData(JsonNode sequenceData, String className,
            String sourceFile, String packageName) {
        Neo4jClassNode classNode = new Neo4jClassNode();
        classNode.setId(generateClassId(className, packageName));
        classNode.setName(className);
        classNode.setPackageName(packageName);
        classNode.setSourceFile(sourceFile);
        classNode.setLineNumber(1);
        classNode.setColumnNumber(1);

        return classNode;
    }

    /**
     * 創建介面實體（共用邏輯）
     */
    protected Neo4jInterfaceNode createInterfaceEntityFromSequenceData(JsonNode sequenceData, String interfaceName,
            String sourceFile, String packageName) {
        Neo4jInterfaceNode interfaceNode = new Neo4jInterfaceNode();
        interfaceNode.setId(generateInterfaceId(interfaceName, packageName));
        interfaceNode.setName(interfaceName);
        interfaceNode.setPackageName(packageName);
        interfaceNode.setSourceFile(sourceFile);
        interfaceNode.setLineNumber(1);
        interfaceNode.setColumnNumber(1);

        return interfaceNode;
    }

    /**
     * 創建方法實體（共用邏輯）
     */
    protected Neo4jMethodNode createMethodEntityFromSequenceData(JsonNode methodGroup, String className,
            String sourceFile, String packageName) {
        Neo4jMethodNode methodNode = new Neo4jMethodNode();
        String methodName = methodGroup.path("methodName").asText();
        String methodSignature = methodGroup.path("methodSignature").asText();
        int startLine = methodGroup.path("startLineNumber").asInt(0);
        int endLine = methodGroup.path("endLineNumber").asInt(0);

        methodNode.setId(generateMethodId(methodName, className));
        methodNode.setName(methodName);
        methodNode.setClassName(className);
        methodNode.setPackageName(packageName);
        methodNode.setReturnType(methodSignature.isEmpty() ? "void" : methodSignature);
        methodNode.setSourceFile(sourceFile);
        methodNode.setLineNumber(startLine);
        methodNode.setColumnNumber(0);
        methodNode.setBodyLength(endLine - startLine + 1);

        return methodNode;
    }

    /**
     * 創建註解實體（共用邏輯）
     */
    protected Neo4jAnnotationNode createAnnotationEntityFromData(JsonNode annotation, String targetType,
            String sourceFile, String packageName) {
        Neo4jAnnotationNode annotationNode = new Neo4jAnnotationNode();
        String annotationName = annotation.path("annotationName").asText();
        if (annotationName.isEmpty()) {
            annotationName = annotation.path("simpleName").asText();
        }
        int lineNumber = annotation.path("startLineNumber").asInt(0);
        JsonNode parameters = annotation.path("parameters");

        annotationNode.setId(generateAnnotationId(annotationName, targetType));
        annotationNode.setName(annotationName);
        annotationNode.setTargetType(targetType);
        annotationNode.setSourceFile(sourceFile);
        annotationNode.setLineNumber(lineNumber);
        annotationNode.setColumnNumber(0);
        annotationNode.setParameters(convertParametersToList(parameters));

        return annotationNode;
    }

    // ==================== 輔助方法 ====================

    /**
     * 檢查是否為構造函數
     */
    protected boolean isConstructor(String methodName, String className) {
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
    protected boolean isGetter(String methodName) {
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
    protected boolean isSetter(String methodName) {
        if (methodName == null || methodName.trim().isEmpty()) {
            return false;
        }

        String trimmedName = methodName.trim();
        return trimmedName.startsWith("set") && trimmedName.length() > 3;
    }

    /**
     * 檢查方法是否應該被過濾掉
     */
    protected boolean shouldFilterMethod(String methodName, String className, List<Neo4jMethodNode> allMethods) {
        if (isConstructor(methodName, className)) {
            log.debug("過濾構造函數: {}", methodName);
            return true;
        }

        if (isJavaStandardMethod(methodName, className)) {
            log.debug("過濾 Java 標準方法: {}", methodName);
            return true;
        }

        if (isGetter(methodName) || isSetter(methodName)) {
            if (hasMatchingGetterSetter(methodName, allMethods)) {
                log.debug("過濾 Getter/Setter 方法（成對存在）: {}", methodName);
                return true;
            }
        }

        return false;
    }

    /**
     * 檢查是否為 Java 標準方法
     */
    protected boolean isJavaStandardMethod(String methodName, String className) {
        if (methodName == null || methodName.trim().isEmpty()) {
            return false;
        }

        if (isObjectStandardMethod(methodName)) {
            return true;
        }

        return isCommonJavaStandardMethod(methodName);
    }

    /**
     * 檢查是否為 Object 類的標準方法
     */
    protected boolean isObjectStandardMethod(String methodName) {
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
    protected boolean isCommonJavaStandardMethod(String methodName) {
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
     * 檢查 Getter/Setter 方法是否成對存在
     */
    protected boolean hasMatchingGetterSetter(String methodName, List<Neo4jMethodNode> allMethods) {
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
}
