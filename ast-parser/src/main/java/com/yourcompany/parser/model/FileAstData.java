package com.yourcompany.parser.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class FileAstData implements Serializable {
    private String relativePath; // Relative path from the base directory
    private String absolutePath;
    private String packageName;
    private List<String> imports;
    private AstNode compilationUnitNode; // The root of the AST tree for this file

    public FileAstData() {
        this.imports = new ArrayList<>();
    }

    // Getters and Setters
    public String getRelativePath() { return relativePath; }
    public void setRelativePath(String relativePath) { this.relativePath = relativePath; }
    public String getAbsolutePath() { return absolutePath; }
    public void setAbsolutePath(String absolutePath) { this.absolutePath = absolutePath; }
    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }
    public List<String> getImports() { return imports; }
    public void setImports(List<String> imports) { this.imports = imports; }
    public AstNode getCompilationUnitNode() { return compilationUnitNode; }
    public void setCompilationUnitNode(AstNode compilationUnitNode) { this.compilationUnitNode = compilationUnitNode; }
}