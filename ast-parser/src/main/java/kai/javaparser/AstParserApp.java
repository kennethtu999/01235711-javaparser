package kai.javaparser;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays; // Needed for Arrays.toString in logging
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.core.JavaCore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kai.javaparser.util.Util;

public class AstParserApp {

    private static final Logger logger = LoggerFactory.getLogger(AstParserApp.class);
    
    public static void main(String[] args) {
        if (args.length < 4) { // Now expecting at least 3 arguments: sourceRoots, outputDir, classpath
            System.out.println("Usage: java -jar java-ast-parser.jar <base_folder> <source_root_dir1,...> <output_dir> <classpath_item1,...> [java_compliance_level]");
            System.out.println("Example: java -jar java-ast-parser.jar /path/to/base/folder /path/to/src/main/java /path/to/output /path/to/classes,/path/to/lib.jar 17");
            System.out.println("Note: <classpath_item1,...> should be comma-separated absolute paths to JARs or compiled class directories.");
            return;
        }

        String baseFolder = args[0];
        String sourceRootDirsArg = args[1];
        String outputBaseDir = args[2];
        String classpathArg = args[3]; // New argument for classpath
        String javaComplianceLevel = args.length > 3 ? args[3] : JavaCore.VERSION_17; // Default to Java 17


        AstParserApp instance = new AstParserApp();
        instance.execute(baseFolder, sourceRootDirsArg, outputBaseDir, classpathArg, javaComplianceLevel);
    }

    /**
     * Execute the AST parsing for the given arguments.
     * @param baseFolder
     * @param sourceRootDirsArg
     * @param outputBaseDir
     * @param classpathArg
     * @param javaComplianceLevel
     */
    public void execute(String baseFolder, String sourceRootDirsArg, String outputBaseDir, String classpathArg, String javaComplianceLevel) {
        Path outputBaseDir0 = Paths.get(outputBaseDir);

        Set<Path> sourceRoots = Stream.of(sourceRootDirsArg.split(","))
                .map(Paths::get)
                .collect(Collectors.toSet());

        List<String> projectSourcesList = new ArrayList<>();
        List<String> projectClasspathList = new ArrayList<>();

        for (Path sourceRoot : sourceRoots) {
            if (!Files.exists(sourceRoot) || !Files.isDirectory(sourceRoot)) {
                logger.error("Source directory does not exist or is not a directory: {}", sourceRoot);
                // Decide if you want to exit here or just skip this source root.
                // For a robust app, you might collect errors and continue. For now, we return.
                return;
            }
            projectSourcesList.add(sourceRoot.toAbsolutePath().toString());
        }

        // Parse the new classpath argument
        Stream.of(classpathArg.split(","))
              .filter(s -> !s.trim().isEmpty()) // Filter out empty strings if multiple commas
              .map(s -> Paths.get(s).toAbsolutePath().toString()) // Resolve to absolute path
              .forEach(projectClasspathList::add);

        // Convert lists to arrays for JDT Extractor
        String[] projectSources = projectSourcesList.toArray(new String[0]);
        String[] projectClasspath = projectClasspathList.toArray(new String[0]);

        logger.info("Starting AST parsing for source roots: {}", sourceRoots);
        logger.info("Project source paths for JDT: {}", Arrays.toString(projectSources));
        logger.info("Project classpath for JDT (JARs/classes): {}", Arrays.toString(projectClasspath));
        logger.info("Java compliance level: {}", javaComplianceLevel);
        logger.info("Output directory: {}", outputBaseDir0.toAbsolutePath());

        AstExtractor astExtractor = new AstExtractor();

        try {
            Files.createDirectories(outputBaseDir0); // Ensure output directory exists
        } catch (IOException e) {
            logger.error("Could not create output directory {}: {}", outputBaseDir0, e.getMessage());
            return;
        }

        for (Path sourceRoot : sourceRoots) {
            try (Stream<Path> paths = Files.walk(sourceRoot)) {
                List<Path> javaFiles = paths
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".java"))
                        .collect(Collectors.toList());

                logger.info("Found {} Java files in {}", javaFiles.size(), sourceRoot);

                // Determine a unique prefix for files from this source root
                // Use a hash of the absolute path to avoid conflicts for same-named files from different roots
                String uniquePrefix = sourceRoot.toAbsolutePath().toString().replace(baseFolder, "").replace("/", "_");

                javaFiles.parallelStream()
                .map(path -> astExtractor.parseJavaFile(path, projectSources, projectClasspath, javaComplianceLevel))
                .forEach(fileAstData -> {
                    if (fileAstData != null) {
                        Path relativePath = sourceRoot.relativize(Paths.get(fileAstData.getAbsolutePath()));
                        fileAstData.setRelativePath(relativePath.toString()); // Set relative path for output
                        
                        // Construct a unique output filename in a subdirectory specific to the sourceRoot
                        Path outputFile0 = outputBaseDir0.resolve(uniquePrefix) // Add uniquePrefix as a subdirectory
                                                      .resolve(relativePath.toString());
                        Path outputFile = outputBaseDir0.resolve(uniquePrefix) // Add uniquePrefix as a subdirectory
                                                      .resolve(relativePath.toString().replace(".java", ".json"));
                        try {
                            Files.createDirectories(outputFile.getParent()); // Ensure parent directories exist
                            Util.writeJson(outputFile, fileAstData);
                            
                            Files.writeString(outputFile0, new String(fileAstData.getFileContent()), Charset.forName("UTF-8"));
                        
                        } catch (IOException e) {
                            logger.error("Error writing AST to {}: {}", outputFile, e.getMessage());
                        }
                    }
                });

            } catch (IOException e) {
                logger.error("Error walking source directory {}: {}", sourceRoot, e.getMessage());
            }
        }

        logger.info("--- Parsing Summary ---");
        logger.info("Output saved to: {}", outputBaseDir0.toAbsolutePath());
    }
}