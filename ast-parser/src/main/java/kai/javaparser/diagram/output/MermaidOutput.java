package kai.javaparser.diagram.output;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 負責生成 Mermaid 序列圖語法。
 * 支援：
 * - participant <safeId> as <displayName>
 * - 保留原始 FQN 方便將來反向解析
 */
public class MermaidOutput {
    private final StringBuilder mermaidBuilder;

    public MermaidOutput() {
        this.mermaidBuilder = new StringBuilder();
        this.mermaidBuilder.append("sequenceDiagram\n");
    }

    /**
     * 添加 Actor（特殊的 participant）。
     *
     * @param name 顯示名稱
     */
    public void addActor(String name) {
        mermaidBuilder.append(String.format("    actor %s\n", name));
    }

    /**
     * 添加一個 participant，支援安全 ID 與顯示名稱。
     *
     * @param safeId      Mermaid 允許的 ID（不可含空格、()<>等）
     * @param displayName 顯示給人看的名稱（可包含原始符號）
     */
    public void addParticipant(String safeId, String displayName) {
        mermaidBuilder.append(String.format("    participant %s as %s\n", safeId, displayName));
    }

    /**
     * 添加從 Actor 到方法的進入點呼叫。
     */
    public void addEntryPointCall(String actorName, String calleeId, String calleeDisplayName) {
        addParticipant(calleeId, calleeDisplayName);
        mermaidBuilder.append(String.format("    %s->>%s: %s\n",
                actorName, calleeId, getMethodSignatureFromDisplayName(calleeDisplayName)));
    }

    /**
     * 添加方法呼叫箭頭。
     */
    public void addCall(String callerId, String calleeId, String signature) {
        mermaidBuilder.append(String.format("    %s->>%s: %s\n", callerId, calleeId, signature));
    }

    public void activate(String participantId) {
        mermaidBuilder.append(String.format("    activate %s\n", participantId));
    }

    public void deactivate(String participantId) {
        mermaidBuilder.append(String.format("    deactivate %s\n", participantId));
    }

    /**
     * 1. 所有 participant / actor 移到最前面
     * 2. 去除重複
     */
    @Override
    public String toString() {
        String[] lines = this.mermaidBuilder.toString().split("\n");
        StringBuilder result = new StringBuilder();
        Set<String> participants = new LinkedHashSet<>();
        StringBuilder others = new StringBuilder();

        for (String line : lines) {
            if (line.startsWith("sequenceDiagram")) {
                continue; // 頭部會手動加
            }
            String trimmed = line.trim();
            if (trimmed.startsWith("participant") || trimmed.startsWith("actor")) {
                participants.add(line);
            } else {
                if (!trimmed.isBlank()) {
                    others.append(line).append("\n");
                }
            }
        }

        // 組合輸出
        result.append("sequenceDiagram\n");
        for (String p : participants) {
            result.append(p).append("\n");
        }
        result.append(others);
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
