package kai.javaparser.diagram;

import java.util.List;

import kai.javaparser.model.*;
import kai.javaparser.diagram.output.MermaidOutput;

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
        String methodSignature = AstClassUtil.getMethodSignature(traceResult.getEntryPointMethodFqn());
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
            renderInteraction((InteractionModel) node, callerId);
        } else if (node instanceof ControlFlowFragment) {
            renderControlFlow((ControlFlowFragment) node, callerId);
        }
    }

    private void renderInteraction(InteractionModel interaction, String callerId) {
        // 處理鏈式呼叫：需要按照正確的順序渲染
        if (interaction.getChildren() != null && !interaction.getChildren().isEmpty()) {
            // 這是一個鏈式呼叫，需要先渲染當前呼叫，然後渲染子呼叫
            renderChainedInteraction(interaction, callerId);
        } else {
            // 單一呼叫
            renderSingleInteraction(interaction, callerId);
        }
    }

    private void renderSingleInteraction(InteractionModel interaction, String callerId) {
        String calleeClassFqn = interaction.getCallee() != null ? interaction.getCallee() : "";
        String calleeId = AstClassUtil.safeMermaidId(calleeClassFqn);

        // 添加參與者並進行方法呼叫
        output.addParticipant(calleeId, calleeClassFqn);
        output.addCall(callerId, calleeId, interaction.getMethodName(),
                interaction.getArguments(), interaction.getAssignedToVariable());

        output.activate(calleeId);
        output.deactivate(calleeId);
    }

    private void renderChainedInteraction(InteractionModel interaction, String callerId) {
        // 對於鏈式呼叫 a.b().c()，正確的順序應該是：
        // 1. callerId -> a : b()
        // 2. a -> returnType : c()

        String calleeClassFqn = interaction.getCallee() != null ? interaction.getCallee() : "";
        String calleeId = AstClassUtil.safeMermaidId(calleeClassFqn);

        // 添加參與者並進行方法呼叫
        output.addParticipant(calleeId, calleeClassFqn);
        output.addCall(callerId, calleeId, interaction.getMethodName(),
                interaction.getArguments(), interaction.getAssignedToVariable());

        output.activate(calleeId);

        // 遞迴渲染子節點（鏈式呼叫的下一個環節）
        for (InteractionModel child : interaction.getChildren()) {
            renderNode(child, calleeId); // 注意：此時的 caller 是 callee
        }

        output.deactivate(calleeId);
    }

    private void renderControlFlow(ControlFlowFragment fragment, String callerId) {
        String condition = fragment.getCondition() != null ? fragment.getCondition() : "";

        // 開始控制流程片段
        switch (fragment.getType()) {
            case ALTERNATIVE:
                output.addAltFragment(condition);
                break;
            case LOOP:
                output.addLoopFragment(condition);
                break;
            case OPTIONAL:
                output.addOptFragment(condition);
                break;
        }

        // 渲染 interactions (只有在不隱藏細節的情況下)
        if (fragment.getInteractions() != null && !config.isHideDetailsInConditionals()) {
            for (InteractionModel interaction : fragment.getInteractions()) {
                // 對於控制流程中的鏈式呼叫，需要使用 Stack 來處理
                renderInteractionWithChain(interaction, callerId);
            }
        }

        // 渲染 alternatives
        if (fragment.getAlternatives() != null) {
            for (ControlFlowFragment alternative : fragment.getAlternatives()) {
                renderNode(alternative, callerId);
            }
        }

        // 結束控制流程片段
        output.endFragment();
    }

    /**
     * 專門處理控制流程中的鏈式呼叫，模仿原始的 handleInteractionMode2 邏輯
     */
    private void renderInteractionWithChain(InteractionModel interaction, String callerId) {
        java.util.Stack<InteractionModel> stack = new java.util.Stack<>();
        stack.push(interaction);

        // 如果有指定，就把所有中間節點都放進去（模仿原始邏輯）
        if (!config.isHideDetailsInConditionals()) {
            List<InteractionModel> child = interaction.getChildren();
            while (child != null && !child.isEmpty()) {
                InteractionModel node1 = child.get(0);
                stack.push(node1);
                child = node1.getChildren();
            }
        }

        // 使用 Stack 反向處理鏈式呼叫
        renderInteractionChainFromStack(stack, stack.pop(), callerId, null);
    }

    /**
     * 模仿原始的 handleInteractionMode2 邏輯
     */
    private void renderInteractionChainFromStack(java.util.Stack<InteractionModel> stack,
            InteractionModel currentNode,
            String callerFqn, String calleeFqn) {

        String activateCallId = null;
        if (callerFqn == null) {
            callerFqn = currentNode.getCaller();
        } else {
            activateCallId = AstClassUtil.safeMermaidId(callerFqn);
            output.activate(activateCallId);
        }

        if (calleeFqn == null) {
            calleeFqn = currentNode.getCallee();
        }

        // 渲染當前的交互
        renderSingleInteractionForChain(currentNode, callerFqn, calleeFqn);

        // 遞迴處理 Stack 中的下一個元素
        if (!stack.isEmpty()) {
            renderInteractionChainFromStack(stack, stack.pop(), calleeFqn, null);
        }

        if (activateCallId != null) {
            output.deactivate(activateCallId);
        }
    }

    /**
     * 為鏈式呼叫渲染單一交互
     */
    private void renderSingleInteractionForChain(InteractionModel interaction, String callerFqn, String calleeFqn) {
        if (config.isHideDetailsInConditionals()) {
            return;
        }

        String calleeId = AstClassUtil.safeMermaidId(calleeFqn);
        String callerId = AstClassUtil.safeMermaidId(callerFqn);

        output.addParticipant(calleeId, calleeFqn);
        output.addCall(callerId, calleeId, interaction.getMethodName(),
                interaction.getArguments(), interaction.getReturnValue());
    }
}
