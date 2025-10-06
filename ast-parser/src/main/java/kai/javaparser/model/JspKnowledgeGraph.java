package kai.javaparser.model;

import java.util.*;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.Setter;

/**
 * JSP 知識圖譜模型
 * 用於表示 JSP 檔案分析結果的節點和關係
 */
@Getter
@Setter
public class JspKnowledgeGraph {

    private String fileName;
    private Date analysisTimestamp;
    private List<JspNode> nodes = new ArrayList<>();
    private List<JspRelationship> relationships = new ArrayList<>();

    /**
     * 添加節點
     */
    public void addNode(JspNode node) {
        nodes.add(node);
    }

    /**
     * 添加關係
     */
    public void addRelationship(JspRelationship relationship) {
        relationships.add(relationship);
    }

    /**
     * 根據類型查找節點
     */
    public List<JspNode> findNodesByType(String type) {
        return nodes.stream()
                .filter(node -> type.equals(node.getType()))
                .collect(Collectors.toList());
    }

    /**
     * 根據 ID 查找節點
     */
    public Optional<JspNode> findNodeById(String id) {
        return nodes.stream()
                .filter(node -> id.equals(node.getId()))
                .findFirst();
    }

    /**
     * 查找與指定節點相關的關係
     */
    public List<JspRelationship> findRelationshipsByNode(String nodeId) {
        return relationships.stream()
                .filter(rel -> nodeId.equals(rel.getFromNodeId()) || nodeId.equals(rel.getToNodeId()))
                .collect(Collectors.toList());
    }

    /**
     * 生成圖譜摘要
     */
    public String generateSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("JSP 知識圖譜摘要 - ").append(fileName).append("\n");
        summary.append("分析時間: ").append(analysisTimestamp).append("\n");
        summary.append("節點總數: ").append(nodes.size()).append("\n");
        summary.append("關係總數: ").append(relationships.size()).append("\n\n");

        // 按類型統計節點
        Map<String, Long> nodeTypeCount = nodes.stream()
                .collect(Collectors.groupingBy(JspNode::getType, Collectors.counting()));

        summary.append("節點類型分布:\n");
        nodeTypeCount
                .forEach((type, count) -> summary.append("  ").append(type).append(": ").append(count).append(" 個\n"));

        // 按類型統計關係
        Map<String, Long> relationshipTypeCount = relationships.stream()
                .collect(Collectors.groupingBy(JspRelationship::getType, Collectors.counting()));

        summary.append("\n關係類型分布:\n");
        relationshipTypeCount
                .forEach((type, count) -> summary.append("  ").append(type).append(": ").append(count).append(" 個\n"));

        return summary.toString();
    }

    /**
     * 基礎節點類別
     */
    @Getter
    @Setter
    public static class JspNode {
        private String id;
        private String type;
        private String name;
        private Map<String, Object> properties = new HashMap<>();
        private int lineNumber;

        public JspNode(String id, String type, String name) {
            this.id = id;
            this.type = type;
            this.name = name;
        }

        public void addProperty(String key, Object value) {
            properties.put(key, value);
        }

        public Object getProperty(String key) {
            return properties.get(key);
        }
    }

    /**
     * 基礎關係類別
     */
    @Getter
    @Setter
    public static class JspRelationship {
        private String id;
        private String type;
        private String fromNodeId;
        private String toNodeId;
        private Map<String, Object> properties = new HashMap<>();

        public JspRelationship(String id, String type, String fromNodeId, String toNodeId) {
            this.id = id;
            this.type = type;
            this.fromNodeId = fromNodeId;
            this.toNodeId = toNodeId;
        }

        public void addProperty(String key, Object value) {
            properties.put(key, value);
        }

        public Object getProperty(String key) {
            return properties.get(key);
        }
    }

    /**
     * JSF 元件節點
     */
    @Getter
    @Setter
    public static class JsfComponentNode extends JspNode {
        private String action;
        private String eventType;

        public JsfComponentNode(String id, String name, String action) {
            super(id, "JSFComponent", name);
            this.action = action;
        }
    }

    /**
     * JavaScript 函式節點
     */
    @Getter
    @Setter
    public static class JavaScriptFunctionNode extends JspNode {
        private String content;
        private List<String> purposeTags = new ArrayList<>();
        private List<String> interactsWithIds = new ArrayList<>();
        private boolean containsAjaxCall = false;
        private boolean containsNavigation = false;
        private int complexityScore = 0;

        public JavaScriptFunctionNode(String id, String name) {
            super(id, "JSFunction", name);
        }
    }

    /**
     * 後端方法節點
     */
    @Getter
    @Setter
    public static class BackendMethodNode extends JspNode {
        private String className;
        private String methodName;
        private String fullSignature;

        public BackendMethodNode(String id, String className, String methodName) {
            super(id, "BackendMethod", methodName);
            this.className = className;
            this.methodName = methodName;
            this.fullSignature = className + "." + methodName + "()";
        }
    }

    /**
     * 頁面節點
     */
    @Getter
    @Setter
    public static class PageNode extends JspNode {
        private String pagecodeClass;
        private List<String> externalJsReferences = new ArrayList<>();

        public PageNode(String id, String name) {
            super(id, "Page", name);
        }
    }

    /**
     * DOM 元素節點
     */
    @Getter
    @Setter
    public static class DomElementNode extends JspNode {
        private String elementType;
        private String formId;

        public DomElementNode(String id, String name, String elementType) {
            super(id, "DOMElement", name);
            this.elementType = elementType;
        }
    }

    /**
     * 觸發關係
     */
    @Getter
    @Setter
    public static class TriggersRelationship extends JspRelationship {
        private String eventType;

        public TriggersRelationship(String fromNodeId, String toNodeId, String eventType) {
            super(UUID.randomUUID().toString(), "TRIGGERS", fromNodeId, toNodeId);
            this.eventType = eventType;
        }
    }

    /**
     * 互動關係
     */
    @Getter
    @Setter
    public static class InteractsWithRelationship extends JspRelationship {
        private String interactionType;

        public InteractsWithRelationship(String fromNodeId, String toNodeId, String interactionType) {
            super(UUID.randomUUID().toString(), "INTERACTS_WITH", fromNodeId, toNodeId);
            this.interactionType = interactionType;
        }
    }

    /**
     * 包含關係
     */
    @Getter
    @Setter
    public static class ContainsRelationship extends JspRelationship {
        public ContainsRelationship(String fromNodeId, String toNodeId) {
            super(UUID.randomUUID().toString(), "CONTAINS", fromNodeId, toNodeId);
        }
    }

    /**
     * 依賴關係
     */
    @Getter
    @Setter
    public static class DependsOnRelationship extends JspRelationship {
        private String dependencyType;

        public DependsOnRelationship(String fromNodeId, String toNodeId, String dependencyType) {
            super(UUID.randomUUID().toString(), "DEPENDS_ON", fromNodeId, toNodeId);
            this.dependencyType = dependencyType;
        }
    }
}
