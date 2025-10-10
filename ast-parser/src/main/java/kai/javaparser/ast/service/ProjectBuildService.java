package kai.javaparser.ast.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.eclipse.EclipseProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import kai.javaparser.ast.configuration.EnvConfiguration;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * 專案建置服務
 * 負責在 AST 解析前執行專案建置，確保所有依賴和編譯產物都可用
 * 支援 Gradle、Maven 和 Eclipse 專案類型
 */
@Service
public class ProjectBuildService {

    private static final Logger logger = LoggerFactory.getLogger(ProjectBuildService.class);

    // 可配置的 Eclipse 路徑變數
    private static final Map<String, String> ECLIPSE_PATH_VARIABLES = Map.of(
            "M2_REPO", System.getProperty("user.home") + File.separator + ".m2" + File.separator + "repository");

    @Autowired
    private EnvConfiguration envConfiguration;

    private enum ProjectType {
        GRADLE, MAVEN, ECLIPSE, UNKNOWN
    }

    /**
     * 建置專案並收集源碼目錄和 classpath 信息
     * 
     * @param projectRoot 專案根目錄
     * @return 建置結果，包含源碼目錄和 classpath
     */
    public BuildResult buildProject(Path projectRoot) throws Exception {
        logger.info("開始建置專案: {}", projectRoot);
        logger.info("使用 Java 環境 - JAVA_HOME: {}, Java 版本: {}",
                envConfiguration.getJavaHome(), envConfiguration.getJavaVersion());

        ProjectType type = detectProjectType(projectRoot);
        if (type == ProjectType.UNKNOWN) {
            throw new IllegalArgumentException("無法識別專案類型，請確保是 Gradle、Maven 或 Eclipse 專案: " + projectRoot);
        }

        logger.info("檢測到專案類型: {}", type);

        Set<String> sourceRoots = new LinkedHashSet<>();
        Set<String> projectClasspath = new LinkedHashSet<>();

        switch (type) {
            case GRADLE:
                processGradleProject(projectRoot, sourceRoots, projectClasspath);
                break;
            case MAVEN:
                processMavenProject(projectRoot, sourceRoots, projectClasspath);
                break;
            case ECLIPSE:
                processEclipseProject(projectRoot, sourceRoots, projectClasspath);
                break;
            default:
                throw new IllegalStateException("不支援的專案類型: " + type);
        }

        logger.info("專案建置完成，收集到 {} 個源碼目錄，{} 個 classpath 項目",
                sourceRoots.size(), projectClasspath.size());

        return new BuildResult(sourceRoots, projectClasspath, type);
    }

    /**
     * 檢測專案類型
     */
    private ProjectType detectProjectType(Path projectRoot) {
        // 優先檢查建置工具，再檢查 IDE 配置
        if (Files.exists(projectRoot.resolve("build.gradle")) || Files.exists(projectRoot.resolve("settings.gradle"))) {
            return ProjectType.GRADLE;
        }
        if (Files.exists(projectRoot.resolve("pom.xml"))) {
            return ProjectType.MAVEN;
        }
        if (Files.exists(projectRoot.resolve(".project")) && Files.exists(projectRoot.resolve(".classpath"))) {
            return ProjectType.ECLIPSE;
        }
        return ProjectType.UNKNOWN;
    }

    /**
     * 處理 Gradle 專案
     */
    private void processGradleProject(Path gradleProjectRoot, Set<String> sourceRoots, Set<String> projectClasspath)
            throws Exception {
        ProjectConnection connection = null;
        try {
            logger.info("連接到 Gradle 專案: {}", gradleProjectRoot);
            connection = GradleConnector.newConnector()
                    .forProjectDirectory(gradleProjectRoot.toFile())
                    .connect();

            // 檢查是否有 gradlew 可用
            Path gradlewPath = findGradleWrapper(gradleProjectRoot);

            if (gradlewPath != null) {
                logger.info("找到 Gradle Wrapper: {}，使用 gradlew 建置專案", gradlewPath);
                buildWithGradleWrapper(gradleProjectRoot, gradlewPath);
            } else {
                logger.info("未找到 Gradle Wrapper，使用 Gradle Tooling API 建置專案");
                logger.info("執行 Gradle 'build' 任務，這可能需要一些時間...");
                connection.newBuild().forTasks("build").withArguments("-x", "test").run(); // 跳過測試以加快速度
                logger.info("Gradle build 任務完成");
            }

            EclipseProject rootEclipseProject = connection.getModel(EclipseProject.class);
            collectGradleProjectInfo(rootEclipseProject, sourceRoots, projectClasspath);

        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }

    /**
     * 尋找 Gradle Wrapper
     */
    private Path findGradleWrapper(Path projectRoot) {
        // 檢查 gradlew (Unix/Linux/macOS)
        Path gradlew = projectRoot.resolve("gradlew");
        if (Files.exists(gradlew) && Files.isExecutable(gradlew)) {
            return gradlew;
        }

        // 檢查 gradlew.bat (Windows)
        Path gradlewBat = projectRoot.resolve("gradlew.bat");
        if (Files.exists(gradlewBat)) {
            return gradlewBat;
        }

        return null;
    }

    /**
     * 使用 Gradle Wrapper 建置專案
     */
    private void buildWithGradleWrapper(Path projectRoot, Path gradlewPath) throws Exception {
        logger.info("使用 Gradle Wrapper 建置專案: {}", gradlewPath);

        // 準備命令
        List<String> command;
        if (gradlewPath.toString().endsWith(".bat")) {
            command = Arrays.asList(gradlewPath.toString(), "build", "-x", "test");
        } else {
            command = Arrays.asList("./" + gradlewPath.getFileName().toString(), "build", "-x", "test");
        }

        // 設定環境變數
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(projectRoot.toFile());
        pb.redirectErrorStream(true);

        // 設定 Java 環境
        String javaHome = envConfiguration.getJavaHome();
        if (javaHome != null) {
            pb.environment().put("JAVA_HOME", javaHome);
            logger.info("設定 JAVA_HOME 環境變數: {}", javaHome);
        }

        // 設定 Gradle 選項
        String gradleOpts = envConfiguration.getGradleOpts();
        if (gradleOpts != null) {
            pb.environment().put("GRADLE_OPTS", gradleOpts);
            logger.info("設定 GRADLE_OPTS 環境變數: {}", gradleOpts);
        }

        // 執行建置
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logger.debug("[Gradle Wrapper 輸出]: {}", line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            logger.warn("Gradle Wrapper 建置完成，但退出碼為: {}。專案可能未完全建置成功。", exitCode);
            throw new RuntimeException("Gradle Wrapper 建置失敗，退出碼為: " + exitCode);
        } else {
            logger.info("Gradle Wrapper 建置成功完成");
        }
    }

    /**
     * 收集 Gradle 專案信息
     */
    private void collectGradleProjectInfo(EclipseProject eclipseProject, Set<String> sourceRoots,
            Set<String> projectClasspath) {
        // 解析源碼目錄路徑為絕對路徑，這對多專案建置很重要
        eclipseProject.getSourceDirectories().forEach(sourceFolder -> {
            sourceRoots.add(eclipseProject.getProjectDirectory().toPath().resolve(sourceFolder.getPath())
                    .toAbsolutePath().toString());
        });

        eclipseProject.getClasspath().forEach(entry -> {
            if (entry.getFile() != null) {
                projectClasspath.add(entry.getFile().toPath().toAbsolutePath().toString());
            }
        });

        // 遞歸收集子專案信息
        eclipseProject.getChildren().forEach(childProject -> {
            collectGradleProjectInfo(childProject, sourceRoots, projectClasspath);
        });

        // 確保當前模組的編譯類別在 classpath 中
        Path currentModuleBuildClasses = eclipseProject.getProjectDirectory().toPath()
                .resolve("build/classes/java/main");
        if (Files.exists(currentModuleBuildClasses) && Files.isDirectory(currentModuleBuildClasses)) {
            projectClasspath.add(currentModuleBuildClasses.toAbsolutePath().toString());
        }
        Path currentModuleTestBuildClasses = eclipseProject.getProjectDirectory().toPath()
                .resolve("build/classes/java/test");
        if (Files.exists(currentModuleTestBuildClasses) && Files.isDirectory(currentModuleTestBuildClasses)) {
            projectClasspath.add(currentModuleTestBuildClasses.toAbsolutePath().toString());
        }
    }

    /**
     * 處理 Maven 專案
     */
    private void processMavenProject(Path mavenProjectRoot, Set<String> sourceRoots, Set<String> projectClasspath)
            throws Exception {
        logger.info("處理 Maven 專案: {}", mavenProjectRoot);

        // 標準 Maven 源碼和輸出路徑
        Path mainSource = mavenProjectRoot.resolve("src/main/java");
        if (Files.exists(mainSource) && Files.isDirectory(mainSource)) {
            sourceRoots.add(mainSource.toAbsolutePath().toString());
        } else {
            logger.warn("Maven 主源碼目錄未找到: {}", mainSource);
        }

        Path testSource = mavenProjectRoot.resolve("src/test/java");
        if (Files.exists(testSource) && Files.isDirectory(testSource)) {
            sourceRoots.add(testSource.toAbsolutePath().toString());
        } else {
            logger.warn("Maven 測試源碼目錄未找到: {}", testSource);
        }

        Path targetClasses = mavenProjectRoot.resolve("target/classes");
        if (Files.exists(targetClasses) && Files.isDirectory(targetClasses)) {
            projectClasspath.add(targetClasses.toAbsolutePath().toString());
        } else {
            logger.warn("Maven target classes 目錄未找到: {}", targetClasses);
        }

        Path targetTestClasses = mavenProjectRoot.resolve("target/test-classes");
        if (Files.exists(targetTestClasses) && Files.isDirectory(targetTestClasses)) {
            projectClasspath.add(targetTestClasses.toAbsolutePath().toString());
        } else {
            logger.warn("Maven target test-classes 目錄未找到: {}", targetTestClasses);
        }

        // 執行 'mvn dependency:build-classpath' 獲取依賴
        logger.info("執行 'mvn dependency:build-classpath' 收集 Maven 依賴");
        List<String> command = Arrays.asList("mvn", "dependency:build-classpath",
                "-Dmdep.outputFile=target/classpath.txt", "-B");
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(mavenProjectRoot.toFile());
        pb.redirectErrorStream(true);

        // 設定 Java 環境
        String javaHome = envConfiguration.getJavaHome();
        if (javaHome != null) {
            pb.environment().put("JAVA_HOME", javaHome);
            logger.info("設定 JAVA_HOME 環境變數: {}", javaHome);
        }

        Process p = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logger.debug("[Maven 命令輸出]: {}", line);
            }
        }

        int exitCode = p.waitFor();
        if (exitCode != 0) {
            logger.error("Maven 命令 'mvn dependency:build-classpath' 失敗，退出碼: {}。Classpath 可能不完整。", exitCode);
        }

        Path classpathTxtFile = mavenProjectRoot.resolve("target/classpath.txt");
        if (Files.exists(classpathTxtFile)) {
            String fullClasspath = Files.readAllLines(classpathTxtFile).stream().collect(Collectors.joining(""));
            for (String path : fullClasspath.split(File.pathSeparator)) {
                if (!path.trim().isEmpty()) {
                    projectClasspath.add(Paths.get(path).toAbsolutePath().toString());
                }
            }
        } else {
            logger.warn("Maven 命令未生成 target/classpath.txt。Classpath 可能不完整。");
        }
    }

    /**
     * 處理 Eclipse 專案
     */
    private void processEclipseProject(Path eclipseProjectRoot, Set<String> sourceRoots, Set<String> projectClasspath)
            throws Exception {
        logger.info("處理 Eclipse 專案: {}", eclipseProjectRoot);

        Path dotProjectFile = eclipseProjectRoot.resolve(".project");
        Path dotClasspathFile = eclipseProjectRoot.resolve(".classpath");

        if (!Files.exists(dotProjectFile)) {
            throw new IOException("Eclipse .project 檔案未找到: " + dotProjectFile);
        }
        if (!Files.exists(dotClasspathFile)) {
            throw new IOException("Eclipse .classpath 檔案未找到: " + dotClasspathFile);
        }

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document classpathDoc = dBuilder.parse(dotClasspathFile.toFile());
        classpathDoc.getDocumentElement().normalize();

        NodeList classpathEntries = classpathDoc.getElementsByTagName("classpathentry");
        Set<String> processedClasspathEntries = new LinkedHashSet<>();

        for (int i = 0; i < classpathEntries.getLength(); i++) {
            Node node = classpathEntries.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                String kind = element.getAttribute("kind");
                String path = element.getAttribute("path");
                String output = element.getAttribute("output");

                Path resolvedPath = resolvePathVariables(path, eclipseProjectRoot);

                switch (kind) {
                    case "src":
                        if (path.startsWith("/")) {
                            logger.warn("在 .classpath 中找到依賴的 Eclipse 專案 '{}'。此啟動器處理單一專案，其 classpath 不會自動包含。",
                                    path.substring(1));
                        } else {
                            if (Files.exists(resolvedPath) && Files.isDirectory(resolvedPath)) {
                                sourceRoots.add(resolvedPath.toAbsolutePath().toString());
                            } else {
                                logger.warn(".classpath 中指定的源碼路徑不存在: {}", resolvedPath);
                            }
                        }
                        if (!output.isEmpty()) {
                            Path outputPathForSrc = eclipseProjectRoot.resolve(output).toAbsolutePath();
                            if (Files.exists(outputPathForSrc) && Files.isDirectory(outputPathForSrc)) {
                                processedClasspathEntries.add(outputPathForSrc.toString());
                            } else {
                                logger.warn(".classpath 中源碼條目的輸出路徑 '{}' 不存在: {}", output, outputPathForSrc);
                            }
                        }
                        break;
                    case "lib":
                        if (Files.exists(resolvedPath)) {
                            processedClasspathEntries.add(resolvedPath.toAbsolutePath().toString());
                        } else {
                            logger.warn(".classpath 中指定的庫路徑無法解析或不存在: {}", resolvedPath);
                        }
                        break;
                    case "output":
                        if (Files.exists(resolvedPath) && Files.isDirectory(resolvedPath)) {
                            processedClasspathEntries.add(resolvedPath.toAbsolutePath().toString());
                        } else {
                            logger.warn(".classpath 中指定的預設輸出路徑不存在: {}", resolvedPath);
                        }
                        break;
                    case "con":
                        if (path.startsWith("org.eclipse.jdt.launching.JRE_CONTAINER")) {
                            logger.info("處理 JRE 系統庫容器: {}", path);
                            String javaHome = System.getProperty("java.home");
                            Path rtJar = Paths.get(javaHome, "lib", "rt.jar");
                            if (Files.exists(rtJar)) {
                                processedClasspathEntries.add(rtJar.toAbsolutePath().toString());
                            }
                            Path jrtFsJar = Paths.get(javaHome, "lib", "jrt-fs.jar");
                            if (Files.exists(jrtFsJar)) {
                                processedClasspathEntries.add(jrtFsJar.toAbsolutePath().toString());
                            }
                            if (!Files.exists(rtJar) && !Files.exists(jrtFsJar)) {
                                logger.warn("找不到標準 JRE 庫 (rt.jar/jrt-fs.jar)。JRE 系統庫可能不完整。路徑: {}", javaHome);
                            }
                        } else if (path.startsWith("org.eclipse.m2e.MAVEN2_CLASSPATH_CONTAINER")) {
                            logger.info("找到 Maven 依賴容器。嘗試通過 'mvn dependency:build-classpath' 獲取 classpath。");
                            if (Files.exists(eclipseProjectRoot.resolve("pom.xml"))) {
                                try {
                                    Set<String> mavenClasspathTemp = new LinkedHashSet<>();
                                    processMavenProject(eclipseProjectRoot, new LinkedHashSet<>(), mavenClasspathTemp);
                                    processedClasspathEntries.addAll(mavenClasspathTemp);
                                } catch (Exception e) {
                                    logger.error("為 Eclipse 專案收集 Maven 依賴失敗: {}", e.getMessage());
                                }
                            } else {
                                logger.warn("找到 Maven 依賴容器，但 pom.xml 不存在。無法自動解析依賴。");
                            }
                        } else {
                            logger.warn("無法識別的 classpath 容器: {}。其依賴不會自動包含。", path);
                        }
                        break;
                    case "var":
                        logger.debug("忽略 classpath 變數定義: {}", path);
                        break;
                    default:
                        logger.warn("未知的 classpathentry 類型: {} 路徑: {}", kind, path);
                }
            }
        }
        projectClasspath.addAll(processedClasspathEntries);
    }

    /**
     * 解析路徑變數
     */
    private Path resolvePathVariables(String path, Path projectRoot) {
        for (Map.Entry<String, String> entry : ECLIPSE_PATH_VARIABLES.entrySet()) {
            String varName = entry.getKey();
            String varValue = entry.getValue();
            if (path.startsWith(varName + "/")) {
                return Paths.get(varValue, path.substring(varName.length() + 1));
            }
            if (path.equals(varName)) {
                return Paths.get(varValue);
            }
        }
        if (path.startsWith("/")) {
            return Paths.get(path);
        }
        return projectRoot.resolve(path);
    }

    /**
     * 建置結果
     */
    public static class BuildResult {
        private final Set<String> sourceRoots;
        private final Set<String> projectClasspath;
        private final ProjectType projectType;

        public BuildResult(Set<String> sourceRoots, Set<String> projectClasspath, ProjectType projectType) {
            this.sourceRoots = sourceRoots;
            this.projectClasspath = projectClasspath;
            this.projectType = projectType;
        }

        public Set<String> getSourceRoots() {
            return sourceRoots;
        }

        public Set<String> getProjectClasspath() {
            return projectClasspath;
        }

        public ProjectType getProjectType() {
            return projectType;
        }

        public String[] getSourceRootsArray() {
            return sourceRoots.toArray(new String[0]);
        }

        public String[] getProjectClasspathArray() {
            return projectClasspath.toArray(new String[0]);
        }
    }
}
