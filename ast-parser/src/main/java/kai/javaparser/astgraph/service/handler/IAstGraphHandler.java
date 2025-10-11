package kai.javaparser.astgraph.service.handler;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import kai.javaparser.ast.entity.Neo4jAnnotationNode;
import kai.javaparser.ast.entity.Neo4jMethodNode;

/**
 * AST 圖轉換處理器接口
 * 定義不同類型節點（類別、介面、抽象類）的處理邏輯
 */
public interface IAstGraphHandler {

        /**
         * 轉換節點為實體
         * 
         * @param rootNode    JSON 根節點
         * @param sourceFile  源文件路徑
         * @param packageName 包名
         * @return 轉換後的節點列表
         */
        List<?> convertToEntities(JsonNode rootNode, String sourceFile, String packageName);

        /**
         * 提取與方法的關係
         * 
         * @param rootNode    JSON 根節點
         * @param nodes       節點列表
         * @param methodNodes 方法節點列表
         * @return 關係映射
         */
        Map<String, List<String>> extractMethodRelations(JsonNode rootNode, List<?> nodes,
                        List<Neo4jMethodNode> methodNodes);

        /**
         * 提取與註解的關係
         * 
         * @param rootNode        JSON 根節點
         * @param nodes           節點列表
         * @param annotationNodes 註解節點列表
         * @return 關係映射
         */
        Map<String, List<String>> extractAnnotationRelations(JsonNode rootNode, List<?> nodes,
                        List<Neo4jAnnotationNode> annotationNodes);

        /**
         * 獲取處理器類型
         * 
         * @return 處理器類型名稱
         */
        String getHandlerType();
}
