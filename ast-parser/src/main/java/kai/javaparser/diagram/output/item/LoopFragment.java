package kai.javaparser.diagram.output.item;

/**
 * 循環片段 (loop) - 用於 for/while 結構
 */
public class LoopFragment extends AbstractMermaidItem {
    private final String condition;

    public LoopFragment(String condition) {
        this.condition = condition;
    }

    @Override
    public String toString() {
        return "loop " + condition;
    }

    @Override
    public String toDiagramString(int indentLevel) {
        return getFullContent(indentLevel, "loop " + condition);
    }
}