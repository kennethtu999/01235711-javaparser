package kai.javaparser.diagram.output.item;

/**
 * 替代片段 (alt) - 用於 if/else 結構
 */
public class AltFragment extends AbstractMermaidItem {
    private final String condition;

    public AltFragment(String condition) {
        this.condition = condition;
    }

    @Override
    public String toString() {
        return "alt " + condition;
    }

    @Override
    public String toDiagramString(int indentLevel) {
        return getFullContent(indentLevel, "alt " + condition);
    }
}