package com.yourcompany.parser;

import com.yourcompany.parser.model.AstNode;
import com.yourcompany.parser.model.AstNodeType;
import com.yourcompany.parser.model.FileAstData;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.stream.Collectors;

public class AstExtractor {

    private static final Logger logger = LoggerFactory.getLogger(AstExtractor.class);

    /**
     * Parses a single Java file and extracts its AST data.
     *
     * @param sourceFilePath The absolute path to the Java source file.
     * @param projectSources Array of absolute paths to source directories for the project.
     * @param projectClasspath Array of absolute paths to JARs or class directories for the project's dependencies.
     * @param complianceLevel Java compliance level (e.g., JavaCore.VERSION_17).
     * @return FileAstData containing the parsed AST information, or null if parsing fails.
     */
    public FileAstData parseJavaFile(Path sourceFilePath, String[] projectSources, String[] projectClasspath, String complianceLevel) {
        try {
            String fileContent = Files.readString(sourceFilePath);
            char[] fileContentChars = fileContent.toCharArray();

            // Fix 4: Use JLS_CURRENT or JLS17
            ASTParser parser = ASTParser.newParser(AST.getJLSLatest()); // Recommended for latest supported JLS
            // ASTParser parser = ASTParser.newParser(AST.JLS17); // If you strictly target Java 17

            parser.setSource(fileContentChars);

            // Configure parser for full binding resolution
            parser.setKind(ASTParser.K_COMPILATION_UNIT);
            parser.setResolveBindings(true); // CRUCIAL for type resolution (FQNs)
            parser.setBindingsRecovery(true); // Recover bindings even if errors exist
            parser.setStatementsRecovery(true); // Recover statements even if errors exist

            // Set up classpath and source paths for binding resolution
            parser.setEnvironment(projectClasspath, projectSources, null, true);

            // Set compiler options (handle Java 8 and 17)
            Map<String, String> options = JavaCore.getOptions();
            JavaCore.setComplianceOptions(complianceLevel, options); // E.g., JavaCore.VERSION_1_8 or JavaCore.VERSION_17
            parser.setCompilerOptions(options);

            // Set the unit name for context (important for resolving types within the file)
            parser.setUnitName(sourceFilePath.getFileName().toString());

            CompilationUnit cu = (CompilationUnit) parser.createAST(null);

            FileAstData fileAstData = new FileAstData();
            fileAstData.setAbsolutePath(sourceFilePath.toAbsolutePath().toString());
            
            AstNode compilationUnitRoot = new AstNode(AstNodeType.COMPILATION_UNIT);
            fileAstData.setCompilationUnitNode(compilationUnitRoot);

            // Check for parsing problems
            for (IProblem problem : cu.getProblems()) {
                if (problem.isError()) {
                    logger.warn("Parsing problem in {}: {}", sourceFilePath, problem.getMessage());
                }
            }

            // Custom AST Visitor to extract information
            cu.accept(new ASTVisitor() {
                private Stack<AstNode> parentStack = new Stack<>();

                private AstNode createAstNode(ASTNode jdtNode, AstNodeType type) {
                    AstNode astNode = new AstNode(type);
                    astNode.setStartPosition(jdtNode.getStartPosition());
                    astNode.setLength(jdtNode.getLength());
                    astNode.setLineNumber(cu.getLineNumber(jdtNode.getStartPosition()));

                    // Add to parent from stack
                    if (!parentStack.isEmpty()) {
                        parentStack.peek().addChild(astNode);
                    }
                    return astNode;
                }

                private void setCommonNodeProperties(AstNode astNode, ASTNode jdtNode) {
                    // Extract modifiers
                    if (jdtNode instanceof BodyDeclaration) {
                        BodyDeclaration bd = (BodyDeclaration) jdtNode;
                        // Fix 1 & Type Safety: Cast to List<IExtendedModifier> to ensure proper generics
                        astNode.setModifiers(((List<IExtendedModifier>) bd.modifiers()).stream()
                                .map(modifier -> {
                                    if (modifier instanceof Modifier) {
                                        return ((Modifier) modifier).getKeyword().toString();
                                    } else if (modifier instanceof Annotation) {
                                        return "@" + ((Annotation) modifier).getTypeName().getFullyQualifiedName();
                                    }
                                    return modifier.toString();
                                })
                                .collect(Collectors.toList()));

                        // Extract annotations specifically
                        // Fix 1 & Type Safety: Cast to List<IExtendedModifier>
                        astNode.setAnnotations(((List<IExtendedModifier>) bd.modifiers()).stream()
                                .filter(modifier -> modifier instanceof Annotation)
                                .map(annotation -> ((Annotation) annotation).getTypeName().getFullyQualifiedName())
                                .collect(Collectors.toList()));
                    }

                    // Resolve FQNs for declarable elements
                    IBinding binding = null;
                    if (jdtNode instanceof TypeDeclaration) {
                        binding = ((TypeDeclaration) jdtNode).resolveBinding();
                    } else if (jdtNode instanceof MethodDeclaration) {
                        binding = ((MethodDeclaration) jdtNode).resolveBinding();
                    } else if (jdtNode instanceof EnumConstantDeclaration) {
                        // Fix 2: EnumConstantDeclaration doesn't have resolveBinding() directly, but its name does.
                        binding = ((EnumConstantDeclaration) jdtNode).getName().resolveBinding();
                    } else if (jdtNode instanceof AnnotationTypeDeclaration) {
                        binding = ((AnnotationTypeDeclaration) jdtNode).resolveBinding();
                    } else if (jdtNode instanceof VariableDeclarationFragment) {
                        binding = ((VariableDeclarationFragment) jdtNode).resolveBinding();
                    } else if (jdtNode instanceof SimpleName) {
                        binding = ((SimpleName) jdtNode).resolveBinding();
                    } else if (jdtNode instanceof QualifiedName) {
                        binding = ((QualifiedName) jdtNode).resolveBinding();
                    } else if (jdtNode instanceof MethodInvocation) {
                        binding = ((MethodInvocation) jdtNode).resolveMethodBinding();
                    } else if (jdtNode instanceof ConstructorInvocation) {
                        binding = ((ConstructorInvocation) jdtNode).resolveConstructorBinding();
                    } else if (jdtNode instanceof ClassInstanceCreation) {
                        binding = ((ClassInstanceCreation) jdtNode).resolveConstructorBinding();
                    }


                    if (binding != null) {
                        String fqn = getFullyQualifiedName(binding);
                        if (fqn != null) {
                            astNode.setFullyQualifiedName(fqn);
                        }
                        if (binding instanceof IVariableBinding) {
                            ITypeBinding typeBinding = ((IVariableBinding) binding).getType();
                            if (typeBinding != null) {
                                astNode.setResolvedTypeFQN(typeBinding.getQualifiedName());
                            }
                        } else if (binding instanceof IMethodBinding) {
                            ITypeBinding returnTypeBinding = ((IMethodBinding) binding).getReturnType();
                            if (returnTypeBinding != null) {
                                astNode.setResolvedTypeFQN(returnTypeBinding.getQualifiedName());
                            }
                        } else if (binding instanceof ITypeBinding) {
                             astNode.setResolvedTypeFQN(((ITypeBinding) binding).getQualifiedName());
                        }
                    }
                }

                private String getFullyQualifiedName(IBinding binding) {
                    if (binding == null) {
                        return null;
                    }
                    switch (binding.getKind()) {
                        case IBinding.TYPE:
                            return ((ITypeBinding) binding).getQualifiedName();
                        case IBinding.METHOD:
                            IMethodBinding methodBinding = (IMethodBinding) binding;
                            // For methods, include parameters for unique signature
                            if (methodBinding.getDeclaringClass() != null) { // Ensure declaring class exists
                                return methodBinding.getDeclaringClass().getQualifiedName() + "." + methodBinding.getName() + getMethodParametersSignature(methodBinding);
                            }
                            return methodBinding.getName() + getMethodParametersSignature(methodBinding); // Fallback if no declaring class (unlikely for resolved methods)
                        case IBinding.VARIABLE:
                            IVariableBinding varBinding = (IVariableBinding) binding;
                            if (varBinding.isField() && varBinding.getDeclaringClass() != null) {
                                return varBinding.getDeclaringClass().getQualifiedName() + "." + varBinding.getName();
                            }
                            return null; // Local variables don't have global FQN
                        case IBinding.PACKAGE:
                            return ((IPackageBinding) binding).getName();
                        default:
                            return null;
                    }
                }

                private String getMethodParametersSignature(IMethodBinding methodBinding) {
                    return Arrays.stream(methodBinding.getParameterTypes())
                            .map(ITypeBinding::getQualifiedName)
                            .collect(Collectors.joining(",", "(", ")"));
                }


                @Override
                public boolean visit(CompilationUnit node) {
                    fileAstData.setPackageName(node.getPackage() != null ? node.getPackage().getName().getFullyQualifiedName() : "");
                    // Type safety: Cast node.imports() to List<ImportDeclaration>
                    ((List<ImportDeclaration>) node.imports()).forEach(imp -> fileAstData.getImports().add(imp.getName().getFullyQualifiedName()));

                    setCommonNodeProperties(compilationUnitRoot, node);
                    parentStack.push(compilationUnitRoot);
                    return true;
                }

                @Override
                public void endVisit(CompilationUnit node) {
                    parentStack.pop();
                }

                @Override
                public boolean visit(PackageDeclaration node) {
                    return false;
                }

                @Override
                public boolean visit(ImportDeclaration node) {
                    return false;
                }

                @Override
                public boolean visit(TypeDeclaration node) {
                    AstNode astNode = createAstNode(node, AstNodeType.TYPE_DECLARATION);
                    astNode.setName(node.getName().getIdentifier());
                    setCommonNodeProperties(astNode, node);
                    parentStack.push(astNode);
                    return true;
                }

                @Override
                public void endVisit(TypeDeclaration node) {
                    parentStack.pop();
                }

                @Override
                public boolean visit(MethodDeclaration node) {
                    AstNode astNode = createAstNode(node, AstNodeType.METHOD_DECLARATION);
                    astNode.setName(node.getName().getIdentifier());
                    setCommonNodeProperties(astNode, node);

                    // Add parameters as children nodes
                    // Type safety: Cast node.parameters() to List<SingleVariableDeclaration>
                    ((List<SingleVariableDeclaration>) node.parameters()).forEach(param -> {
                        AstNode paramNode = new AstNode(AstNodeType.VARIABLE_DECLARATION_FRAGMENT);
                        paramNode.setName(param.getName().getIdentifier());
                        ITypeBinding typeBinding = param.getType().resolveBinding();
                        if (typeBinding != null) {
                            paramNode.setResolvedTypeFQN(typeBinding.getQualifiedName());
                        } else {
                            paramNode.setResolvedTypeFQN(param.getType().toString());
                        }
                        astNode.addChild(paramNode);
                    });

                    parentStack.push(astNode);
                    return true;
                }

                @Override
                public void endVisit(MethodDeclaration node) {
                    parentStack.pop();
                }

                @Override
                public boolean visit(FieldDeclaration node) {
                    AstNode fieldDeclNode = createAstNode(node, AstNodeType.FIELD_DECLARATION);
                    setCommonNodeProperties(fieldDeclNode, node);
                    parentStack.push(fieldDeclNode);
                    return true;
                }

                @Override
                public void endVisit(FieldDeclaration node) {
                    parentStack.pop();
                }

                @Override
                public boolean visit(VariableDeclarationFragment node) {
                    // This fragment could be part of a FieldDeclaration or a local variable
                    AstNode varFragmentNode = createAstNode(node, AstNodeType.VARIABLE_DECLARATION_FRAGMENT);
                    varFragmentNode.setName(node.getName().getIdentifier());
                    setCommonNodeProperties(varFragmentNode, node);

                    // For fields, the type is on the parent FieldDeclaration
                    if (node.getParent() instanceof FieldDeclaration) {
                        FieldDeclaration fd = (FieldDeclaration) node.getParent();
                        if (fd.getType() != null) {
                            ITypeBinding typeBinding = fd.getType().resolveBinding();
                            if (typeBinding != null) {
                                varFragmentNode.setResolvedTypeFQN(typeBinding.getQualifiedName());
                            } else {
                                varFragmentNode.setResolvedTypeFQN(fd.getType().toString());
                            }
                        }
                    }
                    
                    // Capture initializer expression if present
                    if (node.getInitializer() != null) {
                        AstNode initializerNode = new AstNode(AstNodeType.UNKNOWN); // Can define LITERAL or EXPRESSION type
                        initializerNode.setName("Initializer");
                        initializerNode.setLiteralValue(node.getInitializer().toString());
                        // Fix 3: Cast to Expression before calling resolveTypeBinding()
                        if (((Expression) node.getInitializer()).resolveTypeBinding() != null) {
                            initializerNode.setResolvedTypeFQN(((Expression) node.getInitializer()).resolveTypeBinding().getQualifiedName());
                        }
                        varFragmentNode.addChild(initializerNode);
                    }

                    parentStack.push(varFragmentNode);
                    return true;
                }

                @Override
                public void endVisit(VariableDeclarationFragment node) {
                    parentStack.pop();
                }

                @Override
                public boolean visit(MethodInvocation node) {
                    AstNode astNode = createAstNode(node, AstNodeType.METHOD_INVOCATION);
                    astNode.setName(node.getName().getIdentifier());
                    setCommonNodeProperties(astNode, node);
                    
                    if (node.getExpression() != null) {
                        AstNode receiverNode = new AstNode(AstNodeType.UNKNOWN);
                        receiverNode.setName("Receiver");
                        receiverNode.setLiteralValue(node.getExpression().toString());
                        // Fix 3: Cast to Expression
                        if (((Expression) node.getExpression()).resolveTypeBinding() != null) {
                            receiverNode.setResolvedTypeFQN(((Expression) node.getExpression()).resolveTypeBinding().getQualifiedName());
                        }
                        astNode.addChild(receiverNode);
                    }

                    // Type safety: Cast node.arguments() to List<Expression>
                    ((List<Expression>) node.arguments()).forEach(arg -> {
                        AstNode argNode = new AstNode(AstNodeType.UNKNOWN);
                        argNode.setName("Argument");
                        argNode.setLiteralValue(arg.toString());
                        // Fix 3: Cast to Expression
                        if (((Expression) arg).resolveTypeBinding() != null) {
                            argNode.setResolvedTypeFQN(((Expression) arg).resolveTypeBinding().getQualifiedName());
                        }
                        astNode.addChild(argNode);
                    });

                    parentStack.push(astNode);
                    return true;
                }

                @Override
                public void endVisit(MethodInvocation node) {
                    parentStack.pop();
                }

                @Override
                public boolean visit(QualifiedName node) {
                    AstNode astNode = createAstNode(node, AstNodeType.QUALIFIED_NAME);
                    astNode.setName(node.getName().getIdentifier());
                    setCommonNodeProperties(astNode, node);

                    if (node.getQualifier() != null) {
                         AstNode qualifierNode = new AstNode(AstNodeType.UNKNOWN);
                         qualifierNode.setName("Qualifier");
                         qualifierNode.setLiteralValue(node.getQualifier().toString());
                         // Fix 3: Cast to Name (subclass of Expression) for resolveTypeBinding
                         if (((Name) node.getQualifier()).resolveTypeBinding() != null) { // Name is a subclass of Expression
                             qualifierNode.setResolvedTypeFQN(((Name) node.getQualifier()).resolveTypeBinding().getQualifiedName());
                         }
                         astNode.addChild(qualifierNode);
                    }
                    parentStack.push(astNode);
                    return true;
                }

                @Override
                public void endVisit(QualifiedName node) {
                    parentStack.pop();
                }

                @Override
                public boolean visit(SimpleName node) {
                    AstNode astNode = createAstNode(node, AstNodeType.SIMPLE_NAME);
                    astNode.setName(node.getIdentifier());
                    setCommonNodeProperties(astNode, node);
                    return false; // Leaf node typically
                }

                @Override
                public boolean visit(StringLiteral node) {
                    AstNode astNode = createAstNode(node, AstNodeType.UNKNOWN);
                    astNode.setLiteralValue(node.getLiteralValue());
                    astNode.setName("StringLiteral");
                    return false;
                }

                @Override
                public boolean visit(NumberLiteral node) {
                    AstNode astNode = createAstNode(node, AstNodeType.UNKNOWN);
                    astNode.setLiteralValue(node.getToken());
                    astNode.setName("NumberLiteral");
                    return false;
                }
            });

            return fileAstData;

        } catch (IOException e) {
            logger.error("Error reading file {}: {}", sourceFilePath, e.getMessage());
            return null;
        } catch (Exception e) {
            logger.error("Unexpected error parsing file {}: {}", sourceFilePath, e.getMessage(), e);
            return null;
        }
    }
}