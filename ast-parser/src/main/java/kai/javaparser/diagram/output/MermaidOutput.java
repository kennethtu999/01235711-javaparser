package kai.javaparser.diagram.output;

import kai.javaparser.diagram.AstClassUtil;

/**
 * 負責生成 Mermaid 序列圖語法。
 * <p>
 * 此類別封裝了 StringBuilder 的操作細節，提供語意化的高階方法
 * (如 addCall, activate, deactivate)，讓主追蹤邏輯更清晰。
 * </p>
 */
public class MermaidOutput {
    private final StringBuilder mermaidBuilder;

    public MermaidOutput() {
        this.mermaidBuilder = new StringBuilder();
        this.mermaidBuilder.append("sequenceDiagram\n");
    }

    /**
     * 添加一個參與者 (Actor)。
     * @param name 參與者的名稱
     */
    public void addActor(String name) {
        mermaidBuilder.append(String.format("    actor %s\n", name));
    }

    /**
     * 添加一個從參與者發起的進入點呼叫。
     * @param actorName    發起呼叫的參與者
     * @param methodFqn    被呼叫的進入點方法的 FQN
     */
    public void addEntryPointCall(String actorName, String methodFqn) {
        String callee = AstClassUtil.getSimpleClassName(methodFqn);
        String signature = AstClassUtil.getMethodSignature(methodFqn);
        mermaidBuilder.append(String.format("    %s->>%s: %s\n", actorName, callee, signature));
    }

    /**
     * 添加一個方法呼叫的箭頭。
     * @param caller    呼叫端的簡化類別名
     * @param callee    被呼叫端的簡化類別名
     * @param signature 方法簽名
     */
    public void addCall(String caller, String callee, String signature) {
        mermaidBuilder.append(String.format("    %s->>%s: %s\n", caller, callee, signature));
    }

    /**
     * 添加 'activate' 指令。
     * @param participant 要啟動的參與者
     */
    public void activate(String participant) {
        mermaidBuilder.append(String.format("    activate %s\n", participant));
    }

    /**
     * 添加 'deactivate' 指令。
     * @param participant 要停用的參與者
     */
    public void deactivate(String participant) {
        mermaidBuilder.append(String.format("    deactivate %s\n", participant));
    }

    @Override
    public String toString() {
        return this.mermaidBuilder.toString();
    }
}