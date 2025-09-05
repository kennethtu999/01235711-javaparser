package kai.javaparser.handler;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.WhileStatement;

import kai.javaparser.model.ControlFlowFragment;

/**
 * ControlFlowHandler 專門處理 if, for, while 等控制流邏輯
 * 
 * 重新設計原則：
 * 1. 移除複雜的 stack 邏輯
 * 2. 專注於建立清晰的資料結構
 * 3. 讓追蹤邏輯和渲染邏輯分離
 */
public class ControlFlowHandler {
    public boolean visit(IfStatement node, HandlerContext context) {
        ControlFlowFragment fragment = new ControlFlowFragment();
        fragment.setSequenceId(String.valueOf(context.getAndIncrementSequenceCounter()));
        fragment.setType(ControlFlowFragment.ControlFlowType.ALTERNATIVE);
        fragment.setCondition(node.getExpression().toString());
        fragment.setStartLineNumber(context.getCompilationUnit().getLineNumber(node.getStartPosition()));

        // 設置控制流程片段的上下文
        if (context.getCurrentMethodGroup() != null) {
            fragment.setCallerClass(context.getCurrentMethodGroup().getClassName());
            fragment.setCallerMethod(context.getCurrentMethodGroup().getMethodName());
            fragment.setContextPath(context.getCurrentMethodGroup().getFullMethodName());
        }

        // 簡化邏輯：直接添加到當前方法組或控制流程片段
        if (context.getCurrentControlFlowFragment() != null) {
            context.getCurrentControlFlowFragment().addAlternative(fragment);
        } else if (context.getCurrentMethodGroup() != null) {
            context.getCurrentMethodGroup().addControlFlowFragment(fragment);
        }

        // 設置當前控制流程片段，但不使用複雜的 stack
        context.setCurrentControlFlowFragment(fragment);

        // 設置條件表達式節點，以便後續追蹤
        context.setCurrentConditionExpression(node.getExpression());

        return true;
    }

    public void endVisit(IfStatement node, HandlerContext context) {
        ControlFlowFragment fragment = context.getCurrentControlFlowFragment();
        if (fragment != null) {
            fragment.setEndLineNumber(
                    context.getCompilationUnit().getLineNumber(node.getStartPosition() + node.getLength()));

            // 處理 else 部分
            if (node.getElseStatement() != null) {
                ControlFlowFragment elseFragment = new ControlFlowFragment();
                elseFragment.setSequenceId(String.valueOf(context.getAndIncrementSequenceCounter()));
                elseFragment.setType(ControlFlowFragment.ControlFlowType.OPTIONAL);
                elseFragment.setCondition("[else]");
                elseFragment.setStartLineNumber(
                        context.getCompilationUnit().getLineNumber(node.getElseStatement().getStartPosition()));
                elseFragment.setEndLineNumber(context.getCompilationUnit().getLineNumber(
                        node.getElseStatement().getStartPosition() + node.getElseStatement().getLength()));

                // 設置else片段的上下文
                if (context.getCurrentMethodGroup() != null) {
                    elseFragment.setCallerClass(context.getCurrentMethodGroup().getClassName());
                    elseFragment.setCallerMethod(context.getCurrentMethodGroup().getMethodName());
                    elseFragment.setContextPath(context.getCurrentMethodGroup().getFullMethodName());
                }

                fragment.addAlternative(elseFragment);
            }
        }

        // 簡化：重置當前控制流程片段和條件表達式
        context.setCurrentControlFlowFragment(null);
        context.setCurrentConditionExpression(null);
    }

    public boolean visit(ForStatement node, HandlerContext context) {
        return handleLoopFragment(node, "for loop", context);
    }

    public boolean visit(EnhancedForStatement node, HandlerContext context) {
        String condition = node.getParameter().getName().getIdentifier() + " : " + node.getExpression().toString();
        return handleLoopFragment(node, condition, context);
    }

    public boolean visit(WhileStatement node, HandlerContext context) {
        ControlFlowFragment fragment = new ControlFlowFragment();
        fragment.setSequenceId(String.valueOf(context.getAndIncrementSequenceCounter()));
        fragment.setType(ControlFlowFragment.ControlFlowType.LOOP);
        fragment.setCondition("while (" + node.getExpression().toString() + ")");
        fragment.setStartLineNumber(context.getCompilationUnit().getLineNumber(node.getStartPosition()));

        // 設置控制流程片段的上下文
        if (context.getCurrentMethodGroup() != null) {
            fragment.setCallerClass(context.getCurrentMethodGroup().getClassName());
            fragment.setCallerMethod(context.getCurrentMethodGroup().getMethodName());
            fragment.setContextPath(context.getCurrentMethodGroup().getFullMethodName());
        }

        // 簡化邏輯：直接添加到當前方法組或控制流程片段
        if (context.getCurrentControlFlowFragment() != null) {
            context.getCurrentControlFlowFragment().addAlternative(fragment);
        } else if (context.getCurrentMethodGroup() != null) {
            context.getCurrentMethodGroup().addControlFlowFragment(fragment);
        }

        // 設置當前控制流程片段
        context.setCurrentControlFlowFragment(fragment);
        return true;
    }

    public void endVisit(ForStatement node, HandlerContext context) {
        endVisitLoopFragment(node, context);
    }

    public void endVisit(EnhancedForStatement node, HandlerContext context) {
        endVisitLoopFragment(node, context);
    }

    public void endVisit(WhileStatement node, HandlerContext context) {
        ControlFlowFragment fragment = context.getCurrentControlFlowFragment();
        if (fragment != null) {
            fragment.setEndLineNumber(
                    context.getCompilationUnit().getLineNumber(node.getStartPosition() + node.getLength()));
        }

        // 簡化：重置當前控制流程片段
        context.setCurrentControlFlowFragment(null);
    }

    // 抽出共用的邏輯到一個輔助方法
    private boolean handleLoopFragment(ASTNode node, String conditionDescription, HandlerContext context) {
        ControlFlowFragment fragment = new ControlFlowFragment();
        fragment.setSequenceId(String.valueOf(context.getAndIncrementSequenceCounter()));
        fragment.setType(ControlFlowFragment.ControlFlowType.LOOP);
        fragment.setCondition(conditionDescription);
        fragment.setStartLineNumber(context.getCompilationUnit().getLineNumber(node.getStartPosition()));

        // 設置控制流程片段的上下文
        if (context.getCurrentMethodGroup() != null) {
            fragment.setCallerClass(context.getCurrentMethodGroup().getClassName());
            fragment.setCallerMethod(context.getCurrentMethodGroup().getMethodName());
            fragment.setContextPath(context.getCurrentMethodGroup().getFullMethodName());
        }

        // 簡化邏輯：直接添加到當前方法組或控制流程片段
        if (context.getCurrentControlFlowFragment() != null) {
            context.getCurrentControlFlowFragment().addAlternative(fragment);
        } else if (context.getCurrentMethodGroup() != null) {
            context.getCurrentMethodGroup().addControlFlowFragment(fragment);
        }

        // 設置當前控制流程片段
        context.setCurrentControlFlowFragment(fragment);
        return true;
    }

    // 抽出共用的邏輯到一個輔助方法
    private void endVisitLoopFragment(ASTNode node, HandlerContext context) {
        ControlFlowFragment fragment = context.getCurrentControlFlowFragment();
        if (fragment != null) {
            fragment.setEndLineNumber(
                    context.getCompilationUnit().getLineNumber(node.getStartPosition() + node.getLength()));
        }

        // 簡化：重置當前控制流程片段
        context.setCurrentControlFlowFragment(null);
    }
}
