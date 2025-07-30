package com.yourcompany.parser.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Getter;
import lombok.Setter;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@Setter
public class FileAstData implements Serializable {
    private char[] fileContent;
    private String relativePath; // Relative path from the base directory
    private String absolutePath;
    private String packageName;
    private List<String> imports;
    private AstNode compilationUnitNode; // The root of the AST tree for this file

    public FileAstData() {
    }

    public FileAstData(char[] fileContent) {
        this.fileContent = fileContent;
        this.imports = new ArrayList<>();
    }
}