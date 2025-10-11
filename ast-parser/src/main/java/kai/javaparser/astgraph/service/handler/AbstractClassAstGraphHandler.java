package kai.javaparser.astgraph.service.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import kai.javaparser.ast.entity.Neo4jAnnotationNode;
import kai.javaparser.ast.entity.Neo4jClassNode;
import kai.javaparser.ast.entity.Neo4jMethodNode;
import lombok.extern.slf4j.Slf4j;

/**
 * 抽象類 AST 圖轉換處理器
 * 處理抽象類的轉換和關係建立
 */
@Slf4j
@Component
public class AbstractClassAstGraphHandler extends BaseAstGraphHandler {

    @Override
    public List<Neo4jClassNode> convertToEntities(JsonNode rootNode, String sourceFile, String packageName) {
        List<Neo4jClassNode> abstractClassNodes = new ArrayList<>();

        JsonNode sequenceData = rootNode.path("sequenceDiagramData");
        if (sequenceData.isObject()) {
            String classType = sequenceData.path("classType").asText();
            String className = sequenceData.path("classFqn").asText();
            boolean isAbstract = sequenceData.path("isAbstract").asBoolean(false);

            // 檢查是否為抽象類
            if (("AbstractClass".equalsIgnoreCase(classType) || isAbstract) && !className.isEmpty()) {
                Neo4jClassNode abstractClassNode = createClassEntityFromSequenceData(sequenceData, className,
                        sourceFile, packageName);
                abstractClassNode.setNodeType("AbstractClass");
                abstractClassNode.setIsAbstract(true);
                abstractClassNodes.add(abstractClassNode);
                log.debug("創建抽象類節點: {} (ID: {})", className, abstractClassNode.getId());
            }
        }

        log.info("抽象類轉換完成 - 抽象類數量: {}", abstractClassNodes.size());
        return abstractClassNodes;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, List<String>> extractMethodRelations(JsonNode rootNode, List<?> nodes,
            List<Neo4jMethodNode> methodNodes) {
        Map<String, List<String>> relations = new HashMap<>();
        List<Neo4jClassNode> abstractClassNodes = (List<Neo4jClassNode>) nodes;

        log.debug("開始提取抽象類與方法的關係，節點數量: {}, 方法數量: {}", abstractClassNodes.size(), methodNodes.size());

        for (Neo4jClassNode abstractClassNode : abstractClassNodes) {
            List<String> methodIds = new ArrayList<>();
            log.debug("處理抽象類: {} (ID: {})", abstractClassNode.getName(), abstractClassNode.getId());

            for (Neo4jMethodNode methodNode : methodNodes) {
                log.debug("比較抽象類名稱: '{}' 與方法類別名稱: '{}'", abstractClassNode.getName(), methodNode.getClassName());
                if (abstractClassNode.getName().equals(methodNode.getClassName())) {
                    methodIds.add(methodNode.getId());
                    log.debug("找到匹配的方法: {} (ID: {})", methodNode.getName(), methodNode.getId());
                }
            }
            if (!methodIds.isEmpty()) {
                relations.put(abstractClassNode.getId(), methodIds);
                log.debug("抽象類 {} 找到 {} 個方法", abstractClassNode.getName(), methodIds.size());
            } else {
                log.debug("抽象類 {} 沒有找到匹配的方法", abstractClassNode.getName());
            }
        }

        log.debug("提取完成，共建立 {} 個抽象類-方法關係", relations.size());
        return relations;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, List<String>> extractAnnotationRelations(JsonNode rootNode, List<?> nodes,
            List<Neo4jAnnotationNode> annotationNodes) {
        Map<String, List<String>> relations = new HashMap<>();
        List<Neo4jClassNode> abstractClassNodes = (List<Neo4jClassNode>) nodes;

        log.debug("開始提取抽象類與註解的關係，節點數量: {}, 註解數量: {}", abstractClassNodes.size(), annotationNodes.size());

        JsonNode sequenceData = rootNode.path("sequenceDiagramData");
        if (sequenceData.isObject()) {
            String className = sequenceData.path("classFqn").asText();

            // 找到對應的抽象類節點
            Neo4jClassNode abstractClassNode = abstractClassNodes.stream()
                    .filter(c -> c.getName().equals(className))
                    .findFirst()
                    .orElse(null);

            if (abstractClassNode != null) {
                List<String> annotationIds = new ArrayList<>();
                log.debug("處理抽象類: {} (ID: {})", abstractClassNode.getName(), abstractClassNode.getId());

                // 提取抽象類註解
                JsonNode classAnnotations = sequenceData.path("classAnnotations");
                if (classAnnotations.isArray()) {
                    for (JsonNode annotation : classAnnotations) {
                        String annotationName = annotation.path("annotationName").asText();
                        if (annotationName.isEmpty()) {
                            annotationName = annotation.path("simpleName").asText();
                        }

                        // 找到對應的註解節點
                        final String finalAnnotationName = annotationName;
                        Neo4jAnnotationNode annotationNode = annotationNodes.stream()
                                .filter(a -> a.getName().equals(finalAnnotationName)
                                        && "Class".equals(a.getTargetType()))
                                .findFirst()
                                .orElse(null);

                        if (annotationNode != null) {
                            annotationIds.add(annotationNode.getId());
                            log.debug("找到匹配的註解: {} (ID: {})", annotationNode.getName(), annotationNode.getId());
                        }
                    }
                }

                if (!annotationIds.isEmpty()) {
                    relations.put(abstractClassNode.getId(), annotationIds);
                    log.debug("抽象類 {} 找到 {} 個註解", abstractClassNode.getName(), annotationIds.size());
                } else {
                    log.debug("抽象類 {} 沒有找到匹配的註解", abstractClassNode.getName());
                }
            } else {
                log.debug("沒有找到對應的抽象類節點: {}", className);
            }
        }

        log.debug("提取完成，共建立 {} 個抽象類-註解關係", relations.size());
        return relations;
    }

    @Override
    public String getHandlerType() {
        return "AbstractClass";
    }
}
