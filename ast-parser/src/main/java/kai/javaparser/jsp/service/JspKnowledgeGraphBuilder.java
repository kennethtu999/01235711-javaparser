package kai.javaparser.jsp.service;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import kai.javaparser.jsp.model.JspKnowledgeGraph;
import kai.javaparser.jsp.model.JspAnalysisResult;
import kai.javaparser.jsp.model.JspKnowledgeGraph.BackendMethodNode;
import kai.javaparser.jsp.model.JspKnowledgeGraph.ContainsRelationship;
import kai.javaparser.jsp.model.JspKnowledgeGraph.DependsOnRelationship;
import kai.javaparser.jsp.model.JspKnowledgeGraph.DomElementNode;
import kai.javaparser.jsp.model.JspKnowledgeGraph.InteractsWithRelationship;
import kai.javaparser.jsp.model.JspKnowledgeGraph.JavaScriptFunctionNode;
import kai.javaparser.jsp.model.JspKnowledgeGraph.JsfComponentNode;
import kai.javaparser.jsp.model.JspKnowledgeGraph.JspNode;
import kai.javaparser.jsp.model.JspKnowledgeGraph.PageNode;
import kai.javaparser.jsp.model.JspKnowledgeGraph.TriggersRelationship;

/**
 * JSP 知識圖譜建構器
 * 將 JSP 分析結果轉換為知識圖譜表示
 */
@Service
public class JspKnowledgeGraphBuilder {

    private static final Logger logger = LoggerFactory.getLogger(JspKnowledgeGraphBuilder.class);

    @Autowired
    private JspAiAnalysisService aiAnalysisService;

    @Autowired
    private Neo4jJspStorageService neo4jStorageService;

    /**
     * 從 JSP 分析結果建構知識圖譜
     * 
     * @param analysisResult JSP 分析結果
     * @return 知識圖譜
     */
    public JspKnowledgeGraph buildKnowledgeGraph(JspAnalysisResult analysisResult) {
        logger.info("開始建構 JSP 知識圖譜: {}", analysisResult.getFileName());

        JspKnowledgeGraph graph = new JspKnowledgeGraph();
        graph.setFileName(analysisResult.getFileName());
        graph.setAnalysisTimestamp(analysisResult.getAnalysisTimestamp());

        try {
            // 1. 建立頁面節點
            PageNode pageNode = createPageNode(analysisResult);
            graph.addNode(pageNode);

            // 2. 建立 JSF 元件節點和關係
            createJsfComponentNodes(analysisResult, graph, pageNode);

            // 3. 建立 JavaScript 函式節點和關係
            createJavaScriptFunctionNodes(analysisResult, graph, pageNode);

            // 4. 建立後端方法節點和關係
            createBackendMethodNodes(analysisResult, graph);

            // 5. 建立 DOM 元素節點和關係
            // createDomElementNodes(analysisResult, graph);

            // 6. 建立外部依賴關係
            createExternalDependencyRelationships(analysisResult, graph, pageNode);

            logger.info("JSP 知識圖譜建構完成: {} - {} 個節點, {} 個關係",
                    analysisResult.getFileName(), graph.getNodes().size(), graph.getRelationships().size());

            // 儲存到 Neo4j
            try {
                int savedNodes = neo4jStorageService.saveKnowledgeGraph(graph);
                logger.info("知識圖譜已儲存到 Neo4j: {} 個節點", savedNodes);
            } catch (Exception e) {
                logger.warn("儲存到 Neo4j 失敗: {}", e.getMessage());
                // 不中斷流程，繼續返回圖譜
            }

        } catch (Exception e) {
            logger.error("建構知識圖譜時發生錯誤: {}", e.getMessage(), e);
        }

        return graph;
    }

    /**
     * 建立頁面節點
     */
    private PageNode createPageNode(JspAnalysisResult analysisResult) {
        PageNode pageNode = new PageNode("page_" + analysisResult.getFileName(), analysisResult.getFileName());
        pageNode.setPagecodeClass(analysisResult.getPagecodeClass());
        pageNode.setExternalJsReferences(analysisResult.getExternalJsReferences());
        pageNode.setLineNumber(1);

        pageNode.addProperty("fileType", "JSP");
        pageNode.addProperty("analysisTimestamp", analysisResult.getAnalysisTimestamp());

        return pageNode;
    }

    /**
     * 建立 JSF 元件節點和關係
     */
    private void createJsfComponentNodes(JspAnalysisResult analysisResult,
            JspKnowledgeGraph graph, PageNode pageNode) {

        for (kai.javaparser.jsp.model.JsfComponent jsfComponent : analysisResult.getJsfComponents()) {
            // 建立 JSF 元件節點
            JsfComponentNode componentNode = new JsfComponentNode(
                    jsfComponent.getId(),
                    jsfComponent.getId(),
                    jsfComponent.getAction());
            componentNode.setEventType(jsfComponent.getEventType());
            componentNode.setLineNumber(jsfComponent.getLineNumber());
            componentNode.addProperty("componentType", jsfComponent.getType());

            graph.addNode(componentNode);

            // 建立頁面包含元件關係
            ContainsRelationship containsRel = new ContainsRelationship(pageNode.getId(), componentNode.getId());
            graph.addRelationship(containsRel);

            // 如果元件有 action，建立觸發後端方法的關係
            if (jsfComponent.getAction() != null && !jsfComponent.getAction().isEmpty()) {
                String backendMethodId = "method_" + jsfComponent.getAction().replace(".", "_");
                String eventType = jsfComponent.getEventType() != null ? jsfComponent.getEventType() : "action";

                TriggersRelationship triggersRel = new TriggersRelationship(
                        componentNode.getId(),
                        backendMethodId,
                        eventType);
                graph.addRelationship(triggersRel);
            }
        }
    }

    /**
     * 建立 JavaScript 函式節點和關係
     */
    private void createJavaScriptFunctionNodes(JspAnalysisResult analysisResult,
            JspKnowledgeGraph graph, PageNode pageNode) {

        for (kai.javaparser.jsp.model.JavaScriptFunction jsFunction : analysisResult.getJavascriptFunctions()) {
            // 建立 JavaScript 函式節點
            JavaScriptFunctionNode functionNode = new JavaScriptFunctionNode(
                    "js_" + jsFunction.getName(),
                    jsFunction.getName());
            functionNode.setContent(jsFunction.getContent());
            functionNode.setLineNumber(jsFunction.getLineNumber());
            functionNode.setContainsAjaxCall(jsFunction.isContainsAjaxCall());
            functionNode.setContainsNavigation(jsFunction.isContainsNavigation());

            // 使用 AI 分析結果
            if (jsFunction.getAiAnalysis() != null) {
                try {
                    JspAiAnalysisService.JavaScriptAnalysisResult aiResult = aiAnalysisService
                            .analyzeJavaScriptFunction(
                                    jsFunction.getName(),
                                    jsFunction.getContent(),
                                    jsFunction.getLineNumber());

                    functionNode.setPurposeTags(aiResult.getPurposeTags());
                    functionNode.setInteractsWithIds(aiResult.getInteractsWithIds());
                    functionNode.setComplexityScore(aiResult.getComplexityScore());

                    functionNode.addProperty("summary", aiResult.getSummary());
                    functionNode.addProperty("dataFlow", aiResult.getDataFlow());
                    functionNode.addProperty("businessLogic", aiResult.getBusinessLogic());
                    functionNode.addProperty("maintainabilityNotes", aiResult.getMaintainabilityNotes());

                } catch (Exception e) {
                    logger.warn("AI 分析函式失敗: {} - {}", jsFunction.getName(), e.getMessage());
                }
            }

            graph.addNode(functionNode);

            // 建立頁面包含函式關係
            ContainsRelationship containsRel = new ContainsRelationship(pageNode.getId(), functionNode.getId());
            graph.addRelationship(containsRel);

            // 建立函式與 DOM 元素的互動關係
            for (String elementId : functionNode.getInteractsWithIds()) {
                String domElementId = "dom_" + elementId.replace(":", "_");
                InteractsWithRelationship interactsRel = new InteractsWithRelationship(
                        functionNode.getId(),
                        domElementId,
                        "manipulates");
                graph.addRelationship(interactsRel);
            }

            // 建立函式與後端方法的關係（如果有 AJAX 呼叫）
            if (jsFunction.isContainsAjaxCall()) {
                for (String ajaxCall : jsFunction.getAjaxCalls()) {
                    String backendMethodId = "method_" + ajaxCall;
                    TriggersRelationship triggersRel = new TriggersRelationship(
                            functionNode.getId(),
                            backendMethodId,
                            "ajax");
                    graph.addRelationship(triggersRel);
                }
            }
        }
    }

    /**
     * 建立後端方法節點
     */
    private void createBackendMethodNodes(JspAnalysisResult analysisResult,
            JspKnowledgeGraph graph) {

        Set<String> methodNames = new HashSet<>();

        // 從 JSF 元件收集方法名稱
        for (kai.javaparser.jsp.model.JsfComponent jsfComponent : analysisResult.getJsfComponents()) {
            if (jsfComponent.getAction() != null && !jsfComponent.getAction().isEmpty()) {
                methodNames.add(jsfComponent.getAction());
            }
        }

        // 從 JavaScript 函式收集 AJAX 呼叫
        for (kai.javaparser.jsp.model.JavaScriptFunction jsFunction : analysisResult.getJavascriptFunctions()) {
            methodNames.addAll(jsFunction.getAjaxCalls());
        }

        // 建立後端方法節點
        for (String methodName : methodNames) {
            String className = analysisResult.getPagecodeClass();
            if (className == null) {
                className = "unknown";
            }

            String methodId = "method_" + methodName.replace(".", "_");
            BackendMethodNode methodNode = new BackendMethodNode(methodId, className, methodName);
            methodNode.addProperty("source", "JSP Analysis");

            graph.addNode(methodNode);
        }
    }

    /**
     * 建立 DOM 元素節點
     */
    private void createDomElementNodes(JspAnalysisResult analysisResult,
            JspKnowledgeGraph graph) {

        Set<String> elementIds = new HashSet<>();

        // 從 JavaScript 函式收集互動的元素 ID
        for (kai.javaparser.jsp.model.JavaScriptFunction jsFunction : analysisResult.getJavascriptFunctions()) {
            // 這裡可以從 AI 分析結果中獲取更詳細的互動元素
            // 暫時使用基本的方法
            elementIds.addAll(extractElementIdsFromFunction(jsFunction.getContent()));
        }

        // 建立 DOM 元素節點
        for (String elementId : elementIds) {
            String domElementId = "dom_" + elementId.replace(":", "_");
            String elementType = determineElementType(elementId);

            DomElementNode domNode = new DomElementNode(domElementId, elementId, elementType);
            domNode.addProperty("originalId", elementId);

            graph.addNode(domNode);
        }
    }

    /**
     * 建立外部依賴關係
     */
    private void createExternalDependencyRelationships(JspAnalysisResult analysisResult,
            JspKnowledgeGraph graph, PageNode pageNode) {

        for (String jsReference : analysisResult.getExternalJsReferences()) {
            String externalId = "external_" + jsReference.replaceAll("[^a-zA-Z0-9]", "_");

            JspNode externalNode = new JspNode(externalId, "ExternalJS", jsReference);
            externalNode.addProperty("filePath", jsReference);
            externalNode.addProperty("type", "JavaScript Library");

            graph.addNode(externalNode);

            // 建立頁面依賴外部 JS 的關係
            DependsOnRelationship dependsRel = new DependsOnRelationship(
                    pageNode.getId(),
                    externalNode.getId(),
                    "script_dependency");
            graph.addRelationship(dependsRel);
        }
    }

    /**
     * 從函式內容中提取元素 ID
     */
    private Set<String> extractElementIdsFromFunction(String functionContent) {
        Set<String> elementIds = new HashSet<>();

        // 使用正規表示式提取 getElementById 中的 ID
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "document\\.getElementById\\([\"']([^\"']+)[\"']\\)",
                java.util.regex.Pattern.CASE_INSENSITIVE);

        java.util.regex.Matcher matcher = pattern.matcher(functionContent);
        while (matcher.find()) {
            elementIds.add(matcher.group(1));
        }

        return elementIds;
    }

    /**
     * 根據元素 ID 判斷元素類型
     */
    private String determineElementType(String elementId) {
        if (elementId.contains("btn"))
            return "Button";
        if (elementId.contains("txt") || elementId.contains("input"))
            return "Input";
        if (elementId.contains("combo"))
            return "ComboBox";
        if (elementId.contains("rdo"))
            return "RadioButton";
        if (elementId.contains("chk"))
            return "CheckBox";
        if (elementId.contains("date"))
            return "DatePicker";
        if (elementId.contains("form"))
            return "Form";
        return "Unknown";
    }
}
