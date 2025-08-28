package kai.javaparser.diagram.output.item;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MermailCallback extends AbstractMermaidItem {

    private String callerId;
    private String calleeId;
    private String callbackVariableName;

    public MermailCallback(String callerId, String calleeId, String callbackVariableName) {
        this.callerId = callerId;
        this.calleeId = calleeId;
        this.callbackVariableName = callbackVariableName;
    }

    @Override
    public String toDiagramString(int indentLevel) {
        return getFullContent(indentLevel, String.format("%s-->>%s: %s", callerId, calleeId,
                callbackVariableName));
    }

}
