package kai.javaparser.handler;

import kai.javaparser.model.*;
import org.eclipse.jdt.core.dom.CompilationUnit;
import java.util.Map;
import java.util.Stack;
import java.util.HashMap;

/**
 * HandlerContext 用於存放所有 Visitor 在遍歷期間需要共享的狀態
 * 這可以避免在方法之間傳遞大量參數
 */
public class HandlerContext {
    // 從 EnhancedInteractionModelVisitor 移過來的狀態
    private final SequenceDiagramData sequenceData;
    private final CompilationUnit compilationUnit;
    private final Stack<InteractionModel> interactionStack;
    private final Stack<ControlFlowFragment> controlFlowStack;
    private final Map<String, Integer> variableInstanceCounters;

    private int sequenceCounter = 1;
    private ControlFlowFragment currentControlFlowFragment = null;
    private MethodGroup currentMethodGroup = null;
    private String currentClassName = null;

    public HandlerContext(SequenceDiagramData sequenceData, CompilationUnit compilationUnit) {
        this.sequenceData = sequenceData;
        this.compilationUnit = compilationUnit;
        // 初始化 Stacks 和 Maps
        this.interactionStack = new Stack<>();
        this.controlFlowStack = new Stack<>();
        this.variableInstanceCounters = new HashMap<>();
    }

    // Getters and Setters
    public SequenceDiagramData getSequenceData() {
        return sequenceData;
    }

    public CompilationUnit getCompilationUnit() {
        return compilationUnit;
    }

    public Stack<InteractionModel> getInteractionStack() {
        return interactionStack;
    }

    public Stack<ControlFlowFragment> getControlFlowStack() {
        return controlFlowStack;
    }

    public Map<String, Integer> getVariableInstanceCounters() {
        return variableInstanceCounters;
    }

    public int getSequenceCounter() {
        return sequenceCounter;
    }

    public int getAndIncrementSequenceCounter() {
        return sequenceCounter++;
    }

    public ControlFlowFragment getCurrentControlFlowFragment() {
        return currentControlFlowFragment;
    }

    public void setCurrentControlFlowFragment(ControlFlowFragment currentControlFlowFragment) {
        this.currentControlFlowFragment = currentControlFlowFragment;
    }

    public MethodGroup getCurrentMethodGroup() {
        return currentMethodGroup;
    }

    public void setCurrentMethodGroup(MethodGroup currentMethodGroup) {
        this.currentMethodGroup = currentMethodGroup;
    }

    public String getCurrentClassName() {
        return currentClassName;
    }

    public void setCurrentClassName(String currentClassName) {
        this.currentClassName = currentClassName;
    }
}
