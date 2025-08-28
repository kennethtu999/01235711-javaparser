package kai.javaparser.diagram.output.item;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MermailActor extends AbstractMermaidItem {

    private String name;

    public MermailActor(String name) {
        this.name = name;
    }

    @Override
    public String toDiagramString(int indentLevel) {
        return getFullContent(indentLevel, String.format("actor %s", name));
    }
}
