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
import kai.javaparser.astgraph.util.Neo4jIdGenerator;
import lombok.extern.slf4j.Slf4j;

/**
 * AST 圖轉換處理器基類
 * 提供共用的處理邏輯和輔助方法
 */
@Slf4j
public abstract class BaseAstGraphHandler implements IAstGraphHandler {

    @Autowired
    protected AppConfig appConfig;

    @Autowired
    protected Neo4jIdGenerator neo4jIdGenerator;

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
     * 生成類別 ID (使用統一生成器)
     */
    protected String generateClassId(String classFqn) {
        return neo4jIdGenerator.generateClassId(classFqn);
    }

    /**
     * 生成介面 ID (使用統一生成器)
     */
    protected String generateInterfaceId(String interfaceFqn) {
        return neo4jIdGenerator.generateInterfaceId(interfaceFqn);
    }

    /**
     * 生成方法 ID (使用統一生成器)
     */
    protected String generateMethodId(String methodName, String className) {
        return neo4jIdGenerator.generateMethodId(methodName, className);
    }

    /**
     * 生成註解 ID (使用統一生成器)
     */
    protected String generateAnnotationId(String annotationName, String targetType) {
        return neo4jIdGenerator.generateAnnotationId(annotationName, targetType);
    }

    /**
     * 創建類別實體（共用邏輯）
     */
    protected Neo4jClassNode createClassEntityFromSequenceData(JsonNode sequenceData, String className,
            String sourceFile, String packageName) {
        Neo4jClassNode classNode = new Neo4jClassNode();

        // 檢查 className 是否已經是完整的 FQN
        String classFqn;
        String actualPackageName;

        if (className.contains(".")) {
            // className 已經是完整的 FQN
            classFqn = className;
            actualPackageName = extractPackageNameFromFqn(className);
        } else {
            // className 只是簡單類名，需要與 packageName 組合
            classFqn = packageName.isEmpty() ? className : packageName + "." + className;
            actualPackageName = packageName;
        }

        classNode.setId(generateClassId(classFqn));
        classNode.setName(className); // 保持原始 className，用於關係匹配
        classNode.setPackageName(actualPackageName);
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

        // 檢查 interfaceName 是否已經是完整的 FQN
        String interfaceFqn;
        String actualPackageName;

        if (interfaceName.contains(".")) {
            // interfaceName 已經是完整的 FQN
            interfaceFqn = interfaceName;
            actualPackageName = extractPackageNameFromFqn(interfaceName);
        } else {
            // interfaceName 只是簡單介面名，需要與 packageName 組合
            interfaceFqn = packageName.isEmpty() ? interfaceName : packageName + "." + interfaceName;
            actualPackageName = packageName;
        }

        interfaceNode.setId(generateInterfaceId(interfaceFqn));
        interfaceNode.setName(interfaceName); // 保持原始 interfaceName，用於關係匹配
        interfaceNode.setPackageName(actualPackageName);
        interfaceNode.setSourceFile(sourceFile);
        interfaceNode.setLineNumber(1);
        interfaceNode.setColumnNumber(1);

        return interfaceNode;
    }

    /**
     * 從完整限定名中提取包名
     */
    private String extractPackageNameFromFqn(String fqn) {
        if (fqn == null || fqn.isEmpty()) {
            return "";
        }

        // 先去除泛型參數 <...>
        String withoutGenerics = fqn;
        int genericStart = fqn.indexOf('<');
        if (genericStart != -1) {
            withoutGenerics = fqn.substring(0, genericStart);
        }

        // 提取包名（去除最後的類名部分）
        int lastDotIndex = withoutGenerics.lastIndexOf('.');
        if (lastDotIndex >= 0) {
            return withoutGenerics.substring(0, lastDotIndex);
        }

        return "";
    }

    /**
     * 從完整限定名中提取簡單類名
     */
    private String extractSimpleClassName(String fqn) {
        if (fqn == null || fqn.isEmpty()) {
            return "Unknown";
        }

        // 先去除泛型參數 <...>
        String withoutGenerics = fqn;
        int genericStart = fqn.indexOf('<');
        if (genericStart != -1) {
            withoutGenerics = fqn.substring(0, genericStart);
        }

        // 提取最後一部分作為簡單類名
        int lastDotIndex = withoutGenerics.lastIndexOf('.');
        if (lastDotIndex >= 0) {
            return withoutGenerics.substring(lastDotIndex + 1);
        }

        return withoutGenerics;
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
