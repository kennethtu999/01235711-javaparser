package kai.javaparser.service;

import java.util.Set;

/**
 * 原始碼編織器介面
 * 定義程式碼過濾與重組的契約
 */
public interface SourceCodeWeaver {

    /**
     * 編織規則
     */
    interface WeavingRules {
        /**
         * 是否包含import語句
         */
        boolean includeImports();

        /**
         * 是否包含註解
         */
        boolean includeComments();

        /**
         * 是否只提取使用的方法
         */
        boolean extractOnlyUsedMethods();

        /**
         * 要提取的方法名稱集合（當extractOnlyUsedMethods為true時使用）
         */
        Set<String> getUsedMethodNames();

        /**
         * 要提取的類別完整限定名
         */
        String getClassFqn();
    }

    /**
     * 編織結果
     */
    interface WeavingResult {
        /**
         * 編織後的原始碼
         */
        String getWovenSourceCode();

        /**
         * 是否成功編織
         */
        boolean isSuccess();

        /**
         * 錯誤訊息（如果編織失敗）
         */
        String getErrorMessage();
    }

    /**
     * 編織原始碼
     * 
     * @param sourceCode 原始原始碼
     * @param rules      編織規則
     * @return 編織結果
     */
    WeavingResult weave(String sourceCode, WeavingRules rules);

    /**
     * 獲取編織器名稱
     * 
     * @return 編織器名稱，如 "JDT", "Regex" 等
     */
    String getWeaverName();
}
