package kai.javaparser.ast.configuration;

import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 環境配置類
 * 負責從 .env 檔案載入環境變數配置
 */
@Component
public class EnvConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(EnvConfiguration.class);

    private final Dotenv dotenv;
    private final String javaHome;
    private final String javaVersion;
    private final String gradleOpts;

    public EnvConfiguration() {
        this.dotenv = loadDotenv();
        this.javaHome = loadJavaHome();
        this.javaVersion = loadJavaVersion();
        this.gradleOpts = loadGradleOpts();

        logger.info("EnvConfiguration 初始化完成");
        logger.info("JAVA_HOME: {}", javaHome);
        logger.info("JAVA_VERSION: {}", javaVersion);
        logger.info("GRADLE_OPTS: {}", gradleOpts);
    }

    /**
     * 載入 .env 檔案
     */
    private Dotenv loadDotenv() {
        try {
            // 嘗試從多個位置載入 .env 檔案
            String[] envFilePaths = {
                    ".env",
                    "ast-parser/.env",
                    "../.env",
                    "../../.env"
            };

            for (String envFilePath : envFilePaths) {
                Path envFile = Paths.get(envFilePath);
                if (envFile.toFile().exists()) {
                    logger.info("找到 .env 檔案: {}", envFile.toAbsolutePath());

                    // 處理 parent 為 null 的情況（當檔案在當前目錄時）
                    String directory = envFile.getParent() != null ? envFile.getParent().toString()
                            : System.getProperty("user.dir");

                    return Dotenv.configure()
                            .directory(directory)
                            .filename(envFile.getFileName().toString())
                            .load();
                }
            }

            logger.info("未找到 .env 檔案，使用預設配置");
            return Dotenv.configure().ignoreIfMissing().load();

        } catch (DotenvException e) {
            logger.warn("載入 .env 檔案失敗: {}", e.getMessage());
            return Dotenv.configure().ignoreIfMissing().load();
        } catch (Exception e) {
            logger.warn("載入 .env 檔案時發生未預期錯誤: {}", e.getMessage());
            return Dotenv.configure().ignoreIfMissing().load();
        }
    }

    /**
     * 獲取 Java Home 路徑
     */
    private String loadJavaHome() {
        // 1. 優先從 .env 檔案讀取
        String javaHome = dotenv.get("JAVA_BUILD_HOME");
        if (javaHome != null && !javaHome.trim().isEmpty()) {
            logger.info("使用 .env 檔案 JAVA_HOME: {}", javaHome);
            return javaHome;
        }
        throw new RuntimeException("JAVA_BUILD_HOME 未設置");
    }

    /**
     * 獲取 Java 版本
     */
    private String loadJavaVersion() {

        // 1. 優先從 .env 檔案讀取
        String javaVersion = dotenv.get("JAVA_BUILD_VERSION");
        if (javaVersion != null && !javaVersion.trim().isEmpty()) {
            logger.info("使用 .env 檔案 JAVA_BUILD_VERSION: {}", javaVersion);
            return javaVersion;
        }
        throw new RuntimeException("JAVA_BUILD_VERSION 未設置");
    }

    /**
     * 獲取 Gradle 選項
     */
    private String loadGradleOpts() {
        // 1. 優先從 .env 檔案讀取
        String gradleOpts = dotenv.get("GRADLE_BUILD_OPTS");
        if (gradleOpts != null && !gradleOpts.trim().isEmpty()) {
            logger.info("使用 .env 檔案 GRADLE_BUILD_OPTS: {}", gradleOpts);
            return gradleOpts;
        }
        throw new RuntimeException("GRADLE_BUILD_OPTS 未設置");
    }

    public String getEnv(String key, String defaultValue) {
        try {
            String value = dotenv.get(key);
            return value != null ? value : defaultValue;
        } catch (Exception e) {
            logger.debug("獲取環境變數 {} 時發生錯誤: {}", key, e.getMessage());
            return defaultValue;
        }
    }

    // Getters
    public String getJavaHome() {
        return javaHome;
    }

    public String getJavaVersion() {
        return javaVersion;
    }

    public String getGradleOpts() {
        return gradleOpts;
    }

    public Dotenv getDotenv() {
        return dotenv;
    }
}