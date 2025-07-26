package com.yourcompany.parser.model;

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

    // // Getters and Setters
    // public AstNodeType getType() { return type; }
    // public void setType(AstNodeType type) { this.type = type; }
    // public String getName() { return name; }
    // public void setName(String name) { this.name = name; }
    // public String getFullyQualifiedName() { return fullyQualifiedName; }
    // public void setFullyQualifiedName(String fullyQualifiedName) { this.fullyQualifiedName = fullyQualifiedName; }
    // public String getResolvedTypeFQN() { return resolvedTypeFQN; }
    // public void setResolvedTypeFQN(String resolvedTypeFQN) { this.resolvedTypeFQN = resolvedTypeFQN; }
    // public List<String> getModifiers() { return modifiers; }
    // public void setModifiers(List<String> modifiers) { this.modifiers = modifiers; }
    // public List<String> getAnnotations() { return annotations; }
    // public void setAnnotations(List<String> annotations) { this.annotations = annotations; }
    // public int getStartPosition() { return startPosition; }
    // public void setStartPosition(int startPosition) { this.startPosition = startPosition; }
    // public int getLength() { return length; }
    // public void setLength(int length) { this.length = length; }
    // public int getLineNumber() { return lineNumber; }
    // public void setLineNumber(int lineNumber) { this.lineNumber = lineNumber; }
    // public List<AstNode> getChildren() { return children; }
    public void addChild(AstNode child) {
        if (this.children == null) {
            this.children = new ArrayList<>();
        }
        this.children.add(child);
    }
    // public String getLiteralValue() { return literalValue; }
    // public void setLiteralValue(String literalValue) { this.literalValue = literalValue; }

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