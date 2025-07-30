package com.yourcompany.parser;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet; // Use LinkedHashSet to preserve order and handle duplicates
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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AstParserLauncher {

    private static final Logger logger = LoggerFactory.getLogger(AstParserLauncher.class);

    // Configurable Eclipse Path Variables. In a production environment, these would be externalized.
    // For now, these are best guesses or common defaults.
    private static final Map<String, String> ECLIPSE_PATH_VARIABLES = Map.of(
        "M2_REPO", System.getProperty("user.home") + File.separator + ".m2" + File.separator + "repository"
        // JRE_CONTAINER is handled dynamically based on current JRE, not via this map.
        // Add other custom variables if needed, e.g., "MY_CUSTOM_LIB", "/opt/my_lib"
    );

    private enum ProjectType {
        GRADLE, MAVEN, ECLIPSE, UNKNOWN
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java -jar ast-parser.jar <project_root_dir> <output_dir> [java_compliance_level]");
            System.out.println("Example: java -jar ast-parser.jar /path/to/your/app /path/to/output_asts 17");
            return;
        }

        Path projectRoot = Paths.get(args[0]).toAbsolutePath();
        Path outputDir = Paths.get(args[1]).toAbsolutePath();
        String javaComplianceLevel = args.length > 2 ? args[2] : "17";

        if (!Files.exists(projectRoot) || !Files.isDirectory(projectRoot)) {
            logger.error("Project root directory does not exist or is not a directory: {}", projectRoot);
            return;
        }

        ProjectType type = detectProjectType(projectRoot);
        if (type == ProjectType.UNKNOWN) {
            logger.error("Could not determine project type for {}. Please ensure it's a Gradle, Maven, or Eclipse project.", projectRoot);
            return;
        }
        logger.info("Detected project type: {}", type);

        // Use LinkedHashSet to avoid duplicates while preserving order
        Set<String> sourceRoots = new LinkedHashSet<>();
        Set<String> projectClasspath = new LinkedHashSet<>();

        try {
            switch (type) {
                case GRADLE:
                    List<String> gradleSourceRoots = new ArrayList<>();
                    List<String> gradleProjectClasspath = new ArrayList<>();
                    processGradleProject(projectRoot, gradleSourceRoots, gradleProjectClasspath);
                    sourceRoots.addAll(gradleSourceRoots);
                    projectClasspath.addAll(gradleProjectClasspath);
                    break;
                case MAVEN:
                    List<String> mavenSourceRoots = new ArrayList<>();
                    List<String> mavenProjectClasspath = new ArrayList<>();
                    processMavenProject(projectRoot, mavenSourceRoots, mavenProjectClasspath);
                    sourceRoots.addAll(mavenSourceRoots);
                    projectClasspath.addAll(mavenProjectClasspath);
                    break;
                case ECLIPSE:
                    List<String> eclipseSourceRoots = new ArrayList<>();
                    List<String> eclipseProjectClasspath = new ArrayList<>();
                    processEclipseProject(projectRoot, eclipseSourceRoots, eclipseProjectClasspath);
                    sourceRoots.addAll(eclipseSourceRoots);
                    projectClasspath.addAll(eclipseProjectClasspath);
                    break;
                default:
                    throw new IllegalStateException("Unexpected project type: " + type);
            }

            logger.info("Invoking AstParserApp for parsing...");
            AstParserApp instance = new AstParserApp();
            instance.execute(
                projectRoot.toString(),
                String.join(",", sourceRoots), // CORRECTED: Pass the comma-separated list of absolute source paths
                outputDir.toString(),
                String.join(",", projectClasspath), 
                javaComplianceLevel
            );

            logger.info("Parsing completed successfully for project type {}.", type);

        } catch (Exception e) {
            logger.error("Error during AST parsing automation: {}", e.getMessage(), e);
        }
    }

    private static ProjectType detectProjectType(Path projectRoot) {
        // Prioritize build tools over IDE config if both exist
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

    // --- Gradle Project Processing ---
    private static void processGradleProject(Path gradleProjectRoot, List<String> sourceRoots, List<String> projectClasspath) throws Exception {
        ProjectConnection connection = null;
        try {
            logger.info("Connecting to Gradle project: {}", gradleProjectRoot);
            connection = GradleConnector.newConnector()
                    .forProjectDirectory(gradleProjectRoot.toFile())
                    .connect();

            logger.info("Executing Gradle 'build' task on {}. This may take some time...", gradleProjectRoot.getFileName());
            connection.newBuild().forTasks("build").withArguments("-x", "test").run(); // Skip tests to speed up
            logger.info("Gradle build task completed for {}.", gradleProjectRoot.getFileName());

            EclipseProject rootEclipseProject = connection.getModel(EclipseProject.class);

            collectGradleProjectInfo(rootEclipseProject, sourceRoots, projectClasspath);

            logger.info("Collected {} unique source roots.", sourceRoots.size());
            sourceRoots.forEach(s -> logger.debug("Source Root: {}", s));
            logger.info("Collected {} unique classpath entries.", projectClasspath.size());
            projectClasspath.forEach(c -> logger.debug("Classpath Entry: {}", c));

        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }

    private static void collectGradleProjectInfo(EclipseProject eclipseProject, List<String> sourceRoots, List<String> projectClasspath) {
        // FIXED: Resolve source directory paths to be absolute, which is crucial for multi-project builds.
        eclipseProject.getSourceDirectories().forEach(sourceFolder -> {
            sourceRoots.add(eclipseProject.getProjectDirectory().toPath().resolve(sourceFolder.getPath()).toAbsolutePath().toString());
        });

        eclipseProject.getClasspath().forEach(entry -> {
            if (entry.getFile() != null) {
                projectClasspath.add(entry.getFile().toPath().toAbsolutePath().toString());
            }
        });
        
        // Recursively collect info from child projects
        eclipseProject.getChildren().forEach(childProject -> {
            collectGradleProjectInfo(childProject, sourceRoots, projectClasspath);
        });

        // Fallback to ensure the compiled classes of the current module are on the classpath.
        // This is important for resolving dependencies between modules in the same project.
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

    // --- Maven Project Processing ---
    private static void processMavenProject(Path mavenProjectRoot, List<String> sourceRoots, List<String> projectClasspath) throws Exception {
        logger.info("Processing Maven project: {}", mavenProjectRoot);

        // Standard Maven source and output paths
        Path mainSource = mavenProjectRoot.resolve("src/main/java");
        if (Files.exists(mainSource) && Files.isDirectory(mainSource)) {
            sourceRoots.add(mainSource.toAbsolutePath().toString());
        } else {
            logger.warn("Maven main source directory not found: {}", mainSource);
        }

        Path testSource = mavenProjectRoot.resolve("src/test/java");
        if (Files.exists(testSource) && Files.isDirectory(testSource)) {
            sourceRoots.add(testSource.toAbsolutePath().toString());
        } else {
            logger.warn("Maven test source directory not found: {}", testSource);
        }

        Path targetClasses = mavenProjectRoot.resolve("target/classes");
        if (Files.exists(targetClasses) && Files.isDirectory(targetClasses)) {
            projectClasspath.add(targetClasses.toAbsolutePath().toString());
        } else {
            logger.warn("Maven target classes directory not found: {}", targetClasses);
        }

        Path targetTestClasses = mavenProjectRoot.resolve("target/test-classes");
        if (Files.exists(targetTestClasses) && Files.isDirectory(targetTestClasses)) {
            projectClasspath.add(targetTestClasses.toAbsolutePath().toString());
        } else {
            logger.warn("Maven target test classes directory not found: {}", targetTestClasses);
        }

        // --- Execute 'mvn dependency:build-classpath' to get dependencies ---
        logger.info("Executing 'mvn dependency:build-classpath' to collect Maven dependencies.");
        List<String> command = Arrays.asList("mvn", "dependency:build-classpath", "-Dmdep.outputFile=target/classpath.txt", "-B");
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(mavenProjectRoot.toFile());
        pb.redirectErrorStream(true);

        Process p = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logger.debug("[Maven Command Output]: {}", line);
            }
        }

        int exitCode = p.waitFor();
        if (exitCode != 0) {
            logger.error("Maven command 'mvn dependency:build-classpath' failed with exit code {}. Classpath may be incomplete.", exitCode);
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
            logger.warn("Maven command did not generate target/classpath.txt. Classpath may be incomplete.");
        }
        
        projectClasspath.clear();
        projectClasspath.addAll(new LinkedHashSet<>(projectClasspath));

        logger.info("Collected {} source roots for Maven project.", sourceRoots.size());
        sourceRoots.forEach(s -> logger.debug("Source Root: {}", s));
        logger.info("Collected {} classpath entries for Maven project.", projectClasspath.size());
        projectClasspath.forEach(c -> logger.debug("Classpath Entry: {}", c));
    }


    // --- Eclipse Project Processing ---
    private static void processEclipseProject(Path eclipseProjectRoot, List<String> sourceRoots, List<String> projectClasspath) throws Exception {
        logger.info("Processing Eclipse project: {}", eclipseProjectRoot);

        Path dotProjectFile = eclipseProjectRoot.resolve(".project");
        Path dotClasspathFile = eclipseProjectRoot.resolve(".classpath");

        if (!Files.exists(dotProjectFile)) {
            throw new IOException("Eclipse .project file not found at: " + dotProjectFile);
        }
        if (!Files.exists(dotClasspathFile)) {
            throw new IOException("Eclipse .classpath file not found at: " + dotClasspathFile);
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
                            logger.warn("Dependent Eclipse project '{}' found in .classpath. This launcher processes a single project. Its classpath won't be included automatically.", path.substring(1));
                        } else {
                            if (Files.exists(resolvedPath) && Files.isDirectory(resolvedPath)) {
                                sourceRoots.add(resolvedPath.toAbsolutePath().toString());
                            } else {
                                logger.warn("Source path specified in .classpath does not exist: {}", resolvedPath);
                            }
                        }
                        if (!output.isEmpty()) {
                            Path outputPathForSrc = eclipseProjectRoot.resolve(output).toAbsolutePath();
                            if (Files.exists(outputPathForSrc) && Files.isDirectory(outputPathForSrc)) {
                                processedClasspathEntries.add(outputPathForSrc.toString());
                            } else {
                                logger.warn("Output path '{}' for source entry in .classpath does not exist: {}", output, outputPathForSrc);
                            }
                        }
                        break;
                    case "lib":
                        if (Files.exists(resolvedPath)) {
                            processedClasspathEntries.add(resolvedPath.toAbsolutePath().toString());
                        } else {
                            logger.warn("Library path specified in .classpath could not be resolved or does not exist: {}", resolvedPath);
                        }
                        break;
                    case "output":
                        if (Files.exists(resolvedPath) && Files.isDirectory(resolvedPath)) {
                            processedClasspathEntries.add(resolvedPath.toAbsolutePath().toString());
                        } else {
                            logger.warn("Default output path specified in .classpath does not exist: {}", resolvedPath);
                        }
                        break;
                    case "con":
                        if (path.startsWith("org.eclipse.jdt.launching.JRE_CONTAINER")) {
                            logger.info("Handling JRE System Library container: {}", path);
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
                                logger.warn("Could not find standard JRE libraries (rt.jar/jrt-fs.jar). JRE System Library may be incomplete. Path: {}", javaHome);
                            }
                        } else if (path.startsWith("org.eclipse.m2e.MAVEN2_CLASSPATH_CONTAINER")) {
                            logger.info("Maven dependencies container found. Attempting to get classpath via 'mvn dependency:build-classpath'.");
                            if (Files.exists(eclipseProjectRoot.resolve("pom.xml"))) {
                                try {
                                    List<String> mavenClasspathTemp = new ArrayList<>();
                                    processMavenProject(eclipseProjectRoot, new ArrayList<>(), mavenClasspathTemp);
                                    processedClasspathEntries.addAll(mavenClasspathTemp);
                                } catch (Exception e) {
                                    logger.error("Failed to collect Maven dependencies for Eclipse project: {}", e.getMessage());
                                }
                            } else {
                                logger.warn("Maven dependencies container found, but pom.xml not present. Cannot automatically resolve dependencies.");
                            }
                        } else {
                            logger.warn("Unrecognized classpath container: {}. Its dependencies won't be included automatically.", path);
                        }
                        break;
                    case "var":
                        logger.debug("Ignoring classpath variable definition: {}", path);
                        break;
                    default:
                        logger.warn("Unknown classpathentry kind: {} with path: {}", kind, path);
                }
            }
        }
        projectClasspath.addAll(processedClasspathEntries);

        logger.info("Collected {} source roots for Eclipse project.", sourceRoots.size());
        sourceRoots.forEach(s -> logger.debug("Source Root: {}", s));
        logger.info("Collected {} classpath entries for Eclipse project.", projectClasspath.size());
        projectClasspath.forEach(c -> logger.debug("Classpath Entry: {}", c));
    }

    private static Path resolvePathVariables(String path, Path projectRoot) {
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
}