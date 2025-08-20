package kai.javaparser.diagram.output.bo;

import lombok.Data;

@Data
public class MermailCall implements IMermaidItem {

    private String actorName;
    private String calleeId;
    private String methodSignatureFromDisplayName;

    public MermailCall(String actorName, String calleeId, String methodSignatureFromDisplayName) {
        this.actorName = actorName;
        this.calleeId = calleeId;
        this.methodSignatureFromDisplayName = methodSignatureFromDisplayName;
    }

    @Override
    public String toDiagramString(int indentLevel) {
        return String.format("%s%s->>%s: %s", " ".repeat(indentLevel), actorName, calleeId,
                methodSignatureFromDisplayName);
    }

}
