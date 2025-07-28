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
import java.util.LinkedHashSet; // Use LinkedHashSet to preserve order during deduplication
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

        List<String> sourceRoots = new ArrayList<>();
        List<String> projectClasspath = new ArrayList<>();

        try {
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
                    throw new IllegalStateException("Unexpected project type: " + type);
            }

            logger.info("Invoking AstParserApp for parsing...");
            AstParserApp.main(new String[]{
                String.join(",", sourceRoots.stream().map(root -> projectRoot + "/" + root).collect(Collectors.toList())),
                outputDir.toString(),
                String.join(",", projectClasspath), javaComplianceLevel
            });

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
            connection.newBuild().forTasks("build").run();
            logger.info("Gradle build task completed for {}.", gradleProjectRoot.getFileName());

            EclipseProject rootEclipseProject = connection.getModel(EclipseProject.class);

            collectGradleProjectInfo(rootEclipseProject, sourceRoots, projectClasspath);

            logger.info("Collected {} source roots.", sourceRoots.size());
            sourceRoots.forEach(s -> logger.debug("Source Root: {}", s));
            logger.info("Collected {} classpath entries.", projectClasspath.size());
            projectClasspath.forEach(c -> logger.debug("Classpath Entry: {}", c));

        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }

    private static void collectGradleProjectInfo(EclipseProject eclipseProject, List<String> sourceRoots, List<String> projectClasspath) {
        eclipseProject.getSourceDirectories().forEach(sourceFolder -> {
            sourceRoots.add(sourceFolder.getPath());
        });

        eclipseProject.getClasspath().forEach(entry -> {
            if (entry.getFile() != null) {
                projectClasspath.add(entry.getFile().toPath().toAbsolutePath().toString());
            }
        });

        eclipseProject.getChildren().forEach(childProject -> {
            collectGradleProjectInfo(childProject, sourceRoots, projectClasspath);
        });

        // Ensure current module's compiled classes are also added explicitly (fallback for Gradle)
        Path currentModuleBuildClasses = eclipseProject.getProjectDirectory().toPath()
                .resolve("build/classes/java/main");
        if (Files.exists(currentModuleBuildClasses) && Files.isDirectory(currentModuleBuildClasses)) {
            if (!projectClasspath.contains(currentModuleBuildClasses.toAbsolutePath().toString())) {
                projectClasspath.add(currentModuleBuildClasses.toAbsolutePath().toString());
            }
        }
        Path currentModuleTestBuildClasses = eclipseProject.getProjectDirectory().toPath()
                .resolve("build/classes/java/test");
        if (Files.exists(currentModuleTestBuildClasses) && Files.isDirectory(currentModuleTestBuildClasses)) {
            if (!projectClasspath.contains(currentModuleTestBuildClasses.toAbsolutePath().toString())) {
                projectClasspath.add(currentModuleTestBuildClasses.toAbsolutePath().toString());
            }
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
        // This requires Maven to be installed and in PATH.
        logger.info("Executing 'mvn dependency:build-classpath' to collect Maven dependencies.");
        List<String> command = Arrays.asList("mvn", "dependency:build-classpath", "-Dmdep.outputFile=target/classpath.txt", "-B"); // -B for batch mode (no user interaction)
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

        // Read from generated classpath.txt file (always generated if mvn command runs)
        Path classpathTxtFile = mavenProjectRoot.resolve("target/classpath.txt");
        if (Files.exists(classpathTxtFile)) {
            // Read the content of classpath.txt, which is a single line
            String fullClasspath = Files.readAllLines(classpathTxtFile).stream().collect(Collectors.joining(""));
            for (String path : fullClasspath.split(File.pathSeparator)) {
                if (!path.trim().isEmpty()) {
                    projectClasspath.add(Paths.get(path).toAbsolutePath().toString());
                }
            }
        } else {
            logger.warn("Maven command did not generate target/classpath.txt. Classpath may be incomplete.");
        }
        // Remove duplicates and preserve order
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
        Set<String> processedClasspathEntries = new LinkedHashSet<>(); // Use LinkedHashSet for order and deduplication

        for (int i = 0; i < classpathEntries.getLength(); i++) {
            Node node = classpathEntries.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                String kind = element.getAttribute("kind");
                String path = element.getAttribute("path");
                String output = element.getAttribute("output"); // Output attribute for 'src' entries

                // Resolve path variables (like M2_REPO)
                Path resolvedPath = resolvePathVariables(path, eclipseProjectRoot);

                switch (kind) {
                    case "src": // Source folder or project dependency
                        if (path.startsWith("/")) { // Project dependency (e.g., /MyDependentProject)
                            String dependentProjectName = path.substring(1);
                            logger.warn("Dependent Eclipse project '{}' found in .classpath. This launcher currently only processes a single project at a time. Its classpath won't be included automatically.", dependentProjectName);
                            // To handle this, you would need to implement workspace scanning and recursive parsing.
                        } else { // Source folder (relative path)
                            if (Files.exists(resolvedPath) && Files.isDirectory(resolvedPath)) {
                                sourceRoots.add(resolvedPath.toAbsolutePath().toString());
                            } else {
                                logger.warn("Source path specified in .classpath does not exist: {}", resolvedPath);
                            }
                        }
                        // Add output folder for this source entry if specified
                        if (!output.isEmpty()) {
                            Path outputPathForSrc = eclipseProjectRoot.resolve(output).toAbsolutePath();
                            if (Files.exists(outputPathForSrc) && Files.isDirectory(outputPathForSrc)) {
                                processedClasspathEntries.add(outputPathForSrc.toString());
                            } else {
                                logger.warn("Output path '{}' for source entry in .classpath does not exist: {}", output, outputPathForSrc);
                            }
                        }
                        break;
                    case "lib": // External library JAR or folder
                        if (Files.exists(resolvedPath) && (Files.isRegularFile(resolvedPath) || Files.isDirectory(resolvedPath))) {
                            processedClasspathEntries.add(resolvedPath.toAbsolutePath().toString());
                        } else {
                            logger.warn("Library path specified in .classpath could not be resolved or does not exist: {}", resolvedPath);
                        }
                        break;
                    case "output": // Default project output folder (e.g., bin/)
                        if (Files.exists(resolvedPath) && Files.isDirectory(resolvedPath)) {
                            processedClasspathEntries.add(resolvedPath.toAbsolutePath().toString());
                        } else {
                            logger.warn("Default output path specified in .classpath does not exist: {}", resolvedPath);
                        }
                        break;
                    case "con": // Classpath container (e.g., JRE System Library, Maven Dependencies)
                        if (path.startsWith("org.eclipse.jdt.launching.JRE_CONTAINER")) {
                            logger.info("Handling JRE System Library container: {}", path);
                            // JDT's setEnvironment implicitly picks up the running JRE's standard libraries.
                            // Explicitly adding JRE_CONTAINER paths for JDT is generally not strictly required
                            // unless you need specific JRE versions or modules not in the running JRE.
                            // However, if we must list paths, we try best effort for common JRE structures.
                            String javaHome = System.getProperty("java.home");
                            // Java 8: rt.jar (or similar, depends on vendor)
                            Path rtJar = Paths.get(javaHome, "lib", "rt.jar");
                            if (Files.exists(rtJar)) {
                                processedClasspathEntries.add(rtJar.toAbsolutePath().toString());
                            }
                            // Java 9+: modules in jmods, accessed via jrt-fs.jar
                            Path jrtFsJar = Paths.get(javaHome, "lib", "jrt-fs.jar"); // This file enables JRT filesystem access
                            if (Files.exists(jrtFsJar)) {
                                processedClasspathEntries.add(jrtFsJar.toAbsolutePath().toString());
                                // Also add jmods directory for modules (JDT needs actual module paths)
                                Path jmodsDir = Paths.get(javaHome, "jmods");
                                if (Files.exists(jmodsDir) && Files.isDirectory(jmodsDir)) {
                                    try (Stream<Path> jmodFiles = Files.walk(jmodsDir, 1)) { // Only direct children
                                        jmodFiles.filter(p -> p.toString().endsWith(".jmod"))
                                                 .map(Path::toAbsolutePath)
                                                 .map(Path::toString)
                                                 .forEach(processedClasspathEntries::add);
                                    }
                                }
                            }
                            if (!Files.exists(rtJar) && !Files.exists(jrtFsJar)) {
                                logger.warn("Could not find standard JRE libraries (rt.jar/jrt-fs.jar). JRE System Library may be incomplete. Path: {}", javaHome);
                            }

                        } else if (path.startsWith("org.eclipse.m2e.MAVEN2_CLASSPATH_CONTAINER")) {
                            logger.info("Maven dependencies container found for Eclipse project. Attempting to get classpath via 'mvn dependency:build-classpath'.");
                            // If it's a Maven-enabled Eclipse project, use Maven logic
                            if (Files.exists(eclipseProjectRoot.resolve("pom.xml"))) {
                                try {
                                    // Temporarily pass a new list/set for Maven projectClasspath to avoid mixing
                                    List<String> mavenClasspathTemp = new ArrayList<>();
                                    processMavenProject(eclipseProjectRoot, new ArrayList<>(), mavenClasspathTemp);
                                    processedClasspathEntries.addAll(mavenClasspathTemp);
                                } catch (Exception e) {
                                    logger.error("Failed to collect Maven dependencies for Eclipse project: {}", e.getMessage());
                                }
                            } else {
                                logger.warn("Maven dependencies container found, but pom.xml not present in Eclipse project root. Cannot automatically resolve Maven dependencies.");
                                if (ECLIPSE_PATH_VARIABLES.containsKey("M2_REPO")) {
                                    logger.info("Adding M2_REPO to classpath as fallback: {}", ECLIPSE_PATH_VARIABLES.get("M2_REPO"));
                                    processedClasspathEntries.add(ECLIPSE_PATH_VARIABLES.get("M2_REPO"));
                                }
                            }
                        } else {
                            logger.warn("Unrecognized classpath container: {}. Its dependencies won't be included automatically.", path);
                            // If it points to a resolved variable and exists, add it.
                            if (Files.exists(resolvedPath)) {
                                processedClasspathEntries.add(resolvedPath.toAbsolutePath().toString());
                            }
                        }
                        break;
                    case "var": // Variable definition (e.g., path="MY_VAR_NAME")
                        // These are typically resolved when used in other paths.
                        // We don't need to add them to classpath themselves.
                        logger.debug("Ignoring classpath variable definition: {}", path);
                        break;
                    default:
                        logger.warn("Unknown classpathentry kind: {} with path: {}", kind, path);
                }
            }
        }
        projectClasspath.addAll(processedClasspathEntries); // Add all unique, ordered entries

        logger.info("Collected {} source roots for Eclipse project.", sourceRoots.size());
        sourceRoots.forEach(s -> logger.debug("Source Root: {}", s));
        logger.info("Collected {} classpath entries for Eclipse project.", projectClasspath.size());
        projectClasspath.forEach(c -> logger.debug("Classpath Entry: {}", c));
    }

    /**
     * Resolves Eclipse path variables (like M2_REPO) and absolute/relative paths.
     * @param path The path string from .classpath.
     * @param projectRoot The root directory of the Eclipse project.
     * @return The resolved absolute path.
     */
    private static Path resolvePathVariables(String path, Path projectRoot) {
        // Handle common Eclipse-style variables like FOO_VAR/path/to/file.jar
        for (Map.Entry<String, String> entry : ECLIPSE_PATH_VARIABLES.entrySet()) {
            String varName = entry.getKey();
            String varValue = entry.getValue();
            if (path.startsWith(varName + "/")) {
                return Paths.get(varValue, path.substring(varName.length() + 1));
            }
            if (path.equals(varName)) { // For simple variable references like path="M2_REPO"
                return Paths.get(varValue);
            }
        }
        // Handle workspace-relative paths starting with '/' (e.g., /MyProject/lib/foo.jar)
        // For now, we treat them as absolute if they start with /, as this launcher is single-project
        if (path.startsWith("/")) {
            // This is a simplification. In a multi-project workspace, this would need the workspace root.
            return Paths.get(path);
        }
        // Assume it's relative to the current project's root
        return projectRoot.resolve(path);
    }
}