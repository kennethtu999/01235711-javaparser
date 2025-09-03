package kai.javaparser.diagram.output.item;

public class ElseFragment extends AbstractMermaidItem {
    private final String condition;

    public ElseFragment(String condition) {
        this.condition = condition;
    }

    @Override
    public String toDiagramString(int indentLevel) {
        return getFullContent(indentLevel, "else " + condition);
    }
}
