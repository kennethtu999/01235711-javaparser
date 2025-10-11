package kai.javaparser.ast.entity;

import java.util.List;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;

import lombok.Getter;
import lombok.Setter;

/**
 * Neo4j 方法節點實體
 * 對應 AST 中的方法節點
 */
@Node("Method")
@Getter
@Setter
public class Neo4jMethodNode {

    @Id
    private String id;

    @Property("name")
    private String name;

    @Property("className")
    private String className;

    @Property("package")
    private String packageName;

    @Property("returnType")
    private String returnType;

    @Property("parameters")
    private List<String> parameters;

    @Property("sourceFile")
    private String sourceFile;

    @Property("lineNumber")
    private Integer lineNumber;

    @Property("columnNumber")
    private Integer columnNumber;

    @Property("bodyLength")
    private Integer bodyLength;

    @Property("createdAt")
    private Long createdAt;

    // 建構子
    public Neo4jMethodNode() {
        this.createdAt = System.currentTimeMillis();
    }

    public Neo4jMethodNode(String id, String name, String className) {
        this();
        this.id = id;
        this.name = name;
        this.className = className;
    }

    // 關係定義
    @Relationship(type = "CALLS", direction = Relationship.Direction.OUTGOING)
    private List<Neo4jMethodNode> callsMethods;

    @Relationship(type = "HAS_ANNOTATION", direction = Relationship.Direction.OUTGOING)
    private List<Neo4jAnnotationNode> hasAnnotations;

    @Relationship(type = "CONTAINS", direction = Relationship.Direction.INCOMING)
    private Neo4jClassNode containedByClass;

    // 便利方法
    public void addCalls(Neo4jMethodNode target) {
        if (callsMethods == null) {
            callsMethods = new java.util.ArrayList<>();
        }
        callsMethods.add(target);
    }

    public void addHasAnnotation(Neo4jAnnotationNode target) {
        if (hasAnnotations == null) {
            hasAnnotations = new java.util.ArrayList<>();
        }
        hasAnnotations.add(target);
    }
}
