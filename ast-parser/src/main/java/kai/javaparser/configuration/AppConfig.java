package kai.javaparser.configuration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 應用程式配置BO
 * 統一管理環境變數和配置屬性
 */
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppConfig {

    /**
     * AST解析輸出目錄
     */
    private String astDir = "parsed-ast";

    /**
     * 臨時目錄基礎路徑
     */
    private String tempBaseDir = System.getProperty("java.io.tmpdir");

    /**
     * 是否啟用調試模式
     */
    private boolean debugMode = false;

    /**
     * 最大並發任務數
     */
    private int maxConcurrentTasks = 10;

    /**
     * 任務超時時間（秒）
     */
    private int taskTimeoutSeconds = 300;

    /**
     * 圖數據庫配置
     */
    private GraphConfig graph = new GraphConfig();

    // Getters and Setters
    public String getAstDir() {
        return astDir;
    }

    public void setAstDir(String astDir) {
        this.astDir = astDir;
    }

    public String getTempBaseDir() {
        return tempBaseDir;
    }

    public void setTempBaseDir(String tempBaseDir) {
        this.tempBaseDir = tempBaseDir;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }

    public int getMaxConcurrentTasks() {
        return maxConcurrentTasks;
    }

    public void setMaxConcurrentTasks(int maxConcurrentTasks) {
        this.maxConcurrentTasks = maxConcurrentTasks;
    }

    public int getTaskTimeoutSeconds() {
        return taskTimeoutSeconds;
    }

    public void setTaskTimeoutSeconds(int taskTimeoutSeconds) {
        this.taskTimeoutSeconds = taskTimeoutSeconds;
    }

    public GraphConfig getGraph() {
        return graph;
    }

    public void setGraph(GraphConfig graph) {
        this.graph = graph;
    }

    /**
     * 獲取完整的AST輸出目錄路徑
     * 
     * @param projectName 專案名稱
     * @return 完整的輸出目錄路徑
     */
    public String getFullAstOutputDir(String projectName) {
        return java.nio.file.Paths.get(astDir, projectName).toString();
    }

    /**
     * 獲取臨時目錄的完整路徑
     * 
     * @param projectName 專案名稱
     * @return 臨時目錄的完整路徑
     */
    public String getFullTempDir(String projectName) {
        return java.nio.file.Paths.get(tempBaseDir, "ast-parser", projectName).toString();
    }

    @Override
    public String toString() {
        return "AppConfig{" +
                "astDir='" + astDir + '\'' +
                ", tempBaseDir='" + tempBaseDir + '\'' +
                ", debugMode=" + debugMode +
                ", maxConcurrentTasks=" + maxConcurrentTasks +
                ", taskTimeoutSeconds=" + taskTimeoutSeconds +
                ", graph=" + graph +
                '}';
    }

    /**
     * 圖數據庫配置類
     */
    public static class GraphConfig {
        /**
         * 排除的註解列表
         */
        private ExcludeConfig exclude = new ExcludeConfig();

        public ExcludeConfig getExclude() {
            return exclude;
        }

        public void setExclude(ExcludeConfig exclude) {
            this.exclude = exclude;
        }

        @Override
        public String toString() {
            return "GraphConfig{" +
                    "exclude=" + exclude +
                    '}';
        }
    }

    /**
     * 排除配置類
     */
    public static class ExcludeConfig {
        /**
         * 要排除的註解名稱列表（逗號分隔）
         */
        private String annotation = "Override,Deprecated,SuppressWarnings";

        private Set<String> excludedAnnotations;

        public String getAnnotation() {
            return annotation;
        }

        public void setAnnotation(String annotation) {
            this.annotation = annotation;
        }

        /**
         * 獲取排除的註解名稱列表
         * 
         * @return 註解名稱列表
         */
        public Set<String> getExcludedAnnotations() {
            if (excludedAnnotations != null) {
                return excludedAnnotations;
            }

            List<String> excludedAnnotations1 = new ArrayList<>();
            if (annotation != null && !annotation.trim().isEmpty()) {
                excludedAnnotations1 = Arrays.asList(annotation.split(","))
                        .stream()
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
            }

            // 加上JAVA 預設的
            excludedAnnotations1.add("Override");
            excludedAnnotations1.add("Deprecated");
            excludedAnnotations1.add("SuppressWarnings");
            excludedAnnotations = Set.copyOf(excludedAnnotations1);
            return excludedAnnotations;
        }

        @Override
        public String toString() {
            return "ExcludeConfig{" +
                    "annotation='" + annotation + '\'' +
                    '}';
        }
    }
}
