package kai.javaparser.diagram.output.item;

/**
 * 結束控制流程片段
 */
public class EndFragment extends AbstractMermaidItem {
    @Override
    public String toString() {
        return "end";
    }

    @Override
    public String toDiagramString(int indentLevel) {
        return getFullContent(indentLevel - 1, "end");
    }
}