package com.yourcompany.parser.model;

public enum AstNodeType {
    COMPILATION_UNIT,
    PACKAGE_DECLARATION,
    IMPORT_DECLARATION,
    TYPE_DECLARATION, // Class, Interface, Enum, Annotation
    METHOD_DECLARATION,
    FIELD_DECLARATION,
    VARIABLE_DECLARATION_FRAGMENT, // <-- Add this
    METHOD_INVOCATION,
    QUALIFIED_NAME,
    SIMPLE_NAME,
    ANNOTATION,
    MODIFIER,
    // Add more as needed (e.g., IfStatement, ForStatement, ReturnStatement, etc.)
    UNKNOWN
}