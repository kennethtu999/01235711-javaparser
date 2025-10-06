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
 * Neo4j 類別節點實體
 * 對應 AST 中的類別節點
 */
@Node("Class")
@Getter
@Setter
public class Neo4jClassNode {

    @Id
    private String id;

    @Property("name")
    private String name;

    @Property("package")
    private String packageName;

    @Property("modifiers")
    private List<String> modifiers;

    @Property("isAbstract")
    private Boolean isAbstract;

    @Property("isFinal")
    private Boolean isFinal;

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
    public Neo4jClassNode() {
        this.createdAt = new Date();
    }

    public Neo4jClassNode(String id, String name, String packageName) {
        this();
        this.id = id;
        this.name = name;
        this.packageName = packageName;
    }

    // 關係定義
    @Relationship(type = "EXTENDS", direction = Relationship.Direction.OUTGOING)
    private List<Neo4jClassNode> extendsClasses;

    @Relationship(type = "IMPLEMENTS", direction = Relationship.Direction.OUTGOING)
    private List<Neo4jInterfaceNode> implementsInterfaces;

    @Relationship(type = "CONTAINS", direction = Relationship.Direction.OUTGOING)
    private List<Neo4jMethodNode> containsMethods;

    @Relationship(type = "HAS_ANNOTATION", direction = Relationship.Direction.OUTGOING)
    private List<Neo4jAnnotationNode> hasAnnotations;

    // 便利方法
    public void addExtends(Neo4jClassNode target) {
        if (extendsClasses == null) {
            extendsClasses = new java.util.ArrayList<>();
        }
        extendsClasses.add(target);
    }

    public void addImplements(Neo4jInterfaceNode target) {
        if (implementsInterfaces == null) {
            implementsInterfaces = new java.util.ArrayList<>();
        }
        implementsInterfaces.add(target);
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
