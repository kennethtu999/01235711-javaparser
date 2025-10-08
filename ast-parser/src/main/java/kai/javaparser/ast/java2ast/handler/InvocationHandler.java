package kai.javaparser.ast.java2ast.handler;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kai.javaparser.ast.model.InteractionModel;

/**
 * InvocationHandler 專門處理 MethodInvocation, ConstructorInvocation,
 * ClassInstanceCreation 等方法呼叫
 * 
 * 重新設計原則：
 * 1. 使用新的資料結構來處理鏈式呼叫 (chainedNextCall)
 * 2. 移除複雜的 stack 邏輯
 * 3. 專注於建立清晰的資料結構
 * 4. 讓追蹤邏輯和渲染邏輯分離
 */
public class InvocationHandler {
    private static final Logger logger = LoggerFactory.getLogger(InvocationHandler.class);

    public boolean visit(MethodInvocation node, HandlerContext context) {
        InteractionModel interaction = createInteractionModel(node, context);

        // 檢查當前節點是否在條件表達式中
        boolean isInCondition = isNodeInConditionExpression(node, context);
        boolean isInIfBody = isNodeInIfStatementBody(node, context);

        logger.debug("MethodInvocation: {} - isInCondition: {}, isInIfBody: {}, currentInConditionEvaluation: {}",
                node.getName().getIdentifier(), isInCondition, isInIfBody, context.isInConditionEvaluation());

        if (isInCondition) {
            context.setInConditionEvaluation(true);
        } else if (isInIfBody) {
            context.setInConditionEvaluation(false);
        }

        // 檢查是否為鏈式呼叫的一部分
        Expression expression = node.getExpression();
        if (expression != null) {
            InteractionModel parentInteraction = context.getNodeToInteractionMap().get(expression);
            if (parentInteraction != null) {
                // 這是鏈式呼叫的後續環節，將當前互動設定為父互動的 nextChainedCall
                parentInteraction.setNextChainedCall(interaction);
            } else {
                // 如果找不到父互動，則將當前互動作為頂層互動處理
                addInteractionToContext(interaction, context);
            }
        } else {
            // 沒有 expression，通常意味著是 "this." 的隱式呼叫，視為頂層互動
            addInteractionToContext(interaction, context);
        }

        // 將當前節點和互動模型儲存到 map 中，以便後續鏈式呼叫查找
        context.getNodeToInteractionMap().put(node, interaction);

        // 檢查是否被賦值給變數
        ASTNode parent = node.getParent();
        if (parent instanceof VariableDeclarationFragment) {
            VariableDeclarationFragment fragment = (VariableDeclarationFragment) parent;
            if (node.equals(fragment.getInitializer())) {
                interaction.setAssignedToVariable(fragment.getName().getIdentifier());
            }
        }

        return true;
    }

    public void endVisit(MethodInvocation node, HandlerContext context) {
        context.getNodeToInteractionMap().remove(node);

        // 如果當前節點在條件表達式中，檢查是否需要重置條件評估狀態
        if (context.isInConditionEvaluation()) {
            ASTNode currentConditionExpression = context.getCurrentConditionExpression();
            if (currentConditionExpression != null && node == currentConditionExpression) {
                // 如果這是條件表達式本身，重置條件評估狀態
                context.setInConditionEvaluation(false);
            }
        }
    }

    public boolean visit(ConstructorInvocation node, HandlerContext context) {
        InteractionModel interaction = createConstructorInteractionModel(node, context);

        // 由於 ConstructorInvocation 沒有 expression，它通常是獨立的建構，不直接作為鏈式呼叫的後續環節
        // 因此直接添加到上下文中
        addInteractionToContext(interaction, context);

        // 將當前節點和互動模型儲存到 map 中，以便 ClassInstanceCreation 查找 (如果它作為一個鏈的一部分)
        context.getNodeToInteractionMap().put(node, interaction);

        // 檢查是否被賦值給變數
        ASTNode parent = node.getParent();
        if (parent instanceof VariableDeclarationFragment) {
            VariableDeclarationFragment fragment = (VariableDeclarationFragment) parent;
            if (node.equals(fragment.getInitializer())) {
                interaction.setAssignedToVariable(fragment.getName().getIdentifier());
            }
        }

        return true;
    }

    public void endVisit(ConstructorInvocation node, HandlerContext context) {
        context.getNodeToInteractionMap().remove(node);
    }

    public boolean visit(ClassInstanceCreation node, HandlerContext context) {
        InteractionModel interaction = createClassInstanceInteractionModel(node, context);

        // 由於 ClassInstanceCreation 本身代表一個實例化，它的 expression 通常是 null 或 package.class 名稱
        // 它會作為一個新的頂層互動，或者作為一個 MethodInvocation 的 expression
        addInteractionToContext(interaction, context);

        // 將當前節點和互動模型儲存到 map 中，以便後續鏈式呼叫查找
        context.getNodeToInteractionMap().put(node, interaction);

        // 檢查是否被賦值給變數
        ASTNode parent = node.getParent();
        if (parent instanceof VariableDeclarationFragment) {
            VariableDeclarationFragment fragment = (VariableDeclarationFragment) parent;
            if (node.equals(fragment.getInitializer())) {
                interaction.setAssignedToVariable(fragment.getName().getIdentifier());
            }
        }

        return true;
    }

    public void endVisit(ClassInstanceCreation node, HandlerContext context) {
        context.getNodeToInteractionMap().remove(node);
    }

    /**
     * 輔助方法：將 InteractionModel 添加到適當的上下文 (MethodGroup 或 ControlFlowFragment)
     */
    private void addInteractionToContext(InteractionModel interaction, HandlerContext context) {
        if (context.getCurrentControlFlowFragment() != null) {
            // 根據當前是否在條件評估階段來決定添加到哪個列表
            if (context.isInConditionEvaluation()) {
                context.getCurrentControlFlowFragment().addConditionInteraction(interaction);
            } else {
                context.getCurrentControlFlowFragment().addContentInteraction(interaction);
            }
        } else if (context.getCurrentMethodGroup() != null) {
            context.getCurrentMethodGroup().addInteraction(interaction);
        }
    }

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

    /**
     * 檢查節點是否在條件表達式中
     */
    private boolean isNodeInConditionExpression(ASTNode node, HandlerContext context) {
        ASTNode currentConditionExpression = context.getCurrentConditionExpression();
        if (currentConditionExpression == null) {
            return false;
        }

        // 檢查當前節點是否是條件表達式的子節點
        return isDescendantOf(node, currentConditionExpression);
    }

    /**
     * 檢查節點是否在 if 語句主體中（而不是在條件表達式中）
     */
    private boolean isNodeInIfStatementBody(ASTNode node, HandlerContext context) {
        ASTNode currentConditionExpression = context.getCurrentConditionExpression();
        if (currentConditionExpression == null) {
            return false;
        }

        // 找到包含條件表達式的 IfStatement
        ASTNode ifStatement = currentConditionExpression.getParent();
        if (!(ifStatement instanceof IfStatement)) {
            return false;
        }

        IfStatement ifStmt = (IfStatement) ifStatement;

        // 檢查節點是否在 if 語句主體中
        return isDescendantOf(node, ifStmt.getThenStatement()) ||
                (ifStmt.getElseStatement() != null && isDescendantOf(node, ifStmt.getElseStatement()));
    }

    /**
     * 檢查 node 是否是 ancestor 的後代節點
     */
    private boolean isDescendantOf(ASTNode node, ASTNode ancestor) {
        ASTNode current = node.getParent();
        while (current != null) {
            if (current == ancestor) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }
}
