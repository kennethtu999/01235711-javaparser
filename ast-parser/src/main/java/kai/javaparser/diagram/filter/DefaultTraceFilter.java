package kai.javaparser.diagram.filter;

import java.util.Set;

import kai.javaparser.diagram.AstClassUtil;
import kai.javaparser.diagram.TraceFilter;
import kai.javaparser.diagram.idx.AstIndex;

/**
 * TraceFilter 的預設實作。
 * <p>
 * 提供多種常見的過濾規則：
 * 1. 根據類別的 FQN 前綴排除 (例如 "java.", "org.springframework.")。
 * 2. 根據方法名稱排除 (例如 "toString", "hashCode")。
 * 3. 自動排除屬性的 Getter/Setter 方法。
 * </p>
 */
public class DefaultTraceFilter implements TraceFilter {

    private final Set<String> excludedClassPrefixes;
    private final Set<String> excludedMethodNames;

    public DefaultTraceFilter(Set<String> excludedClassPrefixes, Set<String> excludedMethodNames) {
        this.excludedClassPrefixes = excludedClassPrefixes;
        this.excludedMethodNames = excludedMethodNames;
    }

    @Override
    public boolean shouldExclude(String methodFqn, AstIndex astIndex) {
        if (methodFqn == null || methodFqn.isEmpty()) {
            return true;
        }

        String classFqn = AstClassUtil.getClassFqnFromMethodFqn(methodFqn);
        String simpleMethodName = AstClassUtil.getMethodSignature(methodFqn).split("\\(")[0];

        // 規則 1: 檢查是否符合被排除的類別前綴
        if (excludedClassPrefixes.stream().anyMatch(classFqn::startsWith)) {
            System.err.println("INFO: 已跳過追蹤符合排除前綴的類別: " + classFqn);
            return true;
        }

        // 規則 2: 檢查是否為被排除的特定方法名稱
        if (excludedMethodNames.contains(simpleMethodName)) {
            System.err.println("INFO: 已跳過追蹤被排除的方法名稱: " + simpleMethodName);
            return true;
        }

        // // 規則 3: 檢查是否為 Getter/Setter
        // if (astIndex.isGetterSetter(methodFqn)) {
        // System.err.println("INFO: 已跳過追蹤 Getter/Setter: " + methodFqn);
        // return true;
        // }

        return false;
    }
}