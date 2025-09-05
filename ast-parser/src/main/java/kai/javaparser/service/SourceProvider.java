package kai.javaparser.service;

import java.util.Set;

/**
 * 原始碼提供者介面
 * 定義獲取原始碼的契約，支援不同的原始碼來源
 */
public interface SourceProvider {

    /**
     * 獲取指定類別的原始碼
     * 
     * @param classFqn 類別的完整限定名
     * @return 原始碼字串，如果找不到則返回null
     */
    String getSourceCode(String classFqn);

    /**
     * 獲取多個類別的原始碼
     * 
     * @param classFqns 類別完整限定名集合
     * @return 類別FQN到原始碼的映射
     */
    java.util.Map<String, String> getSourceCodes(Set<String> classFqns);

    /**
     * 檢查指定類別是否存在
     * 
     * @param classFqn 類別的完整限定名
     * @return 如果存在返回true，否則返回false
     */
    boolean exists(String classFqn);

    /**
     * 獲取提供者名稱
     * 
     * @return 提供者名稱，如 "FileSystem", "Database" 等
     */
    String getProviderName();
}
