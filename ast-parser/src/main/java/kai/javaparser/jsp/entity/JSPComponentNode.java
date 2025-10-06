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
 * JSP 元件節點實體
 * 對應知識圖譜中的 JSP 元件節點（包括 JSF 元件、HTML 元件等）
 */
@Node("JSPComponent")
@Getter
@Setter
public class JSPComponentNode {

    @Id
    private String id;

    @Property("name")
    private String name;

    @Property("lineNumber")
    private Integer lineNumber;

    @Property("fileName")
    private String fileName;

    @Property("createdAt")
    private Date createdAt;

    // JSP 元件特定屬性
    @Property("componentType")
    private String componentType;

    @Property("action")
    private String action;

    @Property("eventType")
    private String eventType;

    @Property("tagName")
    private String tagName;

    @Property("attributes")
    private List<String> attributes;

    @Property("isSelfClosing")
    private Boolean isSelfClosing;

    @Property("formId")
    private String formId;

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

    // 關係定義
    @Relationship(type = "TRIGGERS", direction = Relationship.Direction.OUTGOING)
    private List<Object> triggers;

    @Relationship(type = "INTERACTS_WITH", direction = Relationship.Direction.OUTGOING)
    private List<Object> interactsWith;

    @Relationship(type = "CONTAINS", direction = Relationship.Direction.OUTGOING)
    private List<Object> contains;

    @Relationship(type = "DEPENDS_ON", direction = Relationship.Direction.OUTGOING)
    private List<Object> dependsOn;

    // 建構子
    public JSPComponentNode() {
        this.createdAt = new Date();
    }

    public JSPComponentNode(String id, String name) {
        this();
        this.id = id;
        this.name = name;
    }

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
