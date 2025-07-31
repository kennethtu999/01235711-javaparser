package kai.javaparser.diagram;

import kai.javaparser.diagram.idx.AstIndex;

/**
 * 定義了一個過濾器介面，用於在生成序列圖時判斷是否應排除某個方法呼叫。
 * 實作這個介面可以自訂化追蹤邏輯。
 */
public interface TraceFilter {

    /**
     * 判斷一個方法呼叫是否應該從序列圖追蹤中排除。
     *
     * @param methodFqn 正在被檢查的方法的完整限定名 (FQN)。
     *                  例如: "com.example.service.UserService.findUserById(long)"
     * @param astIndex  AST 索引物件，提供對整個專案 AST 結構的查詢能力，
     *                  可用於實現更複雜的過濾規則 (例如，判斷是否為 getter/setter)。
     * @return 如果應該排除，則返回 true；否則返回 false。
     */
    boolean shouldExclude(String methodFqn, AstIndex astIndex);
}