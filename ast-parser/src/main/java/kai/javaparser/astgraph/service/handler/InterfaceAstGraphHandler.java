package kai.javaparser.astgraph.service.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import kai.javaparser.ast.entity.Neo4jAnnotationNode;
import kai.javaparser.ast.entity.Neo4jInterfaceNode;
import kai.javaparser.ast.entity.Neo4jMethodNode;
import lombok.extern.slf4j.Slf4j;

/**
 * 介面 AST 圖轉換處理器
 * 處理介面的轉換和關係建立
 */
@Slf4j
@Component
public class InterfaceAstGraphHandler extends BaseAstGraphHandler {

    @Override
    public List<Neo4jInterfaceNode> convertToEntities(JsonNode rootNode, String sourceFile, String packageName) {
        List<Neo4jInterfaceNode> interfaceNodes = new ArrayList<>();

        JsonNode sequenceData = rootNode.path("sequenceDiagramData");
        if (sequenceData.isObject()) {
            String classType = sequenceData.path("classType").asText();
            String className = sequenceData.path("classFqn").asText();

            // 檢查是否為介面
            if ("Interface".equalsIgnoreCase(classType) && !className.isEmpty()) {
                Neo4jInterfaceNode interfaceNode = createInterfaceEntityFromSequenceData(sequenceData, className,
                        sourceFile, packageName);
                interfaceNodes.add(interfaceNode);
                log.debug("創建介面節點: {} (ID: {})", className, interfaceNode.getId());
            }
        }

        log.info("介面轉換完成 - 介面數量: {}", interfaceNodes.size());
        return interfaceNodes;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, List<String>> extractMethodRelations(JsonNode rootNode, List<?> nodes,
            List<Neo4jMethodNode> methodNodes) {
        Map<String, List<String>> relations = new HashMap<>();
        List<Neo4jInterfaceNode> interfaceNodes = (List<Neo4jInterfaceNode>) nodes;
        log.debug("開始提取介面與方法的關係，介面數量: {}, 方法數量: {}", interfaceNodes.size(), methodNodes.size());

        for (Neo4jInterfaceNode interfaceNode : interfaceNodes) {
            List<String> methodIds = new ArrayList<>();
            log.debug("處理介面: {} (ID: {})", interfaceNode.getName(), interfaceNode.getId());

            for (Neo4jMethodNode methodNode : methodNodes) {
                log.debug("比較介面名稱: '{}' 與方法類別名稱: '{}'", interfaceNode.getName(), methodNode.getClassName());
                if (interfaceNode.getName().equals(methodNode.getClassName())) {
                    methodIds.add(methodNode.getId());
                    log.debug("找到匹配的方法: {} (ID: {})", methodNode.getName(), methodNode.getId());
                }
            }
            if (!methodIds.isEmpty()) {
                relations.put(interfaceNode.getId(), methodIds);
                log.debug("介面 {} 找到 {} 個方法", interfaceNode.getName(), methodIds.size());
            } else {
                log.debug("介面 {} 沒有找到匹配的方法", interfaceNode.getName());
            }
        }

        log.debug("提取完成，共建立 {} 個介面-方法關係", relations.size());
        return relations;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, List<String>> extractAnnotationRelations(JsonNode rootNode, List<?> nodes,
            List<Neo4jAnnotationNode> annotationNodes) {
        Map<String, List<String>> relations = new HashMap<>();
        List<Neo4jInterfaceNode> interfaceNodes = (List<Neo4jInterfaceNode>) nodes;

        log.debug("開始提取介面與註解的關係，介面數量: {}, 註解數量: {}", interfaceNodes.size(), annotationNodes.size());

        JsonNode sequenceData = rootNode.path("sequenceDiagramData");
        if (sequenceData.isObject()) {
            String classType = sequenceData.path("classType").asText();
            String className = sequenceData.path("classFqn").asText();

            // 只處理介面類型
            if ("Interface".equalsIgnoreCase(classType)) {
                // 找到對應的介面節點
                for (Neo4jInterfaceNode interfaceNode : interfaceNodes) {
                    if (interfaceNode.getName().equals(className)) {
                        List<String> annotationIds = new ArrayList<>();

                        // 提取介面註解
                        JsonNode interfaceAnnotations = sequenceData.path("classAnnotations");
                        if (interfaceAnnotations.isArray()) {
                            for (JsonNode annotation : interfaceAnnotations) {
                                String annotationName = annotation.path("annotationName").asText();
                                if (annotationName.isEmpty()) {
                                    annotationName = annotation.path("simpleName").asText();
                                }

                                // 找到對應的註解節點
                                for (Neo4jAnnotationNode annotationNode : annotationNodes) {
                                    if (annotationNode.getName().equals(annotationName) &&
                                            "Interface".equals(annotationNode.getTargetType())) {
                                        annotationIds.add(annotationNode.getId());
                                        log.debug("介面 {} 與註解 {} 建立關係", interfaceNode.getName(), annotationName);
                                        break;
                                    }
                                }
                            }
                        }

                        if (!annotationIds.isEmpty()) {
                            relations.put(interfaceNode.getId(), annotationIds);
                            log.debug("介面 {} 找到 {} 個註解關係", interfaceNode.getName(), annotationIds.size());
                        }
                        break;
                    }
                }
            }
        }

        log.debug("介面與註解關係提取完成 - 關係數量: {}", relations.size());
        return relations;
    }

    @Override
    public String getHandlerType() {
        return "Interface";
    }
}
