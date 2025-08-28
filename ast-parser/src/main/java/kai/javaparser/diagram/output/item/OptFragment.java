package kai.javaparser.diagram.output.item;

/**
 * 可選片段 (opt) - 用於單個 if 結構
 */
public class OptFragment extends AbstractMermaidItem {
    private final String condition;

    public OptFragment(String condition) {
        this.condition = condition;
    }

    @Override
    public String toString() {
        return "opt " + condition;
    }

    @Override
    public String toDiagramString(int indentLevel) {
        return getFullContent(indentLevel, "opt " + condition);
    }
}