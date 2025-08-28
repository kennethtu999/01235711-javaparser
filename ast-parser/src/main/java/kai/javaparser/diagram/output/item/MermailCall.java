package kai.javaparser.diagram.output.item;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MermailCall extends AbstractMermaidItem {

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
        return getFullContent(indentLevel, String.format("%s->>%s: %s", actorName, calleeId,
                methodSignatureFromDisplayName));
    }

}
