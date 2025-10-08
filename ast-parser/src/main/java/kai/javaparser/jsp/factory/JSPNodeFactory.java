package kai.javaparser.jsp.factory;

import org.springframework.stereotype.Component;

import kai.javaparser.jsp.entity.JSPBackendMethodNode;
import kai.javaparser.jsp.entity.JSPComponentNode;
import kai.javaparser.jsp.entity.JSPFileNode;
import kai.javaparser.jsp.entity.Neo4jJspNode;
import kai.javaparser.jsp.model.JspKnowledgeGraph;

/**
 * JSP 節點工廠
 * 根據節點類型創建對應的 Neo4j 實體
 */
@Component
public class JSPNodeFactory {

    /**
     * 根據知識圖譜節點創建對應的 Neo4j 實體
     * 
     * @param node     知識圖譜節點
     * @param fileName 檔案名稱
     * @return Neo4j 實體
     */
    public Object createNeo4jNode(JspKnowledgeGraph.JspNode node, String fileName) {
        switch (node.getType()) {
            case "Page":
                return createJSPFileNode(node, fileName);
            case "BackendMethod":
                return createJSPBackendMethodNode(node, fileName);
            case "JSFComponent":
            case "DOMElement":
                return createJSPComponentNode(node, fileName);
            default:
                // 其他類型都使用通用節點
                return createGenericNode(node, fileName);
        }
    }

    /**
     * 創建 JSP 檔案節點
     */
    private JSPFileNode createJSPFileNode(JspKnowledgeGraph.JspNode node, String fileName) {
        JSPFileNode jspFileNode = new JSPFileNode(node.getId(), node.getName());
        jspFileNode.setLineNumber(node.getLineNumber());
        jspFileNode.setFileName(fileName);

        if (node instanceof JspKnowledgeGraph.PageNode) {
            JspKnowledgeGraph.PageNode pageNode = (JspKnowledgeGraph.PageNode) node;
            jspFileNode.setPagecodeClass(pageNode.getPagecodeClass());
            jspFileNode.setExternalJsReferences(pageNode.getExternalJsReferences());
        }

        return jspFileNode;
    }

    /**
     * 創建 JSP 後端方法節點
     */
    private JSPBackendMethodNode createJSPBackendMethodNode(JspKnowledgeGraph.JspNode node, String fileName) {
        JSPBackendMethodNode jspBackendMethodNode = new JSPBackendMethodNode(node.getId(), node.getName());
        jspBackendMethodNode.setLineNumber(node.getLineNumber());
        jspBackendMethodNode.setFileName(fileName);

        if (node instanceof JspKnowledgeGraph.BackendMethodNode) {
            JspKnowledgeGraph.BackendMethodNode methodNode = (JspKnowledgeGraph.BackendMethodNode) node;
            jspBackendMethodNode.setClassName(methodNode.getClassName());
            jspBackendMethodNode.setMethodName(methodNode.getMethodName());
            jspBackendMethodNode.setFullSignature(methodNode.getFullSignature());
        }

        return jspBackendMethodNode;
    }

    /**
     * 創建 JSP 元件節點
     */
    private JSPComponentNode createJSPComponentNode(JspKnowledgeGraph.JspNode node, String fileName) {
        JSPComponentNode jspComponentNode = new JSPComponentNode(node.getId(), node.getName());
        jspComponentNode.setLineNumber(node.getLineNumber());
        jspComponentNode.setFileName(fileName);

        if (node instanceof JspKnowledgeGraph.JsfComponentNode) {
            JspKnowledgeGraph.JsfComponentNode jsfNode = (JspKnowledgeGraph.JsfComponentNode) node;
            jspComponentNode.setComponentType(jsfNode.getType());
            jspComponentNode.setAction(jsfNode.getAction());
            jspComponentNode.setEventType(jsfNode.getEventType());
        } else if (node instanceof JspKnowledgeGraph.DomElementNode) {
            JspKnowledgeGraph.DomElementNode domNode = (JspKnowledgeGraph.DomElementNode) node;
            jspComponentNode.setComponentType(domNode.getElementType());
            jspComponentNode.setTagName(domNode.getElementType());
            jspComponentNode.setFormId(domNode.getFormId());
        }

        return jspComponentNode;
    }

    /**
     * 創建通用節點（用於其他類型）
     */
    private Neo4jJspNode createGenericNode(JspKnowledgeGraph.JspNode node, String fileName) {
        Neo4jJspNode neo4jNode = new Neo4jJspNode(node.getId(), node.getType(), node.getName());
        neo4jNode.setLineNumber(node.getLineNumber());
        neo4jNode.setFileName(fileName);

        // 根據節點類型設定特定屬性
        switch (node.getType()) {
            case "JSFunction":
                if (node instanceof JspKnowledgeGraph.JavaScriptFunctionNode) {
                    JspKnowledgeGraph.JavaScriptFunctionNode jsNode = (JspKnowledgeGraph.JavaScriptFunctionNode) node;
                    neo4jNode.setFunctionContent(jsNode.getContent());
                    neo4jNode.setContainsAjaxCall(jsNode.isContainsAjaxCall());
                    neo4jNode.setContainsNavigation(jsNode.isContainsNavigation());
                    neo4jNode.setComplexityScore(jsNode.getComplexityScore());
                    neo4jNode.setPurposeTags(jsNode.getPurposeTags());
                    neo4jNode.setInteractsWithIds(jsNode.getInteractsWithIds());
                }
                break;
        }

        return neo4jNode;
    }
}
