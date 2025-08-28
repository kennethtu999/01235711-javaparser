package kai.javaparser.diagram.output.item;

public abstract class AbstractMermaidItem {
    public static final String INDENT_STRING = " ";
    public static final int INDENT_LEVEL = 2;

    public String getFullContent(int indentLevel, String content) {
        return INDENT_STRING.repeat(indentLevel * INDENT_LEVEL) + content;
    }

    abstract public String toDiagramString(int indentLevel);
}
