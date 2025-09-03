package kai.javaparser.diagram.output.item;

public class MermaidNote extends AbstractMermaidItem {
    public enum Location {
        left,
        right,
        over
    }

    private String content;
    private Location direction;
    private String participantId;

    public MermaidNote(String participantId, Location direction, String content) {
        this.content = content;
        this.direction = direction;
        this.participantId = participantId;
    }

    @Override
    public String toDiagramString(int indentLevel) {
        String direction = this.direction.name();
        if (!direction.equals("over")) {
            direction = direction + " of ";
        }
        return getFullContent(indentLevel,
                String.format("Note %s %s: %s", direction, participantId, getSafeValue(content)));
    }

}
