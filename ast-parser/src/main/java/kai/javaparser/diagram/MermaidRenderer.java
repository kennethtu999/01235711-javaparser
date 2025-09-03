package kai.javaparser.diagram;

import kai.javaparser.diagram.output.MermaidOutput;
import kai.javaparser.model.ControlFlowFragment;
import kai.javaparser.model.DiagramNode;
import kai.javaparser.model.InteractionModel;
import kai.javaparser.model.TraceResult;

/**
 * Mermaid 渲染器：
 * 負責將 TraceResult 轉換為 Mermaid 序列圖語法字串
 */
public class MermaidRenderer {
    private final SequenceOutputConfig config;
    private MermaidOutput output;

    public MermaidRenderer(SequenceOutputConfig config) {
        this.config = config;
    }

    public String render(TraceResult traceResult) {
        this.output = new MermaidOutput();

        // 1. 設定進入點
        output.addActor("User");
        String entryClassFqn = AstClassUtil.getClassFqnFromMethodFqn(traceResult.getEntryPointMethodFqn());
        String methodSignature = AstClassUtil.getMethodSignature(traceResult.getEntryPointMethodFqn()).replaceAll(
                "\\(.*",
                "");
        String entryClassId = AstClassUtil.safeMermaidId(entryClassFqn);

        output.addEntryPointCall("User", entryClassId, methodSignature);
        output.activate(entryClassId);

        // 2. 遞迴遍歷呼叫樹
        for (DiagramNode node : traceResult.getSequenceNodes()) {
            renderNode(node, entryClassId);
        }

        output.deactivate(entryClassId);

        return output.toString();
    }

    private void renderNode(DiagramNode node, String callerId) {
        if (node instanceof InteractionModel) {
            renderInteraction(false, (InteractionModel) node, callerId);
        } else if (node instanceof ControlFlowFragment) {
            renderControlFlow((ControlFlowFragment) node, callerId, true); // Initial call, always true for top-level
        }
    }

    private void renderInteraction(boolean isConditionEvaluation, InteractionModel interaction, String callerId) {
        // 使用新的資料結構來處理渲染邏輯
        if (interaction.getNextChainedCall() != null) {
            // 這是一個鏈式呼叫，需要按照正確的順序渲染
            renderChainedInteraction(isConditionEvaluation, interaction, callerId);
        } else {
            // 單一呼叫
            renderSingleInteraction(isConditionEvaluation, interaction, callerId);
        }
    }

    private void renderSingleInteraction(boolean isConditionEvaluation, InteractionModel interaction, String callerId) {
        String calleeClassFqn = interaction.getCallee() != null ? interaction.getCallee() : "";
        String calleeId = AstClassUtil.safeMermaidId(calleeClassFqn);

        // 只有在未被過濾器排除的情況下才渲染此交互
        if (!config.getFilter().shouldExclude(calleeClassFqn, interaction.getMethodName(), null)) {
            output.addParticipant(calleeId, calleeClassFqn);
            output.addCall(callerId, calleeId, interaction.getMethodName(),
                    interaction.getArguments(), interaction.getAssignedToVariable(), isConditionEvaluation,
                    interaction.getReturnValue());

            // 只有在有內部呼叫時才添加 activate/deactivate
            boolean hasInternalCalls = !config.isHideDetailsInChainExpression() &&
                    interaction.getInternalCalls() != null &&
                    !interaction.getInternalCalls().isEmpty();

            if (hasInternalCalls) {
                output.activate(calleeId);

                // 渲染內部呼叫
                for (DiagramNode internalCall : interaction.getInternalCalls()) {
                    renderNode(internalCall, calleeId);
                }

                output.deactivate(calleeId);
            }
        }
    }

    private void renderChainedInteraction(boolean isConditionEvaluation, InteractionModel interaction,
            String callerId) {
        // 對於鏈式呼叫 a.b().c()，正確的順序應該是：
        // 1. callerId -> a : b()
        // 2. a -> returnType : c()

        String calleeClassFqn = interaction.getCallee() != null ? interaction.getCallee() : "";
        String calleeId = AstClassUtil.safeMermaidId(calleeClassFqn);

        // 只有在未被過濾器排除的情況下才渲染此交互
        if (!config.getFilter().shouldExclude(calleeClassFqn, interaction.getMethodName(), null)) {
            output.addParticipant(calleeId, calleeClassFqn);
            output.addCall(callerId, calleeId, interaction.getMethodName(),
                    interaction.getArguments(), interaction.getAssignedToVariable(), isConditionEvaluation,
                    interaction.getReturnValue());

            // 檢查是否有內部呼叫或鏈式呼叫的下一個環節
            boolean hasInternalCalls = !config.isHideDetailsInChainExpression() &&
                    interaction.getInternalCalls() != null &&
                    !interaction.getInternalCalls().isEmpty();
            boolean hasNextChainedCall = interaction.getNextChainedCall() != null;

            if (hasInternalCalls || hasNextChainedCall) {
                output.activate(calleeId);

                // 渲染內部呼叫（如果配置允許）
                if (hasInternalCalls) {
                    for (DiagramNode internalCall : interaction.getInternalCalls()) {
                        renderNode(internalCall, calleeId);
                    }
                }

                // 遞迴渲染鏈式呼叫的下一個環節
                if (hasNextChainedCall) {
                    renderInteraction(isConditionEvaluation, interaction.getNextChainedCall(), calleeId);
                }

                output.deactivate(calleeId);
            }
        }
    }

    private void renderControlFlow(ControlFlowFragment fragment, String callerId, boolean isFirstAlternativeInBlock) {
        String condition = fragment.getCondition() != null ? fragment.getCondition() : "";

        // 開始控制流程片段
        switch (fragment.getType()) {
            case ALTERNATIVE:
                if (isFirstAlternativeInBlock) {
                    output.addAltFragment(condition);
                } else {
                    if (condition.isEmpty()) {
                        output.addElseFragment(); // Assuming a new method for 'else'
                    } else {
                        output.addElseIfFragment(condition); // Assuming a new method for 'else if'
                    }
                }
                break;
            case LOOP:
                output.addLoopFragment(condition);
                break;
            case OPTIONAL:
                output.addOptFragment(condition);
                break;
        }

        // 渲染條件評估互動 (只有在不隱藏細節的情況下)
        if (fragment.getConditionInteractions() != null && !config.isHideDetailsInConditionals()) {
            for (InteractionModel interaction : fragment.getConditionInteractions()) {
                // 渲染條件評估的互動節點
                renderInteraction(true, interaction, callerId);
            }
        }

        // 渲染內容執行互動 (只有在不隱藏細節的情況下)
        if (fragment.getContentInteractions() != null && !config.isHideDetailsInConditionals()) {
            for (InteractionModel interaction : fragment.getContentInteractions()) {
                // 渲染內容執行的互動節點
                renderInteraction(false, interaction, callerId);
            }
        }

        // 渲染 alternatives
        if (fragment.getAlternatives() != null) {
            boolean firstAlternative = true;
            for (ControlFlowFragment alternative : fragment.getAlternatives()) {
                renderControlFlow(alternative, callerId, firstAlternative);
                firstAlternative = false;
            }
        }

        // 結束控制流程片段
        output.endFragment();
    }
}
