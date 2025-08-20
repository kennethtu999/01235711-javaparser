package kai.javaparser.diagram.output.bo;

import lombok.Data;

@Data
public class MermailParticipant implements IMermaidItem {

    private String safeId;
    private String displayName;

    public MermailParticipant(String safeId, String displayName) {
        this.safeId = safeId;
        this.displayName = displayName;
    }

    @Override
    public String toDiagramString(int indentLevel) {
        return String.format("%sparticipant %s as %s", " ".repeat(indentLevel), safeId, displayName);
    }
}
