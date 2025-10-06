package kai.javaparser.jsp.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import kai.javaparser.jsp.entity.Neo4jJspNode;
import kai.javaparser.jsp.repository.Neo4jJspNodeRepository;

/**
 * Neo4j JSP 查詢服務
 * 提供各種查詢功能來檢索 JSP 知識圖譜資料
 */
@Service
public class Neo4jJspQueryService {

    private static final Logger logger = LoggerFactory.getLogger(Neo4jJspQueryService.class);

    @Autowired
    private Neo4jJspNodeRepository repository;

    /**
     * 查找特定按鈕的工作流程
     */
    public Map<String, Object> findButtonWorkflow(String buttonId) {
        logger.info("查找按鈕工作流程: {}", buttonId);

        Map<String, Object> result = new HashMap<>();
        try {
            List<Object[]> workflow = repository.findButtonWorkflow(buttonId);
            result.put("buttonId", buttonId);
            result.put("workflow", workflow);
            result.put("success", true);
        } catch (Exception e) {
            logger.error("查找按鈕工作流程失敗: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 分析特定檔案的資料
     */
    public Map<String, Object> analyzeFileData(String fileName) {
        logger.info("分析檔案資料: {}", fileName);

        Map<String, Object> result = new HashMap<>();
        try {
            List<Neo4jJspNode> nodes = repository.findByFileName(fileName);
            result.put("fileName", fileName);
            result.put("nodes", nodes);
            result.put("totalNodes", nodes.size());
            result.put("success", true);

        } catch (Exception e) {
            logger.error("分析檔案資料失敗: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 查找包含 AJAX 呼叫的函式
     */
    public Map<String, Object> findAjaxFunctions() {
        logger.info("查找包含 AJAX 呼叫的函式");

        Map<String, Object> result = new HashMap<>();
        try {
            List<Neo4jJspNode> ajaxFunctions = repository.findFunctionsWithAjaxCalls();
            result.put("ajaxFunctions", ajaxFunctions);
            result.put("count", ajaxFunctions.size());
            result.put("success", true);

        } catch (Exception e) {
            logger.error("查找 AJAX 函式失敗: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 查找高複雜度函式
     */
    public Map<String, Object> findHighComplexityFunctions() {
        logger.info("查找高複雜度函式");

        Map<String, Object> result = new HashMap<>();
        try {
            List<Neo4jJspNode> complexFunctions = repository.findFunctionsByComplexity(5);
            result.put("complexFunctions", complexFunctions);
            result.put("count", complexFunctions.size());
            result.put("success", true);

        } catch (Exception e) {
            logger.error("查找高複雜度函式失敗: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 根據標籤查找函式
     */
    public Map<String, Object> findFunctionsByTag(String tag) {
        logger.info("根據標籤查找函式: {}", tag);

        Map<String, Object> result = new HashMap<>();
        try {
            List<Neo4jJspNode> functions = repository.findFunctionsByTag(tag);
            result.put("tag", tag);
            result.put("functions", functions);
            result.put("count", functions.size());
            result.put("success", true);

        } catch (Exception e) {
            logger.error("根據標籤查找函式失敗: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 獲取統計資訊
     */
    public Map<String, Object> getStatistics() {
        logger.info("獲取統計資訊");

        Map<String, Object> result = new HashMap<>();
        try {
            // 節點統計
            result.put("totalNodes", repository.count());

            // 類型分布
            result.put("nodeTypes", repository.getNodeTypeStatistics());

            // 關係類型分布
            result.put("relationshipTypes", repository.getRelationshipTypeStatistics());

            result.put("success", true);

        } catch (Exception e) {
            logger.error("獲取統計資訊失敗: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 生成分析報告
     */
    public Map<String, Object> generateAnalysisReport(String fileName) {
        logger.info("生成分析報告: {}", fileName);

        Map<String, Object> result = new HashMap<>();
        try {
            // 檔案資料
            Map<String, Object> fileData = analyzeFileData(fileName);
            result.put("fileData", fileData);

            // 統計資訊
            Map<String, Object> statistics = getStatistics();
            result.put("statistics", statistics);

            // 特殊函式
            result.put("ajaxFunctions", findAjaxFunctions());
            result.put("complexFunctions", findHighComplexityFunctions());

            result.put("success", true);

        } catch (Exception e) {
            logger.error("生成分析報告失敗: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 查找觸發特定方法的元件
     */
    public Map<String, Object> findComponentsTriggeringMethod(String methodId) {
        logger.info("查找觸發特定方法的元件: {}", methodId);

        Map<String, Object> result = new HashMap<>();
        try {
            List<Neo4jJspNode> components = repository.findComponentsTriggeringMethod(methodId);
            result.put("methodId", methodId);
            result.put("components", components);
            result.put("count", components.size());
            result.put("success", true);

        } catch (Exception e) {
            logger.error("查找觸發方法的元件失敗: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 查找特定節點的詳細資訊
     */
    public Map<String, Object> findNodeDetails(String nodeId) {
        logger.info("查找節點詳細資訊: {}", nodeId);

        Map<String, Object> result = new HashMap<>();
        try {
            Optional<Neo4jJspNode> node = repository.findById(nodeId);
            if (node.isPresent()) {
                result.put("node", node.get());
                result.put("relationships", repository.findNodeRelationships(nodeId));
                result.put("success", true);
            } else {
                result.put("success", false);
                result.put("error", "節點不存在: " + nodeId);
            }

        } catch (Exception e) {
            logger.error("查找節點詳細資訊失敗: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 查找包含導航的函式
     */
    public Map<String, Object> findNavigationFunctions() {
        logger.info("查找包含導航的函式");

        try {
            return findFunctionsByTag("navigation");
        } catch (Exception e) {
            logger.error("查找導航函式失敗: {}", e.getMessage(), e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * 查找資料驗證函式
     */
    public Map<String, Object> findValidationFunctions() {
        logger.info("查找資料驗證函式");

        try {
            return findFunctionsByTag("validation");
        } catch (Exception e) {
            logger.error("查找驗證函式失敗: {}", e.getMessage(), e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * 查找表單提交函式
     */
    public Map<String, Object> findFormSubmissionFunctions() {
        logger.info("查找表單提交函式");

        try {
            return findFunctionsByTag("form-submission");
        } catch (Exception e) {
            logger.error("查找表單提交函式失敗: {}", e.getMessage(), e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * 查找 UI 操作函式
     */
    public Map<String, Object> findUiManipulationFunctions() {
        logger.info("查找 UI 操作函式");

        try {
            return findFunctionsByTag("ui-manipulation");
        } catch (Exception e) {
            logger.error("查找 UI 操作函式失敗: {}", e.getMessage(), e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
    }
}
