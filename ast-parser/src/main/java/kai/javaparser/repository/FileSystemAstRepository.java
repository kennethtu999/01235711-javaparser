package kai.javaparser.repository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import kai.javaparser.ast.model.FileAstData;
import kai.javaparser.ast.repository.AstRepository;

/**
 * 基於檔案系統的 AST 資料儲存實現
 * 
 * 將原本分散在 AstIndex 和 AstParserService 中的檔案系統操作邏輯集中到此類別，
 * 實現了 AstRepository 介面，提供統一的 AST 資料存取抽象。
 */
@Repository
public class FileSystemAstRepository implements AstRepository {

    private static final Logger logger = LoggerFactory.getLogger(FileSystemAstRepository.class);
    private static final String CACHE_FILE_NAME = "ast-index.cache";

    // 索引: 類別的 FQN -> 包含該類別 AST 的 JSON 檔案路徑
    private Map<String, Path> classToPathIndex;
    // 快取: JSON 檔案路徑 -> 已解析的 FileAstData 物件，避免重複讀取和反序列化 JSON
    private final Map<Path, FileAstData> astDataCache = new ConcurrentHashMap<>();

    private final ObjectMapper mapper;

    private Path astJsonDir;
    private Path cacheFilePath;

    @Value("${app.astDir}")
    private String initAstDir;

    public FileSystemAstRepository(@Autowired ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @PostConstruct
    public void postConstruct() {
        this.initialize(Path.of(initAstDir));
    }

    /**
     * 初始化儲存目錄
     * 
     * @param astJsonDir AST JSON 檔案目錄
     */
    public void initialize(Path astJsonDir) {
        this.astJsonDir = astJsonDir;
        this.cacheFilePath = astJsonDir.resolve(CACHE_FILE_NAME);
        this.classToPathIndex = new ConcurrentHashMap<>();
    }

    @Override
    public void save(FileAstData fileAstData) throws IOException {
        if (astJsonDir == null) {
            throw new IllegalStateException("Repository not initialized with AST directory");
        }

        if (fileAstData == null) {
            logger.warn("Attempted to save null FileAstData");
            return;
        }

        // 確保輸出目錄存在
        Files.createDirectories(astJsonDir);

        // 根據相對路徑建立輸出檔案路徑
        Path relativePath = Path.of(fileAstData.getRelativePath());
        Path outputFile = astJsonDir.resolve(relativePath.toString().replace(".java", ".json"));

        // 確保父目錄存在
        Files.createDirectories(outputFile.getParent());

        // 儲存 JSON 檔案
        mapper.writeValue(outputFile.toFile(), fileAstData);

        // 更新索引
        fileAstData.findTopLevelClassFqn().ifPresent(classFqn -> {
            classToPathIndex.put(classFqn, outputFile);
            logger.debug("Updated index for class: {} -> {}", classFqn, outputFile);
        });

        logger.debug("Saved AST data to: {}", outputFile);
    }

    @Override
    public FileAstData findByFqn(String classFqn) {
        if (classFqn == null || classFqn.trim().isEmpty()) {
            return null;
        }

        Path path = classToPathIndex.get(classFqn);
        if (path == null) {
            logger.debug("Class not found in index: {}", classFqn);
            return null;
        }

        // computeIfAbsent 確保對同一個檔案，只會讀取和解析一次
        return astDataCache.computeIfAbsent(path, this::getAstDataFromFile);
    }

    @Override
    public void loadOrBuild() throws IOException, ClassNotFoundException {
        if (astJsonDir == null) {
            // 在測試環境中，嘗試使用預設的 AST 目錄
            String defaultAstDir = System.getProperty("user.dir") + "/parsed-ast";
            this.astJsonDir = Path.of(defaultAstDir);
            this.cacheFilePath = this.astJsonDir.resolve(CACHE_FILE_NAME);
            logger.info("Auto-initialized repository with default AST directory: {}", astJsonDir);
        }

        if (isValidCacheFile(cacheFilePath)) {
            loadFromCache();
            logger.info("Loaded AST index from cache: {}", cacheFilePath);
        } else {
            buildFromFileSystem();
            saveToCache();
            logger.info("Built AST index from file system and saved to cache: {}", cacheFilePath);
        }
    }

    @Override
    public List<String> getAllClassFqns() {
        return new ArrayList<>(classToPathIndex.keySet());
    }

    @Override
    public boolean exists(String classFqn) {
        return classToPathIndex.containsKey(classFqn);
    }

    /**
     * 清理緩存，用於測試環境
     */
    public void clearCache() {
        astDataCache.clear();
        logger.info("AST緩存已清理");
    }

    /**
     * 獲取 AST JSON 目錄路徑
     * 
     * @return AST JSON 目錄路徑
     */
    public Path getAstJsonDir() {
        return astJsonDir;
    }

    private boolean isValidCacheFile(Path cacheFilePath) {
        // 如果快取檔案不存在，則不使用快取
        if (!Files.exists(cacheFilePath)) {
            return false;
        }

        // 如果 ast 資料夾沒有子資料夾，則不使用快取
        File cacheFile = cacheFilePath.toFile();
        File astFolder = astJsonDir.toFile();
        File astFolderFirstSubFolder = Arrays.stream(astFolder.listFiles()).filter(File::isDirectory).findFirst()
                .orElse(null);
        if (astFolderFirstSubFolder == null) {
            return false;
        }

        // 如果快取檔案的修改時間比 ast 資料夾第一個子資料夾的修改時間還要早，則不使用快取
        Date cacheFileDate = new Date(cacheFile.lastModified());
        Date astFolderFirstSubFolderDate = new Date(astFolderFirstSubFolder.lastModified());
        if (cacheFileDate.before(astFolderFirstSubFolderDate)) {
            return false;
        }

        return true;
    }

    @SuppressWarnings("unchecked")
    private void loadFromCache() throws IOException, ClassNotFoundException {
        Map<String, String> data = mapper.readValue(cacheFilePath.toFile(), Map.class);
        if (data != null) {
            data.forEach((classFqn, path) -> {
                classToPathIndex.put(classFqn, Path.of(path.replaceFirst("file://", "")));
            });
        }
    }

    private void saveToCache() throws IOException {
        mapper.writeValue(cacheFilePath.toFile(), this.classToPathIndex);
    }

    private void buildFromFileSystem() throws IOException {
        try (Stream<Path> paths = Files.walk(astJsonDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json"))
                    .forEach(jsonFile -> {
                        FileAstData astData = getAstDataFromFile(jsonFile);
                        // 假設一個 Java 檔案只定義一個 public 頂層類別
                        if (astData != null) {
                            astData.findTopLevelClassFqn().ifPresent(classFqn -> {
                                classToPathIndex.put(classFqn, jsonFile);
                            });
                        }
                    });
        }
    }

    private FileAstData getAstDataFromFile(Path path) {
        try {
            FileAstData result = mapper.readValue(path.toFile(), FileAstData.class);
            if (result == null) {
                logger.warn("JSON 解析結果為 null: {}", path);
                return null;
            }
            return result;
        } catch (IOException e) {
            logger.error("讀取或解析 AST 檔案失敗: {}, 錯誤: {}", path, e.getMessage());
            return null;
        } catch (Exception e) {
            logger.error("解析 AST 檔案時發生未知錯誤: {}, 錯誤: {}", path, e.getMessage());
            return null;
        }
    }
}
