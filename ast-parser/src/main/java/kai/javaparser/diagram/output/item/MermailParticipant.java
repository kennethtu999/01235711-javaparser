package kai.javaparser.diagram.output.item;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MermailParticipant extends AbstractMermaidItem {

    private String safeId;
    private String displayName;

    public MermailParticipant(String safeId, String displayName) {
        this.safeId = safeId;
        this.displayName = displayName;
    }

    @Override
    public String toDiagramString(int indentLevel) {
        return getFullContent(indentLevel, String.format("participant %s as %s", safeId, displayName));
    }
}
