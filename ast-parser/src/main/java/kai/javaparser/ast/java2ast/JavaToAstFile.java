package kai.javaparser.ast.java2ast;

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
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kai.javaparser.ast.model.AnnotationInfo;
import kai.javaparser.ast.model.FileAstData;
import kai.javaparser.ast.model.SequenceDiagramData;
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

            // 設置類別類型
            String classType = detectClassType(cu);
            sequenceData.setClassType(classType);
            logger.debug("檢測到類別類型: {} for {}", classType, fqn);

            // 【新增：提取繼承和實現資訊】
            extractInheritanceInfo(cu, sequenceData);

            // 提取類別級別的註解
            extractClassAnnotations(cu, sequenceData);

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

    /**
     * 檢測類別類型
     * 
     * @param cu CompilationUnit
     * @return 類別類型: "Class", "Interface"
     */
    private String detectClassType(CompilationUnit cu) {
        for (Object typeDecl : cu.types()) {
            if (typeDecl instanceof TypeDeclaration) {
                TypeDeclaration type = (TypeDeclaration) typeDecl;
                String className = type.getName().getIdentifier();

                // 檢查是否為介面
                if (type.isInterface()) {
                    logger.debug("檢測到介面: {}", className);
                    return "Interface";
                }

                // 檢查是否為抽象類別
                @SuppressWarnings("unchecked")
                List<Object> modifiers = type.modifiers();
                for (Object modifierObj : modifiers) {
                    if (modifierObj instanceof org.eclipse.jdt.core.dom.Modifier) {
                        org.eclipse.jdt.core.dom.Modifier modifier = (org.eclipse.jdt.core.dom.Modifier) modifierObj;
                        if (modifier.isAbstract()) {
                            logger.debug("檢測到抽象類別: {}", className);
                            return "AbstractClass";
                        }
                    }
                }

                // 預設為一般類別
                logger.debug("檢測到一般類別: {}", className);
                return "Class";
            }
        }
        logger.debug("未找到類型宣告，使用預設值: Class");
        return "Class"; // 預設值
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

    /**
     * 【新增輔助方法：提取繼承和實現資訊】
     */
    private void extractInheritanceInfo(CompilationUnit cu, SequenceDiagramData sequenceData) {
        for (Object typeDecl : cu.types()) {
            if (typeDecl instanceof TypeDeclaration) {
                TypeDeclaration type = (TypeDeclaration) typeDecl;

                // 提取父類別 (extends)
                if (type.getSuperclassType() != null) {
                    ITypeBinding binding = type.getSuperclassType().resolveBinding();
                    if (binding != null) {
                        sequenceData.setExtendsClassFqn(binding.getQualifiedName());
                        logger.debug("提取父類別: {}", binding.getQualifiedName());
                    } else {
                        // 如果 binding 為 null，使用 toString (可能不準確，但聊勝於無)
                        sequenceData.setExtendsClassFqn(type.getSuperclassType().toString());
                        logger.warn("無法解析父類別 binding，使用 toString: {}", type.getSuperclassType().toString());
                    }
                }

                // 提取實現介面 (implements)
                @SuppressWarnings("unchecked")
                List<org.eclipse.jdt.core.dom.Type> interfaces = type.superInterfaceTypes();
                List<String> interfaceFqns = new ArrayList<>();
                for (org.eclipse.jdt.core.dom.Type interfaceType : interfaces) {
                    ITypeBinding binding = interfaceType.resolveBinding();
                    if (binding != null) {
                        interfaceFqns.add(binding.getQualifiedName());
                        logger.debug("提取實現介面: {}", binding.getQualifiedName());
                    } else {
                        interfaceFqns.add(interfaceType.toString());
                        logger.warn("無法解析實現介面 binding，使用 toString: {}", interfaceType.toString());
                    }
                }
                sequenceData.setImplementsInterfaceFqns(interfaceFqns);
            }
        }
    }

}