package kai.javaparser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment; // <-- 新增此導入
import org.eclipse.jdt.core.dom.WhileStatement;

import kai.javaparser.model.ControlFlowFragment;
import kai.javaparser.model.InteractionModel;
import kai.javaparser.model.MethodGroup;
import kai.javaparser.model.SequenceDiagramData;

/**
 * EnhancedInteractionModelVisitor is a visitor that extracts interaction models
 * from a Java AST.
 * 
 * It extends ASTVisitor to traverse the AST and extract interaction models.
 * 
 * It also handles control flow fragments and method groups.
 * 
 */
public class EnhancedInteractionModelVisitor extends ASTVisitor {
    private final SequenceDiagramData sequenceData;
    private final CompilationUnit compilationUnit;
    private final Stack<InteractionModel> interactionStack = new Stack<>();
    private final Stack<ControlFlowFragment> controlFlowStack = new Stack<>();
    private final Map<String, Integer> variableInstanceCounters = new HashMap<>();
    private int sequenceCounter = 1;
    private ControlFlowFragment currentControlFlowFragment = null;

    // 新增：方法分組追蹤
    private MethodGroup currentMethodGroup = null;
    private String currentClassName = null; // Full qualified class name

    public EnhancedInteractionModelVisitor(SequenceDiagramData sequenceData, CompilationUnit compilationUnit) {
        this.sequenceData = sequenceData;
        this.compilationUnit = compilationUnit;
    }

    @Override
    public boolean visit(TypeDeclaration node) {
        // 記錄當前類名 (包含包名)
        String packageName = compilationUnit.getPackage() != null
                ? compilationUnit.getPackage().getName().getFullyQualifiedName()
                : "";
        currentClassName = packageName.isEmpty() ? node.getName().getIdentifier()
                : packageName + "." + node.getName().getIdentifier();
        return true;
    }

    @Override
    public void endVisit(TypeDeclaration node) {
        currentClassName = null; // 離開類別範圍
    }

    @Override
    public boolean visit(MethodDeclaration node) {
        // 只處理public方法
        // if (node.modifiers().stream().anyMatch(mod -> mod instanceof Modifier
        // && ((Modifier) mod).getKeyword() == Modifier.ModifierKeyword.PUBLIC_KEYWORD))
        // {

        // 創建新的方法分組
        currentMethodGroup = new MethodGroup();
        currentMethodGroup.setMethodName(node.getName().getIdentifier());
        currentMethodGroup.setClassName(currentClassName);
        currentMethodGroup.setFullMethodName(currentClassName + "." + node.getName().getIdentifier());
        currentMethodGroup.setStartLineNumber(compilationUnit.getLineNumber(node.getStartPosition()));

        // 提取方法簽名
        StringBuilder signature = new StringBuilder();
        if (node.parameters() != null) {
            for (int i = 0; i < node.parameters().size(); i++) {
                SingleVariableDeclaration param = (SingleVariableDeclaration) node.parameters().get(i);
                if (i > 0)
                    signature.append(", ");
                // Use resolveTypeBinding for full qualified name if available
                ITypeBinding typeBinding = param.getType().resolveBinding();
                signature.append(typeBinding != null ? typeBinding.getQualifiedName() : param.getType().toString());
            }
        }
        currentMethodGroup.setMethodSignature(signature.toString());

        // 新增：提取 throws 異常
        List<String> thrownExceptions = new ArrayList<>();
        for (Object exceptionType : node.thrownExceptionTypes()) {
            if (exceptionType instanceof Type) {
                ITypeBinding typeBinding = ((Type) exceptionType).resolveBinding();
                thrownExceptions
                        .add(typeBinding != null ? typeBinding.getQualifiedName() : ((Type) exceptionType).toString());
            }
        }
        currentMethodGroup.setThrownExceptions(thrownExceptions);

        // 添加到序列數據
        sequenceData.addMethodGroup(currentMethodGroup);

        return true;
        // }
        // return false;
    }

    @Override
    public void endVisit(MethodDeclaration node) {
        if (currentMethodGroup != null) {
            // 設置方法結束行號
            currentMethodGroup
                    .setEndLineNumber(compilationUnit.getLineNumber(node.getStartPosition() + node.getLength()));
            currentMethodGroup = null;
        }
    }

    @Override
    public boolean visit(IfStatement node) {
        ControlFlowFragment fragment = new ControlFlowFragment();
        fragment.setSequenceId(String.valueOf(sequenceCounter++));
        fragment.setType(ControlFlowFragment.ControlFlowType.ALTERNATIVE);
        fragment.setCondition(node.getExpression().toString());
        fragment.setStartLineNumber(compilationUnit.getLineNumber(node.getStartPosition()));

        // 設置控制流程片段的上下文
        if (currentMethodGroup != null) {
            fragment.setCallerClass(currentMethodGroup.getClassName());
            fragment.setCallerMethod(currentMethodGroup.getMethodName());
            fragment.setContextPath(currentMethodGroup.getFullMethodName());
        }

        if (currentControlFlowFragment != null) {
            currentControlFlowFragment.addAlternative(fragment);
        } else if (currentMethodGroup != null) {
            currentMethodGroup.addControlFlowFragment(fragment);
        } else {
            // 如果不在方法內，添加到序列數據（向後兼容）
            sequenceData.getAllControlFlowFragments().add(fragment);
        }

        controlFlowStack.push(fragment);
        currentControlFlowFragment = fragment;
        return true;
    }

    @Override
    public void endVisit(IfStatement node) {
        if (!controlFlowStack.isEmpty()) {
            ControlFlowFragment fragment = controlFlowStack.pop();
            fragment.setEndLineNumber(compilationUnit.getLineNumber(node.getStartPosition() + node.getLength()));

            // Note: The 'else' part is typically handled as a separate 'alternative'
            // fragment,
            // but your current structure adds it *into* the 'if' fragment's alternatives.
            // This is a design choice. The code below keeps your current design.
            if (fragment.getType() == ControlFlowFragment.ControlFlowType.ALTERNATIVE &&
                    node.getElseStatement() != null) {
                // Add else alternative
                ControlFlowFragment elseFragment = new ControlFlowFragment();
                elseFragment.setSequenceId(String.valueOf(sequenceCounter++));
                elseFragment.setType(ControlFlowFragment.ControlFlowType.OPTIONAL); // Or another type for 'else'
                elseFragment.setCondition("[else]");
                elseFragment.setStartLineNumber(
                        compilationUnit.getLineNumber(node.getElseStatement().getStartPosition()));
                elseFragment.setEndLineNumber(compilationUnit.getLineNumber(
                        node.getElseStatement().getStartPosition() + node.getElseStatement().getLength()));

                // 設置else片段的上下文
                if (currentMethodGroup != null) {
                    elseFragment.setCallerClass(currentMethodGroup.getClassName());
                    elseFragment.setCallerMethod(currentMethodGroup.getMethodName());
                    elseFragment.setContextPath(currentMethodGroup.getFullMethodName());
                }

                fragment.addAlternative(elseFragment);
            }
        }
        currentControlFlowFragment = controlFlowStack.isEmpty() ? null : controlFlowStack.peek();
    }

    // 這是您原始的 visit(ForStatement node) 方法
    @Override
    public boolean visit(ForStatement node) {
        return handleLoopFragment(node, "for loop");
    }

    // 這是處理 EnhancedForStatement 的方法
    @Override
    public boolean visit(EnhancedForStatement node) {
        String condition = node.getParameter().getName().getIdentifier() + " : " + node.getExpression().toString();
        return handleLoopFragment(node, condition);
    }

    // 抽出共用的邏輯到一個輔助方法
    private boolean handleLoopFragment(ASTNode node, String conditionDescription) {
        ControlFlowFragment fragment = new ControlFlowFragment();
        fragment.setSequenceId(String.valueOf(sequenceCounter++));
        fragment.setType(ControlFlowFragment.ControlFlowType.LOOP);
        fragment.setCondition(conditionDescription);
        fragment.setStartLineNumber(compilationUnit.getLineNumber(node.getStartPosition()));

        // 設置控制流程片段的上下文
        if (currentMethodGroup != null) {
            fragment.setCallerClass(currentMethodGroup.getClassName());
            fragment.setCallerMethod(currentMethodGroup.getMethodName());
            fragment.setContextPath(currentMethodGroup.getFullMethodName());
        }

        if (currentControlFlowFragment != null) {
            currentControlFlowFragment.addAlternative(fragment); // Adding a loop as an alternative to its parent
                                                                 // fragment (e.g., if inside an if)
        } else if (currentMethodGroup != null) {
            currentMethodGroup.addControlFlowFragment(fragment); // Adding a top-level loop to the method
        } else {
            sequenceData.getAllControlFlowFragments().add(fragment); // Fallback: add to global list
        }

        controlFlowStack.push(fragment);
        currentControlFlowFragment = fragment;
        return true;
    }

    // 這是您原始的 endVisit(ForStatement node) 方法
    @Override
    public void endVisit(ForStatement node) {
        endVisitLoopFragment(node);
    }

    // 這是處理 EnhancedForStatement 的方法
    @Override
    public void endVisit(EnhancedForStatement node) {
        endVisitLoopFragment(node);
    }

    // 抽出共用的邏輯到一個輔助方法
    private void endVisitLoopFragment(ASTNode node) {
        if (!controlFlowStack.isEmpty()) {
            ControlFlowFragment fragment = controlFlowStack.pop();
            fragment.setEndLineNumber(compilationUnit.getLineNumber(node.getStartPosition() + node.getLength()));
        }
        currentControlFlowFragment = controlFlowStack.isEmpty() ? null : controlFlowStack.peek();
    }

    @Override
    public boolean visit(WhileStatement node) {
        ControlFlowFragment fragment = new ControlFlowFragment();
        fragment.setSequenceId(String.valueOf(sequenceCounter++));
        fragment.setType(ControlFlowFragment.ControlFlowType.LOOP);
        fragment.setCondition("while (" + node.getExpression().toString() + ")");
        fragment.setStartLineNumber(compilationUnit.getLineNumber(node.getStartPosition()));

        // 設置控制流程片段的上下文
        if (currentMethodGroup != null) {
            fragment.setCallerClass(currentMethodGroup.getClassName());
            fragment.setCallerMethod(currentMethodGroup.getMethodName());
            fragment.setContextPath(currentMethodGroup.getFullMethodName());
        }

        if (currentControlFlowFragment != null) {
            currentControlFlowFragment.addAlternative(fragment);
        } else if (currentMethodGroup != null) {
            currentMethodGroup.addControlFlowFragment(fragment);
        } else {
            sequenceData.getAllControlFlowFragments().add(fragment);
        }

        controlFlowStack.push(fragment);
        currentControlFlowFragment = fragment;
        return true;
    }

    @Override
    public void endVisit(WhileStatement node) {
        if (!controlFlowStack.isEmpty()) {
            // Fix: Set endLineNumber for WhileStatement
            ControlFlowFragment fragment = controlFlowStack.pop();
            fragment.setEndLineNumber(compilationUnit.getLineNumber(node.getStartPosition() + node.getLength()));
        }
        currentControlFlowFragment = controlFlowStack.isEmpty() ? null : controlFlowStack.peek();
    }

    @Override
    public boolean visit(MethodInvocation node) {
        InteractionModel interaction = createInteractionModel(node);

        // Determine if this is a nested call (e.g., chained calls or argument
        // evaluation)
        if (!interactionStack.isEmpty()) {
            // This interaction is a child of the current top of the stack
            interactionStack.peek().addChild(interaction);
        } else if (currentControlFlowFragment != null) {
            // This is a top-level interaction within a control flow fragment
            currentControlFlowFragment.addInteraction(interaction);
        } else if (currentMethodGroup != null) {
            // This is a top-level interaction within a method group
            currentMethodGroup.addInteraction(interaction);
        } else {
            // Fallback: Add to global list if no other context
            sequenceData.getAllInteractions().add(interaction);
        }

        interactionStack.push(interaction); // Push this interaction onto the stack for its children
        return true;
    }

    @Override
    public void endVisit(MethodInvocation node) {
        if (!interactionStack.isEmpty()) {
            interactionStack.pop();
        }
    }

    @Override
    public boolean visit(ConstructorInvocation node) {
        InteractionModel interaction = createConstructorInteractionModel(node);

        if (!interactionStack.isEmpty()) {
            interactionStack.peek().addChild(interaction);
        } else if (currentControlFlowFragment != null) {
            currentControlFlowFragment.addInteraction(interaction);
        } else if (currentMethodGroup != null) {
            currentMethodGroup.addInteraction(interaction);
        } else {
            sequenceData.getAllInteractions().add(interaction);
        }

        interactionStack.push(interaction);
        return true;
    }

    @Override
    public void endVisit(ConstructorInvocation node) {
        if (!interactionStack.isEmpty()) {
            interactionStack.pop();
        }
    }

    @Override
    public boolean visit(ClassInstanceCreation node) {
        InteractionModel interaction = createClassInstanceInteractionModel(node);

        if (!interactionStack.isEmpty()) {
            interactionStack.peek().addChild(interaction);
        } else if (currentControlFlowFragment != null) {
            currentControlFlowFragment.addInteraction(interaction);
        } else if (currentMethodGroup != null) {
            currentMethodGroup.addInteraction(interaction);
        } else {
            sequenceData.getAllInteractions().add(interaction);
        }

        interactionStack.push(interaction);
        return true;
    }

    @Override
    public void endVisit(ClassInstanceCreation node) {
        if (!interactionStack.isEmpty()) {
            interactionStack.pop();
        }
    }

    private InteractionModel createInteractionModel(MethodInvocation node) {
        InteractionModel interaction = new InteractionModel();
        interaction.setSequenceId(String.valueOf(sequenceCounter++));
        interaction.setMethodName(node.getName().getIdentifier());
        interaction.setLineNumber(compilationUnit.getLineNumber(node.getStartPosition()));

        // Set caller (current class)
        String caller = currentClassName != null ? currentClassName : "unknown"; // Use currentClassName
        interaction.setCaller(caller);

        // Set callee with variable information
        String callee = resolveCalleeType(node);
        interaction.setCallee(callee);

        // Extract variable name from expression
        if (node.getExpression() != null) {
            String variableName = extractVariableName(node.getExpression());
            if (variableName != null) {
                interaction.setCalleeVariable(variableName);
                interaction.setCalleeInstanceId(generateInstanceId(variableName));
            }
        } else {
            // If expression is null, it implies 'this'
            interaction.setCalleeVariable("this");
            interaction.setCalleeInstanceId(generateInstanceId("this"));
        }

        // Set return value type
        String returnType = resolveReturnType(node);
        interaction.setReturnValue(returnType);

        // Process arguments
        for (Object arg : node.arguments()) {
            if (arg instanceof Expression) {
                String argValue = arg.toString();
                interaction.addArgument(argValue);
            }
        }

        // 判斷是否被賦值給變數 -->
        ASTNode parent = node.getParent();
        if (parent instanceof VariableDeclarationFragment) {
            VariableDeclarationFragment fragment = (VariableDeclarationFragment) parent;
            // 檢查當前的 MethodInvocation 是否是 VariableDeclarationFragment 的初始化表達式
            if (node.equals(fragment.getInitializer())) {
                interaction.setAssignedToVariable(fragment.getName().getIdentifier());
            }
        }
        // <-------------------------------------->

        return interaction;
    }

    private InteractionModel createConstructorInteractionModel(ConstructorInvocation node) {
        InteractionModel interaction = new InteractionModel();
        interaction.setSequenceId(String.valueOf(sequenceCounter++));
        // Use the actual type being constructed
        IMethodBinding constructorBinding = node.resolveConstructorBinding();
        String constructedType = "unknown";
        if (constructorBinding != null && constructorBinding.getDeclaringClass() != null) {
            constructedType = constructorBinding.getDeclaringClass().getQualifiedName();
        }
        interaction.setMethodName(constructedType); // Use constructed type as method name for constructor invocation
        interaction.setLineNumber(compilationUnit.getLineNumber(node.getStartPosition()));

        String caller = currentClassName != null ? currentClassName : "unknown";
        interaction.setCaller(caller);

        interaction.setCallee(constructedType);
        interaction.setReturnValue(constructedType); // Constructor 'returns' an instance of its type

        // Process arguments
        for (Object arg : node.arguments()) {
            if (arg instanceof Expression) {
                String argValue = arg.toString();
                interaction.addArgument(argValue);
            }
        }

        // 判斷是否被賦值給變數 -->
        ASTNode parent = node.getParent();
        if (parent instanceof VariableDeclarationFragment) {
            VariableDeclarationFragment fragment = (VariableDeclarationFragment) parent;
            // 檢查當前的 ConstructorInvocation 是否是 VariableDeclarationFragment 的初始化表達式
            if (node.equals(fragment.getInitializer())) {
                interaction.setAssignedToVariable(fragment.getName().getIdentifier());
            }
        }
        // <-------------------------------------->

        return interaction;
    }

    private InteractionModel createClassInstanceInteractionModel(ClassInstanceCreation node) {
        InteractionModel interaction = new InteractionModel();
        interaction.setSequenceId(String.valueOf(sequenceCounter++));
        // Use the actual type being instantiated
        ITypeBinding typeBinding = node.getType().resolveBinding();
        String instantiatedType = typeBinding != null ? typeBinding.getQualifiedName() : node.getType().toString();

        interaction.setMethodName(instantiatedType); // Use instantiated type as method name for class instance creation
        interaction.setLineNumber(compilationUnit.getLineNumber(node.getStartPosition()));

        String caller = currentClassName != null ? currentClassName : "unknown";
        interaction.setCaller(caller);

        interaction.setCallee(instantiatedType);
        interaction.setReturnValue(instantiatedType);

        // Process arguments
        for (Object arg : node.arguments()) {
            if (arg instanceof Expression) {
                String argValue = arg.toString();
                interaction.addArgument(argValue);
            }
        }

        // 判斷是否被賦值給變數 -->
        ASTNode parent = node.getParent();
        if (parent instanceof VariableDeclarationFragment) {
            VariableDeclarationFragment fragment = (VariableDeclarationFragment) parent;
            // 檢查當前的 ClassInstanceCreation 是否是 VariableDeclarationFragment 的初始化表達式
            if (node.equals(fragment.getInitializer())) {
                interaction.setAssignedToVariable(fragment.getName().getIdentifier());
            }
        }
        // <-------------------------------------->

        return interaction;
    }

    /**
     * Extract variable name from expression
     */
    private String extractVariableName(Expression expression) {
        if (expression instanceof SimpleName) {
            return ((SimpleName) expression).getIdentifier();
        } else if (expression instanceof FieldAccess) {
            FieldAccess fieldAccess = (FieldAccess) expression;
            return fieldAccess.getName().getIdentifier();
        } else if (expression instanceof MethodInvocation) {
            MethodInvocation methodInvocation = (MethodInvocation) expression;
            if (methodInvocation.getExpression() != null) {
                return extractVariableName(methodInvocation.getExpression());
            }
            // If it's a method invocation without an explicit expression,
            // it implies 'this'.
            return "this";
        }
        // Handle other expression types if necessary (e.g., ArrayAccess,
        // ParenthesizedExpression)
        return null;
    }

    /**
     * Generate unique instance ID for variable
     */
    private String generateInstanceId(String variableName) {
        // Reset counter if variable name is "this" (common instance)
        if ("this".equals(variableName)) {
            return "this1"; // Assume only one 'this' instance per context
        }
        int count = variableInstanceCounters.getOrDefault(variableName, 0) + 1;
        variableInstanceCounters.put(variableName, count);
        return variableName + count;
    }

    /**
     * This method is now redundant as currentClassName is maintained by
     * visit/endVisit(TypeDeclaration)
     * Keeping it for now but consider removing if not used elsewhere.
     */
    // private String getCurrentClassName() {
    // return currentClassName != null ? currentClassName : "unknown";
    // }

    /**
     * 解析被呼叫者類型
     */
    private String resolveCalleeType(MethodInvocation node) {
        if (node.getExpression() != null) {
            ITypeBinding typeBinding = node.getExpression().resolveTypeBinding();
            if (typeBinding != null) {
                return typeBinding.getQualifiedName();
            }
        }

        // 嘗試從方法綁定獲取
        IMethodBinding methodBinding = node.resolveMethodBinding();
        if (methodBinding != null && methodBinding.getDeclaringClass() != null) {
            return methodBinding.getDeclaringClass().getQualifiedName();
        }

        return "unknown";
    }

    /**
     * 解析回傳值類型
     */
    private String resolveReturnType(MethodInvocation node) {
        IMethodBinding methodBinding = node.resolveMethodBinding();
        if (methodBinding != null) {
            ITypeBinding returnType = methodBinding.getReturnType();
            if (returnType != null) {
                return returnType.getQualifiedName();
            }
        }
        return "void";
    }
}