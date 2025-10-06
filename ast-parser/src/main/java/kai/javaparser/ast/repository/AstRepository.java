package kai.javaparser.ast.repository;

import java.io.IOException;
import java.util.List;

import kai.javaparser.model.FileAstData;

/**
 * AST 資料儲存抽象介面
 * 
 * 定義了 AST 資料的儲存和檢索契約，將 AST 解析的核心邏輯與資料儲存徹底解耦。
 * 這為後續所有模組建立了一個穩定、抽象的資料來源。
 */
public interface AstRepository {

    /**
     * 儲存 FileAstData 到儲存系統
     * 
     * @param fileAstData 要儲存的 AST 資料
     * @throws IOException 如果儲存失敗
     */
    void save(FileAstData fileAstData) throws IOException;

    /**
     * 根據類別的完整限定名 (FQN) 查找對應的 FileAstData
     * 
     * @param classFqn 類別的完整限定名
     * @return FileAstData 物件，如果找不到則為 null
     */
    FileAstData findByFqn(String classFqn);

    /**
     * 載入或建立 AST 索引
     * 
     * 此方法會先檢查快取是否存在。如果存在，則從快取載入索引；
     * 否則，掃描儲存系統來建立一個新的索引。
     * 
     * @throws IOException            如果載入或建立索引失敗
     * @throws ClassNotFoundException 如果從快取檔案反序列化時找不到對應的類別
     */
    void loadOrBuild() throws IOException, ClassNotFoundException;

    /**
     * 獲取所有已儲存的類別 FQN 列表
     * 
     * @return 類別 FQN 列表
     */
    List<String> getAllClassFqns();

    /**
     * 檢查指定的類別是否存在
     * 
     * @param classFqn 類別的完整限定名
     * @return 如果存在則返回 true，否則返回 false
     */
    boolean exists(String classFqn);
}
