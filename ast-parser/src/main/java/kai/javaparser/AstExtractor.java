package kai.javaparser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kai.javaparser.model.FileAstData;
import kai.javaparser.model.SequenceDiagramData;

public class AstExtractor {

    private static final Logger logger = LoggerFactory.getLogger(AstExtractor.class);

    /**
     * Parses a single Java file and extracts its AST data.
     *
     * @param sourceFilePath   The absolute path to the Java source file.
     * @param projectSources   Array of absolute paths to source directories for the
     *                         project.
     * @param projectClasspath Array of absolute paths to JARs or class directories
     *                         for the project's dependencies.
     * @param complianceLevel  Java compliance level (e.g., JavaCore.VERSION_17).
     * @return FileAstData containing the parsed AST information, or null if parsing
     *         fails.
     */
    public FileAstData parseJavaFile(Path sourceFilePath, String[] projectSources, String[] projectClasspath,
            String complianceLevel) {
        try {
            String fileContent = Files.readString(sourceFilePath);
            char[] fileContentChars = fileContent.toCharArray();

            ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
            parser.setSource(fileContentChars);
            parser.setKind(ASTParser.K_COMPILATION_UNIT);
            parser.setResolveBindings(true);
            parser.setBindingsRecovery(true);
            parser.setStatementsRecovery(true);
            parser.setEnvironment(projectClasspath, projectSources, null, true);

            Map<String, String> options = JavaCore.getOptions();
            JavaCore.setComplianceOptions(complianceLevel, options);
            parser.setCompilerOptions(options);
            parser.setUnitName(sourceFilePath.getFileName().toString());

            CompilationUnit cu = (CompilationUnit) parser.createAST(null);

            // 檢查解析問題
            for (IProblem problem : cu.getProblems()) {
                if (problem.isError()) {
                    logger.warn("解析問題 {}: {}", sourceFilePath, problem.getMessage());
                }
            }

            SequenceDiagramData sequenceData = new SequenceDiagramData();

            // 設置入口方法（假設是第一個public方法）
            String fqn = findClassFqn(cu);
            sequenceData.setClassFqn(fqn);

            // 使用自定義訪問者提取互動
            cu.accept(new EnhancedInteractionModelVisitor(sequenceData, cu));

            FileAstData fileAstData = new FileAstData();
            fileAstData.setPackageName(cu.getPackage().getName().getFullyQualifiedName());
            fileAstData.setFileContent(fileContentChars);
            fileAstData.setRelativePath(sourceFilePath.getFileName().toString());
            fileAstData.setAbsolutePath(sourceFilePath.toAbsolutePath().toString());
            fileAstData.setSequenceDiagramData(sequenceData);
            @SuppressWarnings("unchecked")
            List<String> imports = ((List<ImportDeclaration>) cu.imports()).stream()
                    .map(i -> i.getName().getFullyQualifiedName())
                    .collect(Collectors.toList());
            fileAstData.setImports(imports);

            return fileAstData;

        } catch (IOException e) {
            logger.error("讀取文件錯誤 {}: {}", sourceFilePath, e.getMessage());
            return null;
        } catch (Exception e) {
            logger.error("解析文件時發生意外錯誤 {}: {}", sourceFilePath, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 尋找入口方法（第一個public方法）
     */
    private String findClassFqn(CompilationUnit cu) {
        String packageName = cu.getPackage() != null ? cu.getPackage().getName().getFullyQualifiedName() : "";

        for (Object typeDecl : cu.types()) {
            if (typeDecl instanceof TypeDeclaration) {
                TypeDeclaration type = (TypeDeclaration) typeDecl;
                String className = type.getName().getIdentifier();
                return packageName + "." + className;
            }
        }
        return "unknown";
    }

}