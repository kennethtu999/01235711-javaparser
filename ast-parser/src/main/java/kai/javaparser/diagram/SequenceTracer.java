package kai.javaparser.diagram;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kai.javaparser.diagram.idx.AstIndex;
import kai.javaparser.model.ControlFlowFragment;
import kai.javaparser.model.DiagramNode;
import kai.javaparser.model.FileAstData;
import kai.javaparser.model.InteractionModel;
import kai.javaparser.model.MethodGroup;
import kai.javaparser.model.TraceResult;
import lombok.Builder;

/**
 * 序列追蹤器：
 * 1. 載入 AST 索引
 * 2. 遞迴追蹤方法呼叫
 * 3. 建立獨立於輸出格式的呼叫樹資料結構
 * 
 * 重新設計原則：
 * 1. 移除複雜的 stack 邏輯
 * 2. 專注於建立清晰的資料結構
 * 3. 讓追蹤邏輯和渲染邏輯分離
 * 4. 簡化鏈式呼叫的處理
 */
@Builder
public class SequenceTracer {
    private static final Logger logger = LoggerFactory.getLogger(SequenceTracer.class);

    private SequenceOutputConfig config;
    private String astDir;
    private AstIndex astIndex;

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

    public TraceResult trace(String entryPointMethodFqn) {
        startup();

        List<DiagramNode> sequenceNodes = new ArrayList<>();
        traceMethod(entryPointMethodFqn, new HashSet<>(), config.getDepth(), sequenceNodes);

        logger.info("\n---O 序列追蹤完成 ---");
        logger.info("進入點: " + entryPointMethodFqn);
        logger.info("追蹤到的節點數量: " + sequenceNodes.size());

        return new TraceResult(entryPointMethodFqn, sequenceNodes);
    }

    /**
     * 遞迴追蹤方法呼叫並建立 DiagramNode 列表
     * 簡化邏輯：專注於建立清晰的資料結構
     */
    private void traceMethod(String methodFqn, Set<String> callStack, int depth, List<DiagramNode> parentNodes) {
        if (depth <= 0)
            return;
        if (!isTraceable(methodFqn, callStack))
            return;

        String classFqn = AstClassUtil.getClassFqnFromMethodFqn(methodFqn);
        FileAstData astData = astIndex.getAstDataByClassFqn(classFqn);
        if (astData == null)
            return;

        List<InteractionModel> topLevelInvocations = astData.findMethodNode(methodFqn)
                .map(astData::findMethodInvocations)
                .orElse(new ArrayList<>()).stream()
                .filter(inv -> inv.getMethodName() != null
                        && !config.getFilter().shouldExclude(inv.getCallee(), inv.getMethodName(), astIndex))
                .filter(inv -> inv.getNextChainedCall() == null) // 只處理頂層互動 (非鏈式呼叫的後續環節)
                .collect(Collectors.toList());

        // 獲取當前方法的 MethodGroup
        MethodGroup currentMethodGroup = astData.getSequenceDiagramData()
                .findMethodGroup(AstClassUtil.getMethodSignature(methodFqn).split("\\(")[0]);
        List<ControlFlowFragment> controlFlowFragments = new ArrayList<>();
        if (currentMethodGroup != null && currentMethodGroup.getControlFlowFragments() != null) {
            controlFlowFragments.addAll(currentMethodGroup.getControlFlowFragments());
        }

        // 如果沒有任何頂層互動或控制流程，則返回
        if (topLevelInvocations.isEmpty() && controlFlowFragments.isEmpty())
            return;

        callStack.add(methodFqn);

        // 合併所有 DiagramNode 並按行號排序
        List<DiagramNode> sortedNodes = new ArrayList<>();
        sortedNodes.addAll(topLevelInvocations);
        sortedNodes.addAll(controlFlowFragments);
        sortedNodes.sort(Comparator.comparingInt(DiagramNode::getStartLineNumber));

        // 將當前層級的節點加入父節點列表
        parentNodes.addAll(sortedNodes);

        // 為本層的每個節點遞迴尋找下一層 (處理 internalCalls)
        for (DiagramNode node : sortedNodes) {
            if (node instanceof InteractionModel) {
                // 處理鏈式呼叫和內部呼叫
                processInteractionModelRecursive((InteractionModel) node, callStack, depth);
            } else if (node instanceof ControlFlowFragment) {
                processControlFlowNode((ControlFlowFragment) node, callStack, depth);
            }
        }

        callStack.remove(methodFqn);
    }

    /**
     * 遞迴處理 InteractionModel，包括其鏈式呼叫和內部呼叫
     */
    private void processInteractionModelRecursive(InteractionModel interaction, Set<String> callStack, int depth) {
        if (depth <= 0)
            return;

        // 1. 處理當前互動的內部呼叫 (如果它不是鏈式呼叫的後續環節)
        // 我們需要追蹤 callee 方法內部的活動
        String calleeMethodFqn = AstClassUtil.getMethodFqn(interaction.getCallee(), interaction.getMethodName());
        List<DiagramNode> internalChildNodes = new ArrayList<>();
        traceMethod(calleeMethodFqn, callStack, depth - 1, internalChildNodes);

        // 將內部呼叫設定到 internalCalls 中
        if (!internalChildNodes.isEmpty()) {
            for (DiagramNode node : internalChildNodes) {
                interaction.addInternalCall(node);
            }
        }

        // 2. 處理鏈式呼叫的下一個環節
        if (interaction.getNextChainedCall() != null) {
            processInteractionModelRecursive(interaction.getNextChainedCall(), callStack, depth);
        }
    }

    /**
     * 處理控制流程節點
     * 簡化邏輯：專注於建立清晰的資料結構
     */
    private void processControlFlowNode(ControlFlowFragment fragment, Set<String> callStack, int depth) {
        if (depth <= 0)
            return;

        // 合併所有互動和 alternatives，並按行號排序
        List<DiagramNode> sortedNodes = new ArrayList<>();

        // 添加條件評估互動
        if (fragment.getConditionInteractions() != null) {
            sortedNodes.addAll(fragment.getConditionInteractions());
        }

        // 添加內容執行互動
        if (fragment.getContentInteractions() != null) {
            sortedNodes.addAll(fragment.getContentInteractions());
        }

        // 添加 alternatives
        if (fragment.getAlternatives() != null) {
            sortedNodes.addAll(fragment.getAlternatives());
        }

        sortedNodes.sort(Comparator.comparingInt(DiagramNode::getStartLineNumber));

        // 依排序後的順序處理所有節點
        for (DiagramNode node : sortedNodes) {
            if (node instanceof InteractionModel) {
                // 處理鏈式呼叫和內部呼叫
                processInteractionModelRecursive((InteractionModel) node, callStack, depth);
            } else if (node instanceof ControlFlowFragment) {
                // 遞迴處理巢狀的控制流程片段
                processControlFlowNode((ControlFlowFragment) node, callStack, depth);
            }
        }
    }

    private boolean isTraceable(String methodFqn, Set<String> callStack) {
        if (callStack.contains(methodFqn))
            return false;
        if (!methodFqn.startsWith(config.getBasePackage()))
            return false;
        return !config.getFilter().shouldExclude(methodFqn, astIndex);
    }
}
