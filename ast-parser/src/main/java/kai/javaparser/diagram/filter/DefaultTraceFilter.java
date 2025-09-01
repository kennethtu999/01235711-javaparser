package kai.javaparser.diagram.filter;

import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private final Logger logger = LoggerFactory.getLogger(DefaultTraceFilter.class);

    private final Set<String> excludedClassPrefixes;
    private final Set<String> excludedMethodNames;

    public DefaultTraceFilter(Set<String> excludedClassPrefixes, Set<String> excludedMethodNames) {
        this.excludedClassPrefixes = excludedClassPrefixes;
        this.excludedMethodNames = excludedMethodNames;
    }

    @Override
    public boolean shouldExclude(String classFqn, String simpleMethodName, AstIndex astIndex) {
        logger.info("INFO: 檢查是否排除方法: {} {}", classFqn, simpleMethodName);

        // 規則 1: 檢查是否符合被排除的類別前綴
        if (classFqn != null && excludedClassPrefixes.stream().anyMatch(classFqn::startsWith)) {
            logger.info("INFO: 已跳過追蹤符合排除前綴的類別: {}", classFqn);
            return true;
        }

        // 規則 2: 檢查是否為被排除的特定方法名稱
        if (simpleMethodName != null && excludedMethodNames.contains(simpleMethodName)) {
            logger.info("INFO: 已跳過追蹤被排除的方法名稱: {}", simpleMethodName);
            return true;
        }

        // // 規則 3: 檢查是否為 Getter/Setter
        // if (astIndex.isGetterSetter(methodFqn)) {
        // System.err.println("INFO: 已跳過追蹤 Getter/Setter: " + methodFqn);
        // return true;
        // }

        return false;
    }

    @Override
    public boolean shouldExclude(String methodFqn, AstIndex astIndex) {
        String classFqn = AstClassUtil.getClassFqnFromMethodFqn(methodFqn);
        String methodSignature = AstClassUtil.getMethodSignature(methodFqn);
        String simpleMethodName = methodSignature.split("\\(")[0];
        return shouldExclude(classFqn, simpleMethodName, astIndex);
    }
}