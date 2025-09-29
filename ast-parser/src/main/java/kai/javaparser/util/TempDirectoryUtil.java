package kai.javaparser.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 臨時目錄工具類
 * 提供創建臨時目錄的統一方法
 */
public class TempDirectoryUtil {

    private static final Logger logger = LoggerFactory.getLogger(TempDirectoryUtil.class);

    /**
     * 創建臨時輸出目錄
     * 
     * @param projectPath 專案路徑
     * @return 臨時目錄路徑
     * @throws IOException 如果創建目錄失敗
     */
    public static Path createTempOutputDir(String projectPath) throws IOException {
        Path projectPathObj = Paths.get(projectPath);
        String projectName = projectPathObj.getFileName().toString();
        Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "ast-parser", projectName);

        Files.createDirectories(tempDir);
        logger.info("創建臨時輸出目錄: {}", tempDir);
        return tempDir;
    }

    /**
     * 創建臨時輸出目錄（使用配置）
     * 
     * @param projectPath 專案路徑
     * @param astDir      配置的AST目錄
     * @return 臨時目錄路徑
     * @throws IOException 如果創建目錄失敗
     */
    public static Path createTempOutputDirWithConfig(String projectPath, String astDir) throws IOException {
        Path projectPathObj = Paths.get(projectPath);
        String projectName = projectPathObj.getFileName().toString();
        Path tempDir = Paths.get(astDir, projectName);

        Files.createDirectories(tempDir);
        logger.info("創建臨時輸出目錄: {} (使用配置: {})", tempDir, astDir);
        return tempDir;
    }
}
