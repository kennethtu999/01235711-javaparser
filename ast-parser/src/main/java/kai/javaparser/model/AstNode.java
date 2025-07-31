package kai.javaparser.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

@JsonInclude(JsonInclude.Include.NON_NULL) // Don't include null fields in JSON
@Data
public class AstNode {
    private AstNodeType type;
    private String name; // For types, methods, fields
    private String fullyQualifiedName; // For types, methods, fields, and resolved types
    private String resolvedTypeFQN; // For expressions, variables, method return types
    private List<String> modifiers;
    private List<String> annotations;
    private int startPosition;
    private int length;
    private int lineNumber;
    private List<AstNode> children;
    private String literalValue; // For literals (e.g., "hello", 123)

    public AstNode() {
        // Initialize children list here, or allow it to be null if @JsonInclude(Include.NON_NULL) is used for it
        this.children = new ArrayList<>();
    }

    public AstNode(AstNodeType type) {
        this.type = type;
        this.children = new ArrayList<>();
    }

    public void addChild(AstNode child) {
        if (this.children == null) {
            this.children = new ArrayList<>();
        }
        this.children.add(child);
    }

    // Override equals and hashCode for potential future use (e.g., comparing nodes)
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AstNode astNode = (AstNode) o;
        return startPosition == astNode.startPosition &&
               length == astNode.length &&
               type == astNode.type &&
               Objects.equals(name, astNode.name) &&
               Objects.equals(fullyQualifiedName, astNode.fullyQualifiedName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, name, fullyQualifiedName, startPosition, length);
    }
}