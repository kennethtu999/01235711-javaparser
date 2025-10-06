package kai.javaparser.diagram.idx;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import kai.javaparser.ast.repository.AstRepository;
import kai.javaparser.model.FileAstData;

/**
 * 管理專案中所有類別的 AST 索引。
 * <p>
 * 重構後，此類別不再直接處理檔案系統操作，而是透過注入的 AstRepository
 * 來存取 AST 資料。這樣實現了關注點分離，讓 AstIndex 專注於索引邏輯，
 * 而將資料儲存抽象化。
 * </p>
 */
@Component
public class AstIndex {

    private final AstRepository astRepository;

    @Autowired
    public AstIndex(AstRepository astRepository) {
        this.astRepository = astRepository;
    }

    /**
     * 載入或建立 AST 索引。
     * 
     * <p>
     * 此方法委託給 AstRepository 來處理索引的載入或建立。
     * 具體的實作細節（如快取檢查、檔案系統掃描等）都由 repository 負責。
     * </p>
     *
     * @throws IOException            如果檔案讀寫或序列化/反序列化失敗。
     * @throws ClassNotFoundException 如果從快取檔案反序列化時找不到對應的類別。
     */
    public void loadOrBuild() throws IOException, ClassNotFoundException {
        astRepository.loadOrBuild();
    }

    /**
     * 根據類別的 FQN 取得對應的 FileAstData。
     * 
     * <p>
     * 此方法委託給 AstRepository 來處理資料的檢索。
     * Repository 會負責快取管理和資料載入的優化。
     * </p>
     *
     * @param classFqn 類別的完整限定名
     * @return FileAstData 物件，如果找不到則為 null。
     */
    public FileAstData getAstDataByClassFqn(String classFqn) {
        return astRepository.findByFqn(classFqn);
    }

    /**
     * 獲取所有已索引的類別 FQN 列表
     * 
     * @return 類別 FQN 列表
     */
    public List<String> getAllClassFqns() {
        return astRepository.getAllClassFqns();
    }

    /**
     * 檢查指定的類別是否存在於索引中
     * 
     * @param classFqn 類別的完整限定名
     * @return 如果存在則返回 true，否則返回 false
     */
    public boolean hasClass(String classFqn) {
        return astRepository.exists(classFqn);
    }
}