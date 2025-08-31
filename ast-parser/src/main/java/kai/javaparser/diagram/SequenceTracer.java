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
            return;

        callStack.add(methodFqn);

        // 合併所有 DiagramNode 並按行號排序
        List<DiagramNode> sortedNodes = new ArrayList<>();
        sortedNodes.addAll(validInvocations);
        sortedNodes.addAll(controlFlowFragments);
        sortedNodes.sort(Comparator.comparingInt(DiagramNode::getStartLineNumber));

        // 將當前層級的節點加入父節點列表
        parentNodes.addAll(sortedNodes);

        // 為本層的每個節點遞迴尋找下一層
        for (DiagramNode node : sortedNodes) {
            if (node instanceof InteractionModel) {
                processInteractionNode((InteractionModel) node, callStack, depth - 1);
            } else if (node instanceof ControlFlowFragment) {
                processControlFlowNode((ControlFlowFragment) node, callStack, depth - 1);
            }
        }

        callStack.remove(methodFqn);
    }

    private void processInteractionNode(InteractionModel interaction, Set<String> callStack, int depth) {
        // 處理鏈式呼叫
        if (interaction.getChildren() != null && !interaction.getChildren().isEmpty()) {
            // 如果設定為不隱藏細節，則為中間節點也尋找子呼叫
            if (!config.isHideDetailsInChainExpression()) {
                String calleeMethodFqn = interaction.getCallee() + "." + interaction.getMethodName();
                List<DiagramNode> childNodes = new ArrayList<>();
                traceMethod(calleeMethodFqn, callStack, depth, childNodes);
                // 將子節點轉換為 InteractionModel 並設定到 children 中
                List<InteractionModel> childInteractions = childNodes.stream()
                        .filter(node -> node instanceof InteractionModel)
                        .map(node -> (InteractionModel) node)
                        .collect(Collectors.toList());
                if (!childInteractions.isEmpty()) {
                    interaction.setChildren(childInteractions);
                }
            }
            processInteractionNode(interaction.getChildren().get(0), callStack, depth);
        } else { // 鏈的最後一個節點
            String fullCalleeFqn = interaction.getCallee() + "." + interaction.getMethodName();
            List<DiagramNode> childNodes = new ArrayList<>();
            traceMethod(fullCalleeFqn, callStack, depth, childNodes);
            // 將子節點轉換為 InteractionModel 並設定到 interaction 內部
            List<InteractionModel> childInteractions = childNodes.stream()
                    .filter(node -> node instanceof InteractionModel)
                    .map(node -> (InteractionModel) node)
                    .collect(Collectors.toList());
            if (!childInteractions.isEmpty()) {
                interaction.setChildren(childInteractions);
            }
        }
    }

    private void processControlFlowNode(ControlFlowFragment fragment, Set<String> callStack, int depth) {
        if (depth <= 0)
            return;

        // 合併 interactions 和 alternatives，並按行號排序
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

                processInteractionChain(queue, queue.pop(), callStack, depth);

            } else if (node instanceof ControlFlowFragment) {
                // 遞迴處理巢狀的控制流程片段
                processControlFlowNode((ControlFlowFragment) node, callStack, depth);
            }
        }
    }

    private void processInteractionChain(Stack<InteractionModel> queue, InteractionModel currentNode,
            Set<String> callStack, int depth) {

        if (!config.isHideDetailsInConditionals()) {
            InteractionModel interaction = currentNode;
            String calleeClassFqn = interaction.getCallee();
            String fullCalleeFqn = calleeClassFqn + "." + interaction.getMethodName();

            if (!config.getFilter().shouldExclude(calleeClassFqn, interaction.getMethodName(), astIndex)) {
                List<DiagramNode> childNodes = new ArrayList<>();
                traceMethod(fullCalleeFqn, callStack, depth - 1, childNodes);
                // 將子節點轉換為 InteractionModel 並設定到 interaction 內部
                List<InteractionModel> childInteractions = childNodes.stream()
                        .filter(node -> node instanceof InteractionModel)
                        .map(node -> (InteractionModel) node)
                        .collect(Collectors.toList());
                if (!childInteractions.isEmpty()) {
                    interaction.setChildren(childInteractions);
                }
            }
        }

        if (!queue.isEmpty()) {
            processInteractionChain(queue, queue.pop(), callStack, depth);
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
