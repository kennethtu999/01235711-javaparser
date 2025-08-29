package kai.javaparser.diagram;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kai.javaparser.diagram.idx.AstIndex;
import kai.javaparser.diagram.output.MermaidOutput;
import kai.javaparser.model.ControlFlowFragment;
import kai.javaparser.model.DiagramNode; // Import DiagramNode
import kai.javaparser.model.FileAstData;
import kai.javaparser.model.InteractionModel;
import kai.javaparser.model.MethodGroup;
import lombok.Builder;

/**
 * 序列圖生成器：
 * 1. 載入 AST 索引
 * 2. 遞迴追蹤方法呼叫
 * 3. 產生 Mermaid 語法（安全 ID + 可還原 FQN）
 */
@Builder
public class SequenceDiagramGenerator {
    private static final Logger logger = LoggerFactory.getLogger(SequenceDiagramGenerator.class);

    private SequenceDiagramOutputConfig config;
    private String astDir;
    private AstIndex astIndex;

    // 輸出結果
    private MermaidOutput output;

    public void startup() {
        if (astIndex != null) {
            return;
        }

        try {
            astIndex = new AstIndex(Path.of(astDir));
            astIndex.loadOrBuild();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("執行期間發生錯誤: " + e.getMessage(), e);
        }
    }

    public String generate(String entryPointMethodFqn) {
        startup();

        output = new MermaidOutput();

        // 固定起點 Actor
        output.addActor("User");
        String entryClassFqn = AstClassUtil.getClassFqnFromMethodFqn(entryPointMethodFqn);
        String methodSignature = AstClassUtil.getMethodSignature(entryPointMethodFqn);
        output.addEntryPointCall("User", AstClassUtil.safeMermaidId(entryClassFqn), methodSignature);

        // 將新參數傳遞給 traceMethod
        traceMethod(entryPointMethodFqn, new HashSet<>(), config.getDepth());

        logger.info("\n---O Mermaid 序列圖語法 ---");
        logger.info(output.toString());

        return output.toString();

    }

    /**
     * 遞迴追蹤方法呼叫 (修改簽章)
     */
    private boolean traceMethod(String methodFqn,
            Set<String> callStack,
            int depth) {

        if (depth <= 0)
            return false;

        if (!isTraceable(methodFqn, callStack))
            return false;

        String classFqn = AstClassUtil.getClassFqnFromMethodFqn(methodFqn);
        FileAstData astData = astIndex.getAstDataByClassFqn(classFqn);
        if (astData == null)
            return false;

        List<InteractionModel> validInvocations = astData.findMethodNode(methodFqn)
                .map(astData::findMethodInvocations)
                .orElse(new ArrayList<>()).stream()
                .filter(inv -> inv.getMethodName() != null
                        && !config.getFilter().shouldExclude(inv.getCallee(), inv.getMethodName(), astIndex))
                .collect(Collectors.toList());

        // 獲取當前方法的 MethodGroup
        MethodGroup currentMethodGroup = astData.getSequenceDiagramData()
                .findMethodGroup(AstClassUtil.getMethodSignature(methodFqn).split("\\(")[0]);
        List<ControlFlowFragment> controlFlowFragments = new ArrayList<>();
        if (currentMethodGroup != null && currentMethodGroup.getControlFlowFragments() != null) {
            controlFlowFragments.addAll(currentMethodGroup.getControlFlowFragments());
        }

        if (validInvocations.isEmpty() && controlFlowFragments.isEmpty())
            return false;

        callStack.add(methodFqn);

        String callerId = AstClassUtil.safeMermaidId(classFqn);
        // 不要重複添加 participant，因為 addEntryPointCall 已經添加過了
        output.activate(callerId);

        // 合併所有 DiagramNode 並按行號排序
        List<DiagramNode> sortedNodes = new ArrayList<>();
        sortedNodes.addAll(validInvocations);
        sortedNodes.addAll(controlFlowFragments);
        sortedNodes.sort(Comparator.comparingInt(DiagramNode::getStartLineNumber));

        // 處理排序後的節點
        for (DiagramNode node : sortedNodes) {
            if (node instanceof ControlFlowFragment) {
                // 將新參數傳遞下去
                traceControlFlowFragment((ControlFlowFragment) node, astIndex, output, callStack,
                        config, depth, callerId);
            } else if (node instanceof InteractionModel) {
                InteractionModel invocation = (InteractionModel) node;
                String calleeClassFqn = invocation.getCallee() != null ? invocation.getCallee() : classFqn;
                String calleeId = AstClassUtil.safeMermaidId(calleeClassFqn);

                output.addParticipant(calleeId, calleeClassFqn);
                output.addCall(callerId, calleeId, invocation.getMethodName(), invocation.getArguments(),
                        invocation.getAssignedToVariable());

                // 遞迴追蹤被呼叫的方法 (也要傳遞新參數)
                String fullCalleeFqn = calleeClassFqn + "." + invocation.getMethodName();
                traceMethod(fullCalleeFqn, callStack, depth - 1);
            }
        }

        output.deactivate(callerId);
        callStack.remove(methodFqn);
        return true;
    }

    /**
     * 處理控制流程片段 (修改簽章並加入核心邏輯)
     */
    private void traceControlFlowFragment(ControlFlowFragment fragment,
            AstIndex astIndex, MermaidOutput output, Set<String> callStack, SequenceDiagramOutputConfig config,
            int depth, String parentCallerId) { // <-- 新增參數

        if (depth <= 0) {
            return;
        }

        String condition = fragment.getCondition() != null ? fragment.getCondition() : "";

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

        // --- START OF FIX ---
        // 修正：合併 interactions 和 alternatives，並按行號排序
        List<DiagramNode> sortedNodes = new ArrayList<>();
        if (fragment.getInteractions() != null) {
            sortedNodes.addAll(fragment.getInteractions());
        }
        if (fragment.getAlternatives() != null) {
            sortedNodes.addAll(fragment.getAlternatives());
        }
        sortedNodes.sort(Comparator.comparingInt(DiagramNode::getStartLineNumber));

        // 依排序後的順序處理所有節點
        for (DiagramNode node : sortedNodes) {
            // =======================================================
            // ==================== 核心修改點 =====================
            // =======================================================
            if (node instanceof InteractionModel) {
                InteractionModel node0 = (InteractionModel) node;

                Stack<InteractionModel> queue = new Stack<>();
                queue.add(node0);

                // 如果有指定，就把所有中間節點都放進去
                if (!config.isHideDetailsInConditionals()) {
                    List<InteractionModel> child = node0.getChildren();
                    while (child != null && !child.isEmpty()) {
                        InteractionModel node1 = child.get(0);
                        queue.add(node1);
                        child = node1.getChildren();
                    }
                }

                handleInteractionMode2(queue, queue.pop(), callStack, depth, null, null);

            } else if (node instanceof ControlFlowFragment) {
                // 遞迴處理巢狀的控制流程片段 (傳遞開關)
                traceControlFlowFragment((ControlFlowFragment) node, astIndex, output, callStack,
                        config, depth,
                        parentCallerId);
            }
        }

        output.endFragment();
    }

    /**
     * 處理 InteractionModel 的巢狀結構
     * EX: accountItem.getAllowQuery().intValue()
     * 
     */
    private void handleInteractionMode2(Stack<InteractionModel> queue, InteractionModel currentNode,
            Set<String> callStack, int depth, String callerFqn, String calleeFqn) {

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

        handleInteractionMode(currentNode, callerFqn, calleeFqn, callStack, depth);
        if (!queue.isEmpty()) {
            handleInteractionMode2(queue, queue.pop(), callStack, depth, calleeFqn, null);
        }

        if (activateCallId != null) {
            output.deactivate(activateCallId);
        }
    }

    private void handleInteractionMode(InteractionModel node, String callerFqn, String calleeFqn, Set<String> callStack,
            int depth) {

        if (config.isHideDetailsInConditionals()) {
            return;
        }

        // --- 以下是原本的邏輯 ---
        InteractionModel interaction = (InteractionModel) node;

        String calleeId = AstClassUtil.safeMermaidId(calleeFqn);
        String callerId = AstClassUtil.safeMermaidId(callerFqn);

        if (!config.getFilter().shouldExclude(calleeFqn, interaction.getMethodName(), astIndex)) {
            output.addParticipant(calleeId, calleeFqn);
            output.addCall(callerId, calleeId, interaction.getMethodName(),
                    interaction.getArguments(), interaction.getReturnValue() /** interaction.getAssignedToVariable() **/
            );
        }

        String fullCalleeFqn = calleeFqn + "." + interaction.getMethodName();
        // 遞迴呼叫時也要傳遞開關
        traceMethod(fullCalleeFqn, callStack, depth - 1);
    }

    private boolean isTraceable(String methodFqn, Set<String> callStack) {
        if (callStack.contains(methodFqn))
            return false;
        if (!methodFqn.startsWith(config.getBasePackage()))
            return false;
        return !config.getFilter().shouldExclude(methodFqn, astIndex);
    }
}
