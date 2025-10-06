package kai.javaparser.ast.entity;

import java.util.Date;
import java.util.List;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;

import lombok.Getter;
import lombok.Setter;

/**
 * Neo4j 介面節點實體
 * 對應 AST 中的介面節點
 */
@Node("Interface")
@Getter
@Setter
public class Neo4jInterfaceNode {

    @Id
    private String id;

    @Property("name")
    private String name;

    @Property("package")
    private String packageName;

    @Property("modifiers")
    private List<String> modifiers;

    @Property("isPublic")
    private Boolean isPublic;

    @Property("isPrivate")
    private Boolean isPrivate;

    @Property("isProtected")
    private Boolean isProtected;

    @Property("isStatic")
    private Boolean isStatic;

    @Property("sourceFile")
    private String sourceFile;

    @Property("lineNumber")
    private Integer lineNumber;

    @Property("columnNumber")
    private Integer columnNumber;

    @Property("createdAt")
    private Date createdAt;

    // 建構子
    public Neo4jInterfaceNode() {
        this.createdAt = new Date();
    }

    public Neo4jInterfaceNode(String id, String name, String packageName) {
        this();
        this.id = id;
        this.name = name;
        this.packageName = packageName;
    }

    // 關係定義
    @Relationship(type = "EXTENDS", direction = Relationship.Direction.OUTGOING)
    private List<Neo4jInterfaceNode> extendsInterfaces;

    @Relationship(type = "CONTAINS", direction = Relationship.Direction.OUTGOING)
    private List<Neo4jMethodNode> containsMethods;

    @Relationship(type = "HAS_ANNOTATION", direction = Relationship.Direction.OUTGOING)
    private List<Neo4jAnnotationNode> hasAnnotations;

    // 便利方法
    public void addExtends(Neo4jInterfaceNode target) {
        if (extendsInterfaces == null) {
            extendsInterfaces = new java.util.ArrayList<>();
        }
        extendsInterfaces.add(target);
    }

    public void addContainsMethod(Neo4jMethodNode target) {
        if (containsMethods == null) {
            containsMethods = new java.util.ArrayList<>();
        }
        containsMethods.add(target);
    }

    public void addHasAnnotation(Neo4jAnnotationNode target) {
        if (hasAnnotations == null) {
            hasAnnotations = new java.util.ArrayList<>();
        }
        hasAnnotations.add(target);
    }
}
