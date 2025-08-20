package kai.javaparser.diagram.output;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import kai.javaparser.diagram.output.bo.IMermaidItem;
import kai.javaparser.diagram.output.bo.MermailActivate;
import kai.javaparser.diagram.output.bo.MermailActor;
import kai.javaparser.diagram.output.bo.MermailCall;
import kai.javaparser.diagram.output.bo.MermailParticipant;

/**
 * 負責生成 Mermaid 序列圖語法。
 * 支援：
 * - participant <safeId> as <displayName>
 * - 保留原始 FQN 方便將來反向解析
 */
public class MermaidOutput {
    private List<IMermaidItem> mermaidList;

    public MermaidOutput() {
        this.mermaidList = new ArrayList<>();
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
        mermaidList.add(new MermailParticipant(safeId, displayName));
    }

    /**
     * 添加從 Actor 到方法的進入點呼叫。
     */
    public void addEntryPointCall(String actorName, String calleeId, String calleeDisplayName) {
        addParticipant(calleeId, calleeDisplayName);
        mermaidList.add(new MermailCall(actorName, calleeId, getMethodSignatureFromDisplayName(calleeDisplayName)));
    }

    /**
     * 添加方法呼叫箭頭。
     */
    public void addCall(String callerId, String calleeId, String signature) {
        mermaidList.add(new MermailCall(callerId, calleeId, signature));
    }

    public void activate(String participantId) {
        mermaidList.add(new MermailActivate(participantId, true));
    }

    public void deactivate(String participantId) {
        mermaidList.add(new MermailActivate(participantId, false));
    }

    /**
     * 1. 所有 participant / actor 移到最前面
     * 2. 去除重複
     * 3. activate 與 deactivate 要成對出現，每多一層 activate 就加兩個空格
     */
    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        Set<IMermaidItem> participants = new LinkedHashSet<>();
        List<IMermaidItem> otherItems = new ArrayList<>();

        // 分類所有項目
        for (IMermaidItem item : mermaidList) {
            String line = item.toString();
            if (item instanceof MermailParticipant || item instanceof MermailActor) {
                participants.add(item);
            } else {
                otherItems.add(item);
            }
        }

        // 組合輸出
        result.append("sequenceDiagram\n");

        // 先輸出所有 participants 和 actors
        for (IMermaidItem participant : participants) {
            result.append(participant.toDiagramString(0)).append("\n");
        }

        // 再輸出其他項目（calls, activates, deactivates）
        int indentLevel = 0;
        for (IMermaidItem item : otherItems) {
            result.append(item.toDiagramString(indentLevel)).append("\n");
            if (item instanceof MermailActivate && ((MermailActivate) item).isActivate()) {
                indentLevel += 2;
            } else if (item instanceof MermailActivate && !((MermailActivate) item).isActivate()) {
                indentLevel -= 2;
            }
        }

        return result.toString();
    }

    /**
     * 從 displayName（通常是 FQN）取出方法簽名。
     */
    private String getMethodSignatureFromDisplayName(String displayName) {
        int lastDot = displayName.lastIndexOf('.');
        return (lastDot >= 0) ? displayName.substring(lastDot + 1) : displayName;
    }
}
