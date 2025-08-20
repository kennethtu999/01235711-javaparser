package kai.javaparser.diagram.output.bo;

import lombok.Data;

@Data
public class MermailActor implements IMermaidItem {

    private String name;

    public MermailActor(String name) {
        this.name = name;
    }

    @Override
    public String toDiagramString(int indentLevel) {
        return String.format("%sactor %s", " ".repeat(indentLevel), name);
    }
}
