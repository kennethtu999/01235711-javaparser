package kai.javaparser.jsp.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kai.javaparser.jsp.entity.JSPBackendMethodNode;
import kai.javaparser.jsp.entity.JSPComponentNode;
import kai.javaparser.jsp.entity.JSPFileNode;
import kai.javaparser.jsp.entity.Neo4jJspNode;
import kai.javaparser.jsp.factory.JSPNodeFactory;
import kai.javaparser.jsp.repository.JSPBackendMethodRepository;
import kai.javaparser.jsp.repository.JSPComponentRepository;
import kai.javaparser.jsp.repository.JSPFileRepository;
import kai.javaparser.jsp.repository.Neo4jJspNodeRepository;
import kai.javaparser.jsp.repository.vo.NodeTypeCount;
import kai.javaparser.model.JspKnowledgeGraph;

/**
 * Neo4j JSP 儲存服務
 * 負責將知識圖譜資料儲存到 Neo4j 資料庫
 */
@Service
@Transactional
public class Neo4jJspStorageService {

    private static final Logger logger = LoggerFactory.getLogger(Neo4jJspStorageService.class);

    @Autowired
    private Neo4jJspNodeRepository repository;

    @Autowired
    private JSPFileRepository jspFileRepository;

    @Autowired
    private JSPBackendMethodRepository jspBackendMethodRepository;

    @Autowired
    private JSPComponentRepository jspComponentRepository;

    @Autowired
    private JSPNodeFactory jspNodeFactory;

    /**
     * 儲存 JSP 知識圖譜到 Neo4j
     * 
     * @param knowledgeGraph 知識圖譜
     * @return 儲存的節點數量
     */
    public int saveKnowledgeGraph(JspKnowledgeGraph knowledgeGraph) {
        logger.info("開始儲存 JSP 知識圖譜到 Neo4j: {}", knowledgeGraph.getFileName());

        try {
            // 1. 先刪除現有的相同檔案資料
            repository.deleteByFileName(knowledgeGraph.getFileName());

            // 2. 轉換並儲存所有節點
            Map<String, Object> nodeMap = new HashMap<>();

            for (JspKnowledgeGraph.JspNode node : knowledgeGraph.getNodes()) {
                Object neo4jNode = jspNodeFactory.createNeo4jNode(node, knowledgeGraph.getFileName());
                Object savedNode = saveNodeByType(neo4jNode);
                nodeMap.put(node.getId(), savedNode);
                logger.debug("儲存節點: {} ({})", node.getId(), node.getType());
            }

            // 3. 建立關係
            int relationshipCount = 0;
            for (JspKnowledgeGraph.JspRelationship relationship : knowledgeGraph.getRelationships()) {
                Object fromNode = nodeMap.get(relationship.getFromNodeId());
                Object toNode = nodeMap.get(relationship.getToNodeId());

                if (fromNode != null && toNode != null) {
                    createRelationship(fromNode, toNode, relationship.getType());
                    relationshipCount++;
                }
            }

            logger.info("知識圖譜儲存完成: {} 個節點, {} 個關係", nodeMap.size(), relationshipCount);
            return nodeMap.size();

        } catch (Exception e) {
            logger.error("儲存知識圖譜時發生錯誤: {}", e.getMessage(), e);
            throw new RuntimeException("儲存知識圖譜失敗", e);
        }
    }

    /**
     * 根據節點類型儲存到對應的Repository
     */
    private Object saveNodeByType(Object node) {
        if (node instanceof JSPFileNode) {
            return jspFileRepository.save((JSPFileNode) node);
        } else if (node instanceof JSPBackendMethodNode) {
            return jspBackendMethodRepository.save((JSPBackendMethodNode) node);
        } else if (node instanceof JSPComponentNode) {
            return jspComponentRepository.save((JSPComponentNode) node);
        } else if (node instanceof Neo4jJspNode) {
            return repository.save((Neo4jJspNode) node);
        }
        return null;
    }

    /**
     * 建立節點關係
     */
    private void createRelationship(Object fromNode, Object toNode, String relationshipType) {
        switch (relationshipType) {
            case "TRIGGERS":
                if (fromNode instanceof JSPFileNode) {
                    ((JSPFileNode) fromNode).addTrigger(toNode);
                } else if (fromNode instanceof JSPBackendMethodNode) {
                    ((JSPBackendMethodNode) fromNode).addTrigger(toNode);
                } else if (fromNode instanceof JSPComponentNode) {
                    ((JSPComponentNode) fromNode).addTrigger(toNode);
                } else if (fromNode instanceof Neo4jJspNode) {
                    ((Neo4jJspNode) fromNode).addTrigger(toNode);
                }
                break;
            case "INTERACTS_WITH":
                if (fromNode instanceof JSPFileNode) {
                    ((JSPFileNode) fromNode).addInteractsWith(toNode);
                } else if (fromNode instanceof JSPBackendMethodNode) {
                    ((JSPBackendMethodNode) fromNode).addInteractsWith(toNode);
                } else if (fromNode instanceof JSPComponentNode) {
                    ((JSPComponentNode) fromNode).addInteractsWith(toNode);
                } else if (fromNode instanceof Neo4jJspNode) {
                    ((Neo4jJspNode) fromNode).addInteractsWith(toNode);
                }
                break;
            case "CONTAINS":
                if (fromNode instanceof JSPFileNode) {
                    ((JSPFileNode) fromNode).addContains(toNode);
                } else if (fromNode instanceof JSPBackendMethodNode) {
                    ((JSPBackendMethodNode) fromNode).addContains(toNode);
                } else if (fromNode instanceof JSPComponentNode) {
                    ((JSPComponentNode) fromNode).addContains(toNode);
                } else if (fromNode instanceof Neo4jJspNode) {
                    ((Neo4jJspNode) fromNode).addContains(toNode);
                }
                break;
            case "DEPENDS_ON":
                if (fromNode instanceof JSPFileNode) {
                    ((JSPFileNode) fromNode).addDependsOn(toNode);
                } else if (fromNode instanceof JSPBackendMethodNode) {
                    ((JSPBackendMethodNode) fromNode).addDependsOn(toNode);
                } else if (fromNode instanceof JSPComponentNode) {
                    ((JSPComponentNode) fromNode).addDependsOn(toNode);
                } else if (fromNode instanceof Neo4jJspNode) {
                    ((Neo4jJspNode) fromNode).addDependsOn(toNode);
                }
                break;
        }
        // 儲存更新後的節點
        saveNodeByType(fromNode);
    }

    /**
     * 根據檔案名稱查找所有節點
     */
    public List<Neo4jJspNode> findByFileName(String fileName) {
        return repository.findByFileName(fileName);
    }

    /**
     * 根據節點類型查找節點
     */
    public List<Neo4jJspNode> findByType(String type) {
        return repository.findByType(type);
    }

    /**
     * 查找特定 ID 的節點
     */
    public Optional<Neo4jJspNode> findById(String id) {
        return repository.findById(id);
    }

    /**
     * 查找觸發特定後端方法的元件
     */
    public List<Neo4jJspNode> findComponentsTriggeringMethod(String methodId) {
        return repository.findComponentsTriggeringMethod(methodId);
    }

    /**
     * 查找特定按鈕的工作流程
     */
    public List<Object[]> findButtonWorkflow(String buttonId) {
        return repository.findButtonWorkflow(buttonId);
    }

    /**
     * 查找包含 AJAX 呼叫的函式
     */
    public List<Neo4jJspNode> findFunctionsWithAjaxCalls() {
        return repository.findFunctionsWithAjaxCalls();
    }

    /**
     * 查找包含導航的函式
     */
    public List<Neo4jJspNode> findFunctionsWithNavigation() {
        return repository.findFunctionsWithNavigation();
    }

    /**
     * 根據複雜度評分查找函式
     */
    public List<Neo4jJspNode> findFunctionsByComplexity(Integer minScore) {
        return repository.findFunctionsByComplexity(minScore);
    }

    /**
     * 根據標籤查找函式
     */
    public List<Neo4jJspNode> findFunctionsByTag(String tag) {
        return repository.findFunctionsByTag(tag);
    }

    /**
     * 獲取節點類型統計
     */
    public List<NodeTypeCount> getNodeTypeStatistics() {
        return repository.getNodeTypeStatistics();
    }

    /**
     * 獲取關係類型統計
     */
    public List<NodeTypeCount> getRelationshipTypeStatistics() {
        return repository.getRelationshipTypeStatistics();
    }

    /**
     * 刪除特定檔案的所有資料
     */
    public void deleteByFileName(String fileName) {
        repository.deleteByFileName(fileName);
        logger.info("已刪除檔案 {} 的所有資料", fileName);
    }

    /**
     * 獲取資料庫統計資訊
     */
    public Map<String, Object> getDatabaseStatistics() {
        Map<String, Object> stats = new HashMap<>();

        try {
            // 使用簡單的計數方法避免映射問題
            Long totalNodes = repository.countJspNodes();
            Long totalRelationships = repository.countJspRelationships();

            stats.put("totalNodes", totalNodes != null ? totalNodes : 0L);
            stats.put("totalRelationships", totalRelationships != null ? totalRelationships : 0L);

            // 只有在有節點時才嘗試獲取詳細統計
            Boolean hasNodes = repository.hasJspNodes();
            if (hasNodes != null && hasNodes) {
                try {
                    List<NodeTypeCount> nodeStats = getNodeTypeStatistics();
                    List<NodeTypeCount> relStats = getRelationshipTypeStatistics();
                    stats.put("nodeTypeDistribution", nodeStats);
                    stats.put("relationshipTypeDistribution", relStats);
                } catch (Exception e) {
                    logger.warn("獲取詳細統計失敗，使用空列表: {}", e.getMessage(), e);
                    stats.put("nodeTypeDistribution", new ArrayList<>());
                    stats.put("relationshipTypeDistribution", new ArrayList<>());
                }
            } else {
                // 沒有節點時直接使用空列表
                stats.put("nodeTypeDistribution", new ArrayList<>());
                stats.put("relationshipTypeDistribution", new ArrayList<>());
            }

        } catch (Exception e) {
            logger.error("獲取資料庫統計資訊失敗: {}", e.getMessage(), e);
            stats.put("totalNodes", 0L);
            stats.put("totalRelationships", 0L);
            stats.put("nodeTypeDistribution", new ArrayList<>());
            stats.put("relationshipTypeDistribution", new ArrayList<>());
        }

        return stats;
    }

    /**
     * 清理所有 JSP 資料
     */
    public void clearAllJspData() {
        repository.deleteAllJspData();
        logger.info("已清理所有 JSP 資料");
    }
}
