package kai.javaparser.handler;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kai.javaparser.model.InteractionModel;

/**
 * InvocationHandler 專門處理 MethodInvocation, ConstructorInvocation,
 * ClassInstanceCreation 等方法呼叫
 */
public class InvocationHandler {
    private static final Logger logger = LoggerFactory.getLogger(InvocationHandler.class);

    public boolean visit(MethodInvocation node, HandlerContext context) {
        InteractionModel interaction = createInteractionModel(node, context);

        // Determine if this is a nested call (e.g., chained calls or argument
        // evaluation)
        if (!context.getInteractionStack().isEmpty()) {
            // This interaction is a child of the current top of the stack
            context.getInteractionStack().peek().addChild(interaction);
        } else if (context.getCurrentControlFlowFragment() != null) {
            // This is a top-level interaction within a control flow fragment
            context.getCurrentControlFlowFragment().addInteraction(interaction);
        } else if (context.getCurrentMethodGroup() != null) {
            // This is a top-level interaction within a method group
            context.getCurrentMethodGroup().addInteraction(interaction);
        } else {
            // Fallback: Add to global list if no other context
            context.getSequenceData().getAllInteractions().add(interaction);
        }

        context.getInteractionStack().push(interaction); // Push this interaction onto the stack for its children
        return true;
    }

    public void endVisit(MethodInvocation node, HandlerContext context) {
        if (!context.getInteractionStack().isEmpty()) {
            context.getInteractionStack().pop();
        }
    }

    public boolean visit(ConstructorInvocation node, HandlerContext context) {
        InteractionModel interaction = createConstructorInteractionModel(node, context);

        if (!context.getInteractionStack().isEmpty()) {
            context.getInteractionStack().peek().addChild(interaction);
        } else if (context.getCurrentControlFlowFragment() != null) {
            context.getCurrentControlFlowFragment().addInteraction(interaction);
        } else if (context.getCurrentMethodGroup() != null) {
            context.getCurrentMethodGroup().addInteraction(interaction);
        } else {
            context.getSequenceData().getAllInteractions().add(interaction);
        }

        context.getInteractionStack().push(interaction);
        return true;
    }

    public void endVisit(ConstructorInvocation node, HandlerContext context) {
        if (!context.getInteractionStack().isEmpty()) {
            context.getInteractionStack().pop();
        }
    }

    public boolean visit(ClassInstanceCreation node, HandlerContext context) {
        InteractionModel interaction = createClassInstanceInteractionModel(node, context);

        if (!context.getInteractionStack().isEmpty()) {
            context.getInteractionStack().peek().addChild(interaction);
        } else if (context.getCurrentControlFlowFragment() != null) {
            context.getCurrentControlFlowFragment().addInteraction(interaction);
        } else if (context.getCurrentMethodGroup() != null) {
            context.getCurrentMethodGroup().addInteraction(interaction);
        } else {
            context.getSequenceData().getAllInteractions().add(interaction);
        }

        context.getInteractionStack().push(interaction);
        return true;
    }

    public void endVisit(ClassInstanceCreation node, HandlerContext context) {
        if (!context.getInteractionStack().isEmpty()) {
            context.getInteractionStack().pop();
        }
    }

    // 將 create...InteractionModel 相關的 private 方法也移到這裡
    // 這些方法現在需要接收 HandlerContext 作為參數
    private InteractionModel createInteractionModel(MethodInvocation node, HandlerContext context) {
        logger.debug("createInteractionModel: {}", node.toString());

        InteractionModel interaction = new InteractionModel();
        interaction.setSequenceId(String.valueOf(context.getAndIncrementSequenceCounter()));
        interaction.setMethodName(node.getName().getIdentifier());
        interaction.setLineNumber(context.getCompilationUnit().getLineNumber(node.getStartPosition()));

        // Set caller (current class)
        String caller = context.getCurrentClassName() != null ? context.getCurrentClassName() : "unknown";
        interaction.setCaller(caller);

        // Set callee with variable information
        String callee = resolveCalleeType(node);
        interaction.setCallee(callee);

        // Extract variable name from expression
        if (node.getExpression() != null) {
            String variableName = extractVariableName(node.getExpression());
            if (variableName != null) {
                interaction.setCalleeVariable(variableName);
                interaction.setCalleeInstanceId(generateInstanceId(variableName, context));
            }
        } else {
            // If expression is null, it implies 'this'
            interaction.setCalleeVariable("this");
            interaction.setCalleeInstanceId(generateInstanceId("this", context));
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

        // 判斷是否被賦值給變數
        ASTNode parent = node.getParent();
        if (parent instanceof VariableDeclarationFragment) {
            VariableDeclarationFragment fragment = (VariableDeclarationFragment) parent;
            // 檢查當前的 MethodInvocation 是否是 VariableDeclarationFragment 的初始化表達式
            if (node.equals(fragment.getInitializer())) {
                interaction.setAssignedToVariable(fragment.getName().getIdentifier());
            }
        }

        return interaction;
    }

    private InteractionModel createConstructorInteractionModel(ConstructorInvocation node, HandlerContext context) {
        InteractionModel interaction = new InteractionModel();
        interaction.setSequenceId(String.valueOf(context.getAndIncrementSequenceCounter()));
        // Use the actual type being constructed
        IMethodBinding constructorBinding = node.resolveConstructorBinding();
        String constructedType = "unknown";
        if (constructorBinding != null && constructorBinding.getDeclaringClass() != null) {
            constructedType = constructorBinding.getDeclaringClass().getQualifiedName();
        }
        interaction.setMethodName(constructedType); // Use constructed type as method name for constructor invocation
        interaction.setLineNumber(context.getCompilationUnit().getLineNumber(node.getStartPosition()));

        String caller = context.getCurrentClassName() != null ? context.getCurrentClassName() : "unknown";
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

        // 判斷是否被賦值給變數
        ASTNode parent = node.getParent();
        if (parent instanceof VariableDeclarationFragment) {
            VariableDeclarationFragment fragment = (VariableDeclarationFragment) parent;
            // 檢查當前的 ConstructorInvocation 是否是 VariableDeclarationFragment 的初始化表達式
            if (node.equals(fragment.getInitializer())) {
                interaction.setAssignedToVariable(fragment.getName().getIdentifier());
            }
        }

        return interaction;
    }

    private InteractionModel createClassInstanceInteractionModel(ClassInstanceCreation node, HandlerContext context) {
        InteractionModel interaction = new InteractionModel();
        interaction.setSequenceId(String.valueOf(context.getAndIncrementSequenceCounter()));
        // Use the actual type being instantiated
        ITypeBinding typeBinding = node.getType().resolveBinding();
        String instantiatedType = typeBinding != null ? typeBinding.getQualifiedName() : node.getType().toString();

        interaction.setMethodName(instantiatedType); // Use instantiated type as method name for class instance creation
        interaction.setLineNumber(context.getCompilationUnit().getLineNumber(node.getStartPosition()));

        String caller = context.getCurrentClassName() != null ? context.getCurrentClassName() : "unknown";
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

        // 判斷是否被賦值給變數
        ASTNode parent = node.getParent();
        if (parent instanceof VariableDeclarationFragment) {
            VariableDeclarationFragment fragment = (VariableDeclarationFragment) parent;
            // 檢查當前的 ClassInstanceCreation 是否是 VariableDeclarationFragment 的初始化表達式
            if (node.equals(fragment.getInitializer())) {
                interaction.setAssignedToVariable(fragment.getName().getIdentifier());
            }
        }

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
    private String generateInstanceId(String variableName, HandlerContext context) {
        // Reset counter if variable name is "this" (common instance)
        if ("this".equals(variableName)) {
            return "this1"; // Assume only one 'this' instance per context
        }
        int count = context.getVariableInstanceCounters().getOrDefault(variableName, 0) + 1;
        context.getVariableInstanceCounters().put(variableName, count);
        return variableName + count;
    }

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
