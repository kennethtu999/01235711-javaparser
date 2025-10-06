package kai.javaparser.ast.java2ast.handler;

import kai.javaparser.model.*;
import org.eclipse.jdt.core.dom.CompilationUnit;
import java.util.Map;
import java.util.HashMap;
import org.eclipse.jdt.core.dom.ASTNode; // Import ASTNode

/**
 * HandlerContext 用於存放所有 Visitor 在遍歷期間需要共享的狀態
 * 這可以避免在方法之間傳遞大量參數
 * 
 * 重新設計原則：
 * 1. 移除複雜的 stack 結構
 * 2. 簡化上下文管理
 * 3. 專注於必要的狀態追蹤
 */
public class HandlerContext {
    // 從 EnhancedInteractionModelVisitor 移過來的狀態
    private final SequenceDiagramData sequenceData;
    private final CompilationUnit compilationUnit;
    private final Map<String, Integer> variableInstanceCounters;
    // 新增：用於儲存 ASTNode 與其對應的 InteractionModel
    private final Map<ASTNode, InteractionModel> nodeToInteractionMap;

    private int sequenceCounter = 1;
    private ControlFlowFragment currentControlFlowFragment = null;
    private MethodGroup currentMethodGroup = null;
    private String currentClassName = null;

    // 新增：追蹤當前是否在條件評估階段
    private boolean inConditionEvaluation = false;

    // 新增：追蹤當前正在處理的條件表達式節點
    private ASTNode currentConditionExpression = null;

    public HandlerContext(SequenceDiagramData sequenceData, CompilationUnit compilationUnit) {
        this.sequenceData = sequenceData;
        this.compilationUnit = compilationUnit;
        // 初始化 Maps
        this.variableInstanceCounters = new HashMap<>();
        this.nodeToInteractionMap = new HashMap<>(); // 初始化新的 Map
    }

    // Getters and Setters
    public SequenceDiagramData getSequenceData() {
        return sequenceData;
    }

    public CompilationUnit getCompilationUnit() {
        return compilationUnit;
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

    public Map<ASTNode, InteractionModel> getNodeToInteractionMap() {
        return nodeToInteractionMap;
    }

    public boolean isInConditionEvaluation() {
        return inConditionEvaluation;
    }

    public void setInConditionEvaluation(boolean inConditionEvaluation) {
        this.inConditionEvaluation = inConditionEvaluation;
    }

    public ASTNode getCurrentConditionExpression() {
        return currentConditionExpression;
    }

    public void setCurrentConditionExpression(ASTNode currentConditionExpression) {
        this.currentConditionExpression = currentConditionExpression;
    }
}
