package kai.javaparser.diagram.output;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import kai.javaparser.diagram.AstClassUtil;
import kai.javaparser.diagram.output.item.AbstractMermaidItem;
import kai.javaparser.diagram.output.item.AltFragment;
import kai.javaparser.diagram.output.item.ElseFragment;
import kai.javaparser.diagram.output.item.ElseIfFragment;
import kai.javaparser.diagram.output.item.EndFragment;
import kai.javaparser.diagram.output.item.LoopFragment;
import kai.javaparser.diagram.output.item.MermailActivate;
import kai.javaparser.diagram.output.item.MermailActor;
import kai.javaparser.diagram.output.item.MermailCall;
import kai.javaparser.diagram.output.item.MermailParticipant;
import kai.javaparser.diagram.output.item.OptFragment;

/**
 * 負責生成 Mermaid 序列圖語法。
 * 支援：
 * - participant <safeId> as <displayName>
 * - 保留原始 FQN 方便將來反向解析
 * - 控制流程片段 (alt, opt, loop)
 */
public class MermaidOutput {
    private List<AbstractMermaidItem> mermaidList;

    private Set<String> participantIds;

    public MermaidOutput() {
        this.mermaidList = new ArrayList<>();
        this.participantIds = new LinkedHashSet<>();
    }

    /**
     * 添加 Actor（特殊的 participant）。
     *
     * @param name 顯示名稱
     */
    public void addActor(String name) {
        mermaidList.add(new MermailActor(name));
    }

    /**
     * 添加一個 participant，支援安全 ID 與顯示名稱。
     *
     * @param safeId      Mermaid 允許的 ID（不可含空格、()<>等）
     * @param displayName 顯示給人看的名稱（可包含原始符號）
     */
    public void addParticipant(String safeId, String displayName) {
        if (participantIds.contains(safeId)) {
            return;
        }

        participantIds.add(safeId);
        mermaidList.add(new MermailParticipant(safeId, displayName));
    }

    /**
     * 添加從 Actor 到方法的進入點呼叫。
     */
    public void addEntryPointCall(String actorName, String calleeId, String calleeDisplayName) {
        addParticipant(calleeId, AstClassUtil.getSimpleClassName(calleeId));
        mermaidList.add(new MermailCall(actorName, calleeId, calleeDisplayName, null, false, null));
    }

    /**
     * 添加方法呼叫箭頭。
     */
    public void addCall(String callerId, String calleeId, String signature,
            List<String> arguments,
            String assignedToVariable, boolean dashLine) {
        addCall(callerId, calleeId, signature, arguments, assignedToVariable, dashLine, null);
    }

    /**
     * 添加方法呼叫箭頭（包含回傳值）。
     */
    public void addCall(String callerId, String calleeId, String signature,
            List<String> arguments,
            String assignedToVariable, boolean dashLine, String returnValue) {

        String finalSignature = signature;
        if (StringUtils.isNotEmpty(assignedToVariable)) {
            finalSignature = assignedToVariable + " : " + finalSignature;
        }
        mermaidList.add(new MermailCall(callerId, calleeId, finalSignature, arguments, dashLine, returnValue));
    }

    public void activate(String participantId) {
        mermaidList.add(new MermailActivate(participantId, true));
    }

    public void deactivate(String participantId) {
        mermaidList.add(new MermailActivate(participantId, false));
    }

    /**
     * 添加替代片段 (alt) - 用於 if/else 結構
     */
    public void addAltFragment(String condition) {
        mermaidList.add(new AltFragment(condition));
    }

    /**
     * 添加 else 片段
     */
    public void addElseFragment() {
        mermaidList.add(new ElseFragment(""));
    }

    /**
     * 添加 else if 片段
     */
    public void addElseIfFragment(String condition) {
        mermaidList.add(new ElseIfFragment(condition));
    }

    /**
     * 添加可選片段 (opt) - 用於單個 if 結構
     */
    public void addOptFragment(String condition) {
        mermaidList.add(new OptFragment(condition));
    }

    /**
     * 添加循環片段 (loop) - 用於 for/while 結構
     */
    public void addLoopFragment(String condition) {
        mermaidList.add(new LoopFragment(condition));
    }

    /**
     * 結束控制流程片段
     */
    public void endFragment() {
        mermaidList.add(new EndFragment());
    }

    /**
     * 1. 所有 participant / actor 移到最前面
     * 2. 去除重複
     * 3. activate 與 deactivate 要成對出現，每多一層 activate 就加兩個空格
     * 4. 控制流程片段需要適當的縮排
     */
    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        Set<AbstractMermaidItem> participants = new LinkedHashSet<>();
        List<AbstractMermaidItem> otherItems = new ArrayList<>();

        // 分類所有項目
        for (AbstractMermaidItem item : mermaidList) {
            if (item instanceof MermailParticipant || item instanceof MermailActor) {
                participants.add(item);
            } else {
                otherItems.add(item);
            }
        }

        // 組合輸出
        result.append("sequenceDiagram\n");

        // 先輸出所有 participants 和 actors
        for (AbstractMermaidItem participant : participants) {
            result.append(participant.toDiagramString(0)).append("\n");
        }

        // 再輸出其他項目（calls, activates, deactivates, control flow fragments）
        int indentLevel = 0;
        otherItems = fixDiagram(otherItems);
        for (AbstractMermaidItem item : otherItems) {
            result.append(item.toDiagramString(indentLevel)).append("\n");
            // System.out.println(indentLevel + " " + item.getClass().getSimpleName());

            if (item instanceof MermailActivate && ((MermailActivate) item).isActivate()) {
                indentLevel += 1;
            } else if (item instanceof MermailActivate && !((MermailActivate) item).isActivate()) {
                indentLevel -= 1;
            } else if (item instanceof AltFragment || item instanceof OptFragment || item instanceof LoopFragment) {
                indentLevel += 1;
            } else if (item instanceof ElseFragment || item instanceof ElseIfFragment) {
                // Else and ElseIf fragments should not increase indent level, as they are part
                // of the same block
            } else if (item instanceof EndFragment) {
                indentLevel -= 1;
            }
        }

        return result.toString();
    }

    /**
     * 修復 diagram
     * 
     * @param otherItems
     * @return
     */
    public List<AbstractMermaidItem> fixDiagram(List<AbstractMermaidItem> otherItems) {
        List<AbstractMermaidItem> result = new ArrayList<>();

        // if line x = opt & line x+1 = end then ignore
        for (int i = 0; i < otherItems.size(); i++) {
            if (otherItems.get(i) instanceof OptFragment && otherItems.get(i + 1) instanceof EndFragment) {
                i = i + 1;
            } else {
                result.add(otherItems.get(i));
            }
        }

        return result;
    }
}
