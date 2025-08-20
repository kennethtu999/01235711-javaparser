package kai.javaparser.diagram.output.bo;

import lombok.Data;

@Data
public class MermailActivate implements IMermaidItem {

    private String participantId;
    private boolean activate;

    public MermailActivate(String participantId, boolean activate) {
        this.participantId = participantId;
        this.activate = activate;
    }

    @Override
    public String toDiagramString(int indentLevel) {
        return String.format("%s%s %s", " ".repeat(indentLevel), activate ? "activate" : "deactivate", participantId);
    }
}
