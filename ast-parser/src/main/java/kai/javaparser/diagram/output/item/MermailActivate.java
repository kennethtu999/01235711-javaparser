package kai.javaparser.diagram.output.item;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MermailActivate extends AbstractMermaidItem {

    private String participantId;
    private boolean activate;

    public MermailActivate(String participantId, boolean activate) {
        this.participantId = participantId;
        this.activate = activate;
    }

    @Override
    public String toDiagramString(int indentLevel) {
        return getFullContent(activate ? indentLevel : indentLevel - 1,
                String.format("%s %s", activate ? "activate" : "deactivate", participantId));
    }
}
