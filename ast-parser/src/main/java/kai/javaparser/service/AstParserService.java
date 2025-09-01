package kai.javaparser.service;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.core.JavaCore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import kai.javaparser.AstExtractor;
import kai.javaparser.util.Util;

/**
 * AST解析服務類
 * 提供Java源碼的AST解析功能
 */
@Service
public class AstParserService {

    private static final Logger logger = LoggerFactory.getLogger(AstParserService.class);

    /**
     * 執行AST解析
     * 
     * @param baseFolder          基礎文件夾
     * @param sourceRootDirsArg   源碼根目錄（逗號分隔）
     * @param outputBaseDir       輸出目錄
     * @param classpathArg        類路徑（逗號分隔）
     * @param javaComplianceLevel Java合規性級別
     * @return 解析結果信息
     */
    public String executeAstParsing(String baseFolder, String sourceRootDirsArg, String outputBaseDir,
            String classpathArg, String javaComplianceLevel) {
        Path outputBaseDir0 = Paths.get(outputBaseDir);

        Set<Path> sourceRoots = Stream.of(sourceRootDirsArg.split(","))
                .map(Paths::get)
                .collect(Collectors.toSet());

        List<String> projectSourcesList = new ArrayList<>();
        List<String> projectClasspathList = new ArrayList<>();

        for (Path sourceRoot : sourceRoots) {
            if (!Files.exists(sourceRoot) || !Files.isDirectory(sourceRoot)) {
                logger.error("Source directory does not exist or is not a directory: {}", sourceRoot);
                return "Error: Source directory does not exist or is not a directory: " + sourceRoot;
            }
            projectSourcesList.add(sourceRoot.toAbsolutePath().toString());
        }

        // Parse the new classpath argument
        Stream.of(classpathArg.split(","))
                .filter(s -> !s.trim().isEmpty())
                .map(s -> Paths.get(s).toAbsolutePath().toString())
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
            Files.createDirectories(outputBaseDir0);
        } catch (IOException e) {
            logger.error("Could not create output directory {}: {}", outputBaseDir0, e.getMessage());
            return "Error: Could not create output directory: " + e.getMessage();
        }

        int totalFiles = 0;
        final int[] processedFiles = { 0 };

        for (Path sourceRoot : sourceRoots) {
            try (Stream<Path> paths = Files.walk(sourceRoot)) {
                List<Path> javaFiles = paths
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".java"))
                        .collect(Collectors.toList());

                logger.info("Found {} Java files in {}", javaFiles.size(), sourceRoot);
                totalFiles += javaFiles.size();

                // Determine a unique prefix for files from this source root
                String uniquePrefix = sourceRoot.toAbsolutePath().toString().replace(baseFolder, "").replace("/", "_");

                javaFiles.parallelStream()
                        .map(path -> astExtractor.parseJavaFile(path, projectSources, projectClasspath,
                                javaComplianceLevel))
                        .forEach(fileAstData -> {
                            if (fileAstData != null) {
                                Path relativePath = sourceRoot.relativize(Paths.get(fileAstData.getAbsolutePath()));
                                fileAstData.setRelativePath(relativePath.toString());

                                // Construct a unique output filename
                                Path outputFile0 = outputBaseDir0.resolve(uniquePrefix)
                                        .resolve(relativePath.toString());
                                Path outputFile = outputBaseDir0.resolve(uniquePrefix)
                                        .resolve(relativePath.toString().replace(".java", ".json"));
                                try {
                                    Files.createDirectories(outputFile.getParent());
                                    Util.writeJson(outputFile, fileAstData);

                                    Files.writeString(outputFile0, new String(fileAstData.getFileContent()),
                                            Charset.forName("UTF-8"));

                                    processedFiles[0]++;

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

        return String.format("AST parsing completed successfully. Processed %d/%d files. Output saved to: %s",
                processedFiles[0], totalFiles, outputBaseDir0.toAbsolutePath());
    }

    /**
     * 簡化版本的AST解析，使用默認參數
     * 
     * @param sourceRoot 源碼根目錄
     * @param outputDir  輸出目錄
     * @return 解析結果信息
     */
    public String parseSourceDirectory(String sourceRoot, String outputDir) {
        String baseFolder = Paths.get(sourceRoot).getParent().toString();
        String classpath = "";
        String javaLevel = JavaCore.VERSION_17;

        return executeAstParsing(baseFolder, sourceRoot, outputDir, classpath, javaLevel);
    }
}
