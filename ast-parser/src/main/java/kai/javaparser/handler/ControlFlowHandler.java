package kai.javaparser.handler;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kai.javaparser.model.ControlFlowFragment;

/**
 * ControlFlowHandler 專門處理 if, for, while 等控制流邏輯
 */
public class ControlFlowHandler {
    private static final Logger logger = LoggerFactory.getLogger(ControlFlowHandler.class);

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

        if (context.getCurrentControlFlowFragment() != null) {
            context.getCurrentControlFlowFragment().addAlternative(fragment);
        } else if (context.getCurrentMethodGroup() != null) {
            context.getCurrentMethodGroup().addControlFlowFragment(fragment);
        } else {
            // 如果不在方法內，添加到序列數據（向後兼容）
            context.getSequenceData().getAllControlFlowFragments().add(fragment);
        }

        context.getControlFlowStack().push(fragment);
        context.setCurrentControlFlowFragment(fragment);
        return true;
    }

    public void endVisit(IfStatement node, HandlerContext context) {
        if (!context.getControlFlowStack().isEmpty()) {
            ControlFlowFragment fragment = context.getControlFlowStack().pop();
            fragment.setEndLineNumber(
                    context.getCompilationUnit().getLineNumber(node.getStartPosition() + node.getLength()));

            // Note: The 'else' part is typically handled as a separate 'alternative'
            // fragment, but your current structure adds it *into* the 'if' fragment's
            // alternatives.
            // This is a design choice. The code below keeps your current design.
            if (fragment.getType() == ControlFlowFragment.ControlFlowType.ALTERNATIVE &&
                    node.getElseStatement() != null) {
                // Add else alternative
                ControlFlowFragment elseFragment = new ControlFlowFragment();
                elseFragment.setSequenceId(String.valueOf(context.getAndIncrementSequenceCounter()));
                elseFragment.setType(ControlFlowFragment.ControlFlowType.OPTIONAL); // Or another type for 'else'
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
        context.setCurrentControlFlowFragment(
                context.getControlFlowStack().isEmpty() ? null : context.getControlFlowStack().peek());
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

        if (context.getCurrentControlFlowFragment() != null) {
            context.getCurrentControlFlowFragment().addAlternative(fragment);
        } else if (context.getCurrentMethodGroup() != null) {
            context.getCurrentMethodGroup().addControlFlowFragment(fragment);
        } else {
            context.getSequenceData().getAllControlFlowFragments().add(fragment);
        }

        context.getControlFlowStack().push(fragment);
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
        if (!context.getControlFlowStack().isEmpty()) {
            // Fix: Set endLineNumber for WhileStatement
            ControlFlowFragment fragment = context.getControlFlowStack().pop();
            fragment.setEndLineNumber(
                    context.getCompilationUnit().getLineNumber(node.getStartPosition() + node.getLength()));
        }
        context.setCurrentControlFlowFragment(
                context.getControlFlowStack().isEmpty() ? null : context.getControlFlowStack().peek());
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

        if (context.getCurrentControlFlowFragment() != null) {
            context.getCurrentControlFlowFragment().addAlternative(fragment); // Adding a loop as an alternative to its
                                                                              // parent
            // fragment (e.g., if inside an if)
        } else if (context.getCurrentMethodGroup() != null) {
            context.getCurrentMethodGroup().addControlFlowFragment(fragment); // Adding a top-level loop to the method
        } else {
            context.getSequenceData().getAllControlFlowFragments().add(fragment); // Fallback: add to global list
        }

        context.getControlFlowStack().push(fragment);
        context.setCurrentControlFlowFragment(fragment);
        return true;
    }

    // 抽出共用的邏輯到一個輔助方法
    private void endVisitLoopFragment(ASTNode node, HandlerContext context) {
        if (!context.getControlFlowStack().isEmpty()) {
            ControlFlowFragment fragment = context.getControlFlowStack().pop();
            fragment.setEndLineNumber(
                    context.getCompilationUnit().getLineNumber(node.getStartPosition() + node.getLength()));
        }
        context.setCurrentControlFlowFragment(
                context.getControlFlowStack().isEmpty() ? null : context.getControlFlowStack().peek());
    }
}
