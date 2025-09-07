package kai.javaparser.java2ast;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kai.javaparser.model.AnnotationInfo;
import kai.javaparser.model.FieldInfo;
import kai.javaparser.model.FileAstData;
import kai.javaparser.model.SequenceDiagramData;
import kai.javaparser.util.AnnotationExtractor;

public class JavaToAstFile {

    private static final Logger logger = LoggerFactory.getLogger(JavaToAstFile.class);

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

            // 提取類別級別的註解
            extractClassAnnotations(cu, sequenceData);

            // 使用自定義訪問者提取互動
            cu.accept(new EnhancedInteractionModelVisitor(sequenceData, cu));

            // 提取 fields 信息
            List<FieldInfo> fields = extractFields(cu);

            FileAstData fileAstData = new FileAstData();
            fileAstData.setPackageName(cu.getPackage().getName().getFullyQualifiedName());
            fileAstData.setFileContent(fileContentChars);
            fileAstData.setRelativePath(sourceFilePath.getFileName().toString());
            fileAstData.setAbsolutePath(sourceFilePath.toAbsolutePath().toString());
            fileAstData.setSequenceDiagramData(sequenceData);
            fileAstData.setFields(fields);
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

    /**
     * 提取類別中的所有 fields
     */
    private List<FieldInfo> extractFields(CompilationUnit cu) {
        List<FieldInfo> fields = new ArrayList<>();

        for (Object typeDecl : cu.types()) {
            if (typeDecl instanceof TypeDeclaration) {
                TypeDeclaration type = (TypeDeclaration) typeDecl;

                // 提取 fields
                for (Object fieldDecl : type.bodyDeclarations()) {
                    if (fieldDecl instanceof FieldDeclaration) {
                        FieldDeclaration field = (FieldDeclaration) fieldDecl;

                        // 獲取修飾符
                        StringBuilder modifiers = new StringBuilder();
                        for (Object modifier : field.modifiers()) {
                            if (modifiers.length() > 0) {
                                modifiers.append(" ");
                            }
                            modifiers.append(modifier.toString());
                        }

                        // 提取欄位註解
                        List<AnnotationInfo> fieldAnnotations = AnnotationExtractor.extractAnnotations(
                                field.modifiers(), cu);

                        // 獲取類型
                        String fieldType = field.getType().toString();

                        // 獲取行號
                        int startLine = cu.getLineNumber(field.getStartPosition());
                        int endLine = cu.getLineNumber(field.getStartPosition() + field.getLength());

                        // 處理每個變數宣告片段
                        for (Object fragment : field.fragments()) {
                            if (fragment instanceof VariableDeclarationFragment) {
                                VariableDeclarationFragment varFragment = (VariableDeclarationFragment) fragment;
                                String fieldName = varFragment.getName().getIdentifier();

                                // 獲取預設值
                                String defaultValue = null;
                                if (varFragment.getInitializer() != null) {
                                    defaultValue = varFragment.getInitializer().toString();
                                }

                                FieldInfo fieldInfo = new FieldInfo(
                                        fieldName,
                                        fieldType,
                                        modifiers.toString(),
                                        startLine,
                                        endLine);
                                fieldInfo.setDefaultValue(defaultValue);

                                // 添加欄位註解
                                for (AnnotationInfo annotation : fieldAnnotations) {
                                    fieldInfo.addAnnotation(annotation);
                                }

                                fields.add(fieldInfo);
                                logger.debug("提取 field: {} {} {} (註解: {})",
                                        modifiers.toString(), fieldType, fieldName, fieldAnnotations.size());
                            }
                        }
                    }
                }
            }
        }

        logger.info("提取到 {} 個 fields", fields.size());
        return fields;
    }

    /**
     * 提取類別級別的註解
     */
    private void extractClassAnnotations(CompilationUnit cu, SequenceDiagramData sequenceData) {
        for (Object typeDecl : cu.types()) {
            if (typeDecl instanceof TypeDeclaration) {
                TypeDeclaration type = (TypeDeclaration) typeDecl;

                // 提取類別上的註解
                List<AnnotationInfo> classAnnotations = AnnotationExtractor.extractAnnotations(
                        type.modifiers(), cu);

                for (AnnotationInfo annotation : classAnnotations) {
                    sequenceData.addClassAnnotation(annotation);
                    logger.debug("提取類別註解: {}", annotation.getAnnotationName());
                }
            }
        }
    }

}