package kai.javaparser.diagram;

import java.util.List;

import kai.javaparser.ast.model.AnnotationInfo;
import kai.javaparser.ast.model.ControlFlowFragment;
import kai.javaparser.ast.model.DiagramNode;
import kai.javaparser.ast.model.FileAstData;
import kai.javaparser.ast.model.InteractionModel;
import kai.javaparser.ast.model.TraceResult;
import kai.javaparser.diagram.idx.AstIndex;
import kai.javaparser.diagram.output.MermaidOutput;

/**
 * Mermaid 渲染器：
 * 負責將 TraceResult 轉換為 Mermaid 序列圖語法字串
 */
public class MermaidRenderer implements DiagramRenderer {
    private final SequenceOutputConfig config;
    private final AstIndex astIndex;
    private MermaidOutput output;

    public MermaidRenderer(SequenceOutputConfig config) {
        this.config = config;
        this.astIndex = null; // 向後兼容
    }

    public MermaidRenderer(SequenceOutputConfig config, AstIndex astIndex) {
        this.config = config;
        this.astIndex = astIndex;
    }

    @Override
    public String render(TraceResult traceResult) {
        this.output = new MermaidOutput();

        // 1. 設定進入點
        output.addActor("User");
        String entryClassFqn = AstClassUtil.getClassFqnFromMethodFqn(traceResult.getEntryPointMethodFqn());
        String methodSignature = AstClassUtil.getMethodSignature(traceResult.getEntryPointMethodFqn()).replaceAll(
                "\\(.*",
                "");
        String entryClassId = AstClassUtil.safeMermaidId(entryClassFqn);

        // 添加類別註解信息
        renderClassAnnotations(traceResult, entryClassId);

        output.addEntryPointCall("User", entryClassId, methodSignature);
        output.activate(entryClassId);

        // 2. 遞迴遍歷呼叫樹
        for (DiagramNode node : traceResult.getSequenceNodes()) {
            renderNode(node, entryClassId);
        }

        output.deactivate(entryClassId);

        return output.toString();
    }

    @Override
    public String getFormatName() {
        return "Mermaid";
    }

    /**
     * 渲染類別註解信息
     */
    private void renderClassAnnotations(TraceResult traceResult, String classId) {
        if (astIndex == null) {
            // 如果沒有 AstIndex，跳過類別註解渲染
            return;
        }

        try {
            // 從 entryPointMethodFqn 獲取類別 FQN
            String classFqn = AstClassUtil.getClassFqnFromMethodFqn(traceResult.getEntryPointMethodFqn());

            // 通過 AstIndex 獲取類別的 AST 數據
            FileAstData fileAstData = astIndex.getAstDataByClassFqn(classFqn);
            if (fileAstData != null && fileAstData.getSequenceDiagramData() != null) {
                List<AnnotationInfo> classAnnotations = fileAstData.getSequenceDiagramData().getClassAnnotations();
                if (classAnnotations != null && !classAnnotations.isEmpty()) {
                    renderAnnotations(classAnnotations, classId);
                }
            }

        } catch (Exception e) {
            // 靜默處理錯誤，不影響圖表生成
            System.err.println("渲染類別註解時發生錯誤: " + e.getMessage());
        }
    }

    /**
     * 渲染方法註解信息
     */
    private void renderMethodAnnotations(String classFqn, String methodName, String participantId) {
        if (astIndex == null) {
            // 如果沒有 AstIndex，跳過方法註解渲染
            return;
        }

        try {
            // 通過 AstIndex 獲取類別的 AST 數據
            FileAstData fileAstData = astIndex.getAstDataByClassFqn(classFqn);
            if (fileAstData != null && fileAstData.getSequenceDiagramData() != null) {
                // 查找對應的方法分組
                var methodGroup = fileAstData.getSequenceDiagramData().findMethodGroup(methodName);
                if (methodGroup != null && methodGroup.getAnnotations() != null
                        && !methodGroup.getAnnotations().isEmpty()) {
                    renderAnnotations(methodGroup.getAnnotations(), participantId);
                }
            }

        } catch (Exception e) {
            // 靜默處理錯誤，不影響圖表生成
            System.err.println("渲染方法註解時發生錯誤: " + e.getMessage());
        }
    }

    /**
     * 渲染註解信息為 Mermaid 註解
     */
    private void renderAnnotations(List<AnnotationInfo> annotations, String participantId) {
        if (annotations == null || annotations.isEmpty()) {
            return;
        }

        StringBuilder annotationText = new StringBuilder();
        for (int i = 0; i < annotations.size(); i++) {
            AnnotationInfo annotation = annotations.get(i);
            if (i > 0) {
                annotationText.append(", ");
            }
            annotationText.append("@").append(annotation.getSimpleName());

            // 添加註解參數
            if (annotation.getParameters() != null && !annotation.getParameters().isEmpty()) {
                annotationText.append("(");
                for (int j = 0; j < annotation.getParameters().size(); j++) {
                    AnnotationInfo.AnnotationParameter param = annotation.getParameters().get(j);
                    if (j > 0) {
                        annotationText.append(", ");
                    }
                    if (param.getParameterName() != null) {
                        annotationText.append(param.getParameterName()).append("=");
                    }
                    annotationText.append(param.getParameterValue());
                }
                annotationText.append(")");
            }
        }

        if (annotationText.length() > 0) {
            output.addNote(participantId, annotationText.toString());
        }
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

            // 渲染方法註解（如果有的話）
            renderMethodAnnotations(calleeClassFqn, interaction.getMethodName(), calleeId);

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

            // 渲染方法註解（如果有的話）
            renderMethodAnnotations(calleeClassFqn, interaction.getMethodName(), calleeId);

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
