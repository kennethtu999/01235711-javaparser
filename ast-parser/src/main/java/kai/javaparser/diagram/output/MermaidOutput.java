package kai.javaparser.diagram.output;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import kai.javaparser.diagram.output.item.AbstractMermaidItem;
import kai.javaparser.diagram.output.item.AltFragment;
import kai.javaparser.diagram.output.item.EndFragment;
import kai.javaparser.diagram.output.item.LoopFragment;
import kai.javaparser.diagram.output.item.MermailActivate;
import kai.javaparser.diagram.output.item.MermailActor;
import kai.javaparser.diagram.output.item.MermailCall;
import kai.javaparser.diagram.output.item.MermailCallback;
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
        addParticipant(calleeId, calleeDisplayName);
        mermaidList.add(new MermailCall(actorName, calleeId, calleeDisplayName, null));
    }

    /**
     * 添加方法呼叫箭頭。
     */
    public void addCall(String callerId, String calleeId, String signature, List<String> arguments,
            String assignedToVariable) {
        mermaidList.add(new MermailCall(callerId, calleeId, signature, arguments));
        if (StringUtils.isNotEmpty(assignedToVariable)) {
            activate(calleeId);
            mermaidList.add(new MermailCallback(calleeId, callerId, assignedToVariable));
            deactivate(calleeId);
        }
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
        for (AbstractMermaidItem item : otherItems) {
            result.append(item.toDiagramString(indentLevel)).append("\n");
            // System.out.println(indentLevel + " " + item.getClass().getSimpleName());

            if (item instanceof MermailActivate && ((MermailActivate) item).isActivate()) {
                indentLevel += 1;
            } else if (item instanceof MermailActivate && !((MermailActivate) item).isActivate()) {
                indentLevel -= 1;
            } else if (item instanceof AltFragment || item instanceof OptFragment || item instanceof LoopFragment) {
                indentLevel += 1;
            } else if (item instanceof EndFragment) {
                indentLevel -= 1;
            }
        }

        return result.toString();
    }
}
