package kai.javaparser.ast.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import kai.javaparser.ast.model.ControlFlowFragment;
import kai.javaparser.ast.model.DiagramNode;
import kai.javaparser.ast.model.FileAstData;
import kai.javaparser.ast.model.InteractionModel;
import kai.javaparser.ast.model.MethodGroup;
import kai.javaparser.ast.model.TraceResult;
import kai.javaparser.diagram.AstClassUtil;
import kai.javaparser.diagram.SequenceOutputConfig;
import kai.javaparser.diagram.idx.AstIndex;

/**
 * 序列追蹤服務：
 * 提供核心的序列追蹤業務邏輯，將追蹤邏輯從 SequenceTracer 中抽象出來。
 * 
 * 職責：
 * 1. 提供統一的序列追蹤介面
 * 2. 依賴注入 AstIndex 來獲取 AST 資料
 * 3. 返回標準化的 TraceResult DTO
 * 
 * 重構原則：
 * 1. 將 SequenceTracer 的核心追蹤邏輯遷移至此
 * 2. 移除對檔案系統的直接依賴
 * 3. 提供可測試、可重用的服務介面
 */
@Service
public class SequenceTraceService {

    private static final Logger logger = LoggerFactory.getLogger(SequenceTraceService.class);

    private final AstIndex astIndex;

    @Autowired
    public SequenceTraceService(AstIndex astIndex) {
        this.astIndex = astIndex;
    }

    /**
     * 執行序列追蹤
     * 
     * @param entryPointMethodFqn 進入點方法的完整限定名
     * @param config              追蹤配置
     * @return 追蹤結果
     */
    public TraceResult trace(String entryPointMethodFqn, SequenceOutputConfig config) {
        logger.info("開始序列追蹤，進入點: {}", entryPointMethodFqn);

        // 確保 AstIndex 被正確載入
        try {
            astIndex.loadOrBuild();
        } catch (Exception e) {
            logger.error("載入 AST 索引失敗", e);
            throw new RuntimeException("載入 AST 索引失敗: " + e.getMessage(), e);
        }

        List<DiagramNode> sequenceNodes = new ArrayList<>();
        traceMethod(entryPointMethodFqn, new HashSet<>(), config.getDepth(), sequenceNodes, config);

        logger.info("序列追蹤完成，進入點: {}, 追蹤到的節點數量: {}",
                entryPointMethodFqn, sequenceNodes.size());

        return new TraceResult(entryPointMethodFqn, sequenceNodes);
    }

    /**
     * 遞迴追蹤方法呼叫並建立 DiagramNode 列表
     * 簡化邏輯：專注於建立清晰的資料結構
     */
    private void traceMethod(String methodFqn, Set<String> callStack, int depth,
            List<DiagramNode> parentNodes, SequenceOutputConfig config) {
        if (depth <= 0)
            return;
        if (!isTraceable(methodFqn, callStack, config))
            return;

        // 移除泛型資訊
        String classFqn = AstClassUtil.getClassFqnFromMethodFqn(methodFqn);
        classFqn = classFqn.replaceAll("<.*>", "");

        FileAstData astData = astIndex.getAstDataByClassFqn(classFqn);
        if (astData == null) {
            logger.warn("無法找到類別的 AST 資料: {}", classFqn);
            return;
        }

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
                processInteractionModelRecursive((InteractionModel) node, callStack, depth, config);
            } else if (node instanceof ControlFlowFragment) {
                processControlFlowNode((ControlFlowFragment) node, callStack, depth, config);
            }
        }

        callStack.remove(methodFqn);
    }

    /**
     * 遞迴處理 InteractionModel，包括其鏈式呼叫和內部呼叫
     */
    private void processInteractionModelRecursive(InteractionModel interaction, Set<String> callStack,
            int depth, SequenceOutputConfig config) {
        if (depth <= 0)
            return;

        // 1. 處理當前互動的內部呼叫 (如果它不是鏈式呼叫的後續環節)
        // 我們需要追蹤 callee 方法內部的活動
        String calleeMethodFqn = AstClassUtil.getMethodFqn(interaction.getCallee(), interaction.getMethodName());
        List<DiagramNode> internalChildNodes = new ArrayList<>();

        // 保存 callStack 的狀態
        Set<String> savedCallStack = new HashSet<>(callStack);
        traceMethod(calleeMethodFqn, callStack, depth - 1, internalChildNodes, config);
        // 恢復 callStack 的狀態
        callStack.clear();
        callStack.addAll(savedCallStack);

        // 將內部呼叫設定到 internalCalls 中
        if (!internalChildNodes.isEmpty()) {
            for (DiagramNode node : internalChildNodes) {
                interaction.addInternalCall(node);
            }
        }

        // 2. 處理鏈式呼叫的下一個環節
        if (interaction.getNextChainedCall() != null) {
            processInteractionModelRecursive(interaction.getNextChainedCall(), callStack, depth, config);
        }
    }

    /**
     * 處理控制流程節點
     * 簡化邏輯：專注於建立清晰的資料結構
     */
    private void processControlFlowNode(ControlFlowFragment fragment, Set<String> callStack,
            int depth, SequenceOutputConfig config) {
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
                // 保存 callStack 的狀態
                Set<String> savedCallStack = new HashSet<>(callStack);
                processInteractionModelRecursive((InteractionModel) node, callStack, depth, config);
                // 恢復 callStack 的狀態
                callStack.clear();
                callStack.addAll(savedCallStack);
            } else if (node instanceof ControlFlowFragment) {
                // 遞迴處理巢狀的控制流程片段
                // 保存 callStack 的狀態
                Set<String> savedCallStack = new HashSet<>(callStack);
                processControlFlowNode((ControlFlowFragment) node, callStack, depth, config);
                // 恢復 callStack 的狀態
                callStack.clear();
                callStack.addAll(savedCallStack);
            }
        }
    }

    private boolean isTraceable(String methodFqn, Set<String> callStack, SequenceOutputConfig config) {
        if (callStack.contains(methodFqn))
            return false;

        // Check if method belongs to any of the base packages
        if (config.getBasePackages() != null && !config.getBasePackages().isEmpty()) {
            boolean belongsToBasePackage = config.getBasePackages().stream()
                    .anyMatch(basePackage -> methodFqn.startsWith(basePackage));
            if (!belongsToBasePackage) {
                return false;
            }
        }

        return !config.getFilter().shouldExclude(methodFqn, astIndex);
    }
}
