package kai.javaparser.jsp.entity;

import java.util.Date;
import java.util.List;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;

import lombok.Getter;
import lombok.Setter;

/**
 * Neo4j JSP 節點實體
 * 對應知識圖譜中的節點
 */
@Node("JspNode")
@Getter
@Setter
public class Neo4jJspNode {

    @Id
    private String id;

    @Property("type")
    private String type;

    @Property("name")
    private String name;

    @Property("lineNumber")
    private Integer lineNumber;

    @Property("fileName")
    private String fileName;

    @Property("createdAt")
    private Date createdAt;

    // JSF 元件特定屬性
    @Property("componentType")
    private String componentType;

    @Property("action")
    private String action;

    @Property("eventType")
    private String eventType;

    // JavaScript 函式特定屬性
    @Property("functionContent")
    private String functionContent;

    @Property("containsAjaxCall")
    private Boolean containsAjaxCall;

    @Property("containsNavigation")
    private Boolean containsNavigation;

    @Property("complexityScore")
    private Integer complexityScore;

    // 後端方法特定屬性
    @Property("className")
    private String className;

    @Property("methodName")
    private String methodName;

    @Property("fullSignature")
    private String fullSignature;

    // DOM 元素特定屬性
    @Property("elementType")
    private String elementType;

    @Property("formId")
    private String formId;

    // 頁面特定屬性
    @Property("pagecodeClass")
    private String pagecodeClass;

    @Property("externalJsReferences")
    private List<String> externalJsReferences;

    // AI 分析結果
    @Property("aiAnalysis")
    private String aiAnalysis;

    @Property("summary")
    private String summary;

    @Property("purposeTags")
    private List<String> purposeTags;

    @Property("interactsWithIds")
    private List<String> interactsWithIds;

    @Property("dataFlow")
    private String dataFlow;

    @Property("businessLogic")
    private String businessLogic;

    @Property("maintainabilityNotes")
    private String maintainabilityNotes;

    // 建構子
    public Neo4jJspNode() {
        this.createdAt = new Date();
    }

    public Neo4jJspNode(String id, String type, String name) {
        this();
        this.id = id;
        this.type = type;
        this.name = name;
    }

    // 關係定義
    @Relationship(type = "TRIGGERS", direction = Relationship.Direction.OUTGOING)
    private List<Object> triggers;

    @Relationship(type = "INTERACTS_WITH", direction = Relationship.Direction.OUTGOING)
    private List<Object> interactsWith;

    @Relationship(type = "CONTAINS", direction = Relationship.Direction.OUTGOING)
    private List<Object> contains;

    @Relationship(type = "DEPENDS_ON", direction = Relationship.Direction.OUTGOING)
    private List<Object> dependsOn;

    // 便利方法
    public void addTrigger(Object target) {
        if (triggers == null) {
            triggers = new java.util.ArrayList<>();
        }
        triggers.add(target);
    }

    public void addInteractsWith(Object target) {
        if (interactsWith == null) {
            interactsWith = new java.util.ArrayList<>();
        }
        interactsWith.add(target);
    }

    public void addContains(Object target) {
        if (contains == null) {
            contains = new java.util.ArrayList<>();
        }
        contains.add(target);
    }

    public void addDependsOn(Object target) {
        if (dependsOn == null) {
            dependsOn = new java.util.ArrayList<>();
        }
        dependsOn.add(target);
    }
}