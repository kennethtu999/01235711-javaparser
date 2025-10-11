package kai.javaparser.astgraph.util;

import org.springframework.stereotype.Component;

/**
 * Neo4j ID 生成器
 * 統一管理所有 Neo4j 節點的 ID 生成邏輯，確保格式一致性
 */
@Component
public class Neo4jIdGenerator {

    /**
     * 生成類別 ID
     * 
     * @param classFqn 類別完整限定名，例如 "com.ibm.tw.aa.bo.AccountItem"
     * @return 類別 ID，例如 "class_com_ibm_tw_aa_bo_AccountItem"
     */
    public String generateClassId(String classFqn) {
        if (classFqn == null || classFqn.isEmpty()) {
            return "class_unknown";
        }

        String packageName = extractPackageNameFromFqn(classFqn);
        String simpleClassName = extractSimpleClassName(classFqn);

        if (packageName.isEmpty()) {
            return String.format("class_%s", simpleClassName);
        }

        return String.format("class_%s_%s", packageName.replace(".", "_"), simpleClassName);
    }

    /**
     * 生成介面 ID
     * 
     * @param interfaceFqn 介面完整限定名，例如 "com.ibm.tw.aa.bo.IAccountService"
     * @return 介面 ID，例如 "interface_com_ibm_tw_aa_bo_IAccountService"
     */
    public String generateInterfaceId(String interfaceFqn) {
        if (interfaceFqn == null || interfaceFqn.isEmpty()) {
            return "interface_unknown";
        }

        String packageName = extractPackageNameFromFqn(interfaceFqn);
        String simpleInterfaceName = extractSimpleClassName(interfaceFqn);

        if (packageName.isEmpty()) {
            return String.format("interface_%s", simpleInterfaceName);
        }

        return String.format("interface_%s_%s", packageName.replace(".", "_"), simpleInterfaceName);
    }

    /**
     * 生成方法 ID
     * 
     * @param methodName 方法名，例如 "getAccountInfo"
     * @param className  類別名，例如 "AccountItem"
     * @return 方法 ID，例如 "method_AccountItem_getAccountInfo"
     */
    public String generateMethodId(String methodName, String className) {
        if (methodName == null || methodName.isEmpty()) {
            methodName = "unknown";
        }
        if (className == null || className.isEmpty()) {
            className = "Unknown";
        }

        return String.format("method_%s_%s", className, methodName);
    }

    /**
     * 生成註解 ID
     * 
     * @param annotationName 註解名，例如 "Service"
     * @param targetType     目標類型，例如 "Class", "Method", "Interface"
     * @return 註解 ID，例如 "annotation_Class_Service"
     */
    public String generateAnnotationId(String annotationName, String targetType) {
        if (annotationName == null || annotationName.isEmpty()) {
            annotationName = "unknown";
        }
        if (targetType == null || targetType.isEmpty()) {
            targetType = "Unknown";
        }

        return String.format("annotation_%s_%s", targetType, annotationName);
    }

    /**
     * 從完整限定名中提取包名
     * 
     * @param fqn 完整限定名，例如 "com.ibm.tw.aa.bo.AccountItem" 或 "java.util.List<String>"
     * @return 包名，例如 "com.ibm.tw.aa.bo" 或 "java.util"
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
     * 從完整限定名中提取簡單類名，處理泛型參數
     * 
     * @param fqn 完整限定名，例如 "java.util.List<String>" 或 "com.example.MyClass"
     * @return 簡單類名，例如 "List" 或 "MyClass"
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
     * 驗證 ID 格式是否正確
     * 
     * @param id             要驗證的 ID
     * @param expectedPrefix 期望的前綴，例如 "class_", "interface_", "method_",
     *                       "annotation_"
     * @return 是否格式正確
     */
    public boolean isValidId(String id, String expectedPrefix) {
        if (id == null || expectedPrefix == null) {
            return false;
        }
        return id.startsWith(expectedPrefix);
    }

    /**
     * 從 ID 中提取類型前綴
     * 
     * @param id Neo4j 節點 ID
     * @return 類型前綴，例如 "class", "interface", "method", "annotation"
     */
    public String extractTypePrefix(String id) {
        if (id == null || !id.contains("_")) {
            return "unknown";
        }

        int firstUnderscoreIndex = id.indexOf('_');
        return id.substring(0, firstUnderscoreIndex);
    }
}
