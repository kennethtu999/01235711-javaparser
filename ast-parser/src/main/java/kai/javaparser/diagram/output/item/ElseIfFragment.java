package kai.javaparser.diagram.output.item;

public class ElseIfFragment extends AbstractMermaidItem {

    private final String condition;

    public ElseIfFragment(String condition) {
        this.condition = condition;
    }

    @Override
    public String toDiagramString(int indentLevel) {
        return getFullContent(indentLevel, "else " + condition);
    }
}
