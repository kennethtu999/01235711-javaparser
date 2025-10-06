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
 * Neo4j 註解節點實體
 * 對應 AST 中的註解節點
 */
@Node("Annotation")
@Getter
@Setter
public class Neo4jAnnotationNode {

    @Id
    private String id;

    @Property("name")
    private String name;

    @Property("targetType")
    private String targetType;

    @Property("parameters")
    private List<String> parameters;

    @Property("sourceFile")
    private String sourceFile;

    @Property("lineNumber")
    private Integer lineNumber;

    @Property("columnNumber")
    private Integer columnNumber;

    @Property("createdAt")
    private Date createdAt;

    // 建構子
    public Neo4jAnnotationNode() {
        this.createdAt = new Date();
    }

    public Neo4jAnnotationNode(String id, String name, String targetType) {
        this();
        this.id = id;
        this.name = name;
        this.targetType = targetType;
    }

    // 關係定義
    @Relationship(type = "HAS_ANNOTATION", direction = Relationship.Direction.INCOMING)
    private List<Object> annotatedBy;

    // 便利方法
    public void addAnnotatedBy(Object target) {
        if (annotatedBy == null) {
            annotatedBy = new java.util.ArrayList<>();
        }
        annotatedBy.add(target);
    }
}
