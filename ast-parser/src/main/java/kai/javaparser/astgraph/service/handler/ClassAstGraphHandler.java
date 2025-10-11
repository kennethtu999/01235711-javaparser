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
 * 類別 AST 圖轉換處理器
 * 處理普通類別的轉換和關係建立
 */
@Slf4j
@Component
public class ClassAstGraphHandler extends BaseAstGraphHandler {

    @Override
    public List<Neo4jClassNode> convertToEntities(JsonNode rootNode, String sourceFile, String packageName) {
        List<Neo4jClassNode> classNodes = new ArrayList<>();

        JsonNode sequenceData = rootNode.path("sequenceDiagramData");
        if (sequenceData.isObject()) {
            String classType = sequenceData.path("classType").asText();
            String className = sequenceData.path("classFqn").asText();

            // 只處理普通類別，不處理抽象類和介面
            if (!className.isEmpty() &&
                    !"Interface".equalsIgnoreCase(classType)) {
                Neo4jClassNode classNode = createClassEntityFromSequenceData(sequenceData, className, sourceFile,
                        packageName);
                classNode.setNodeType("Class");
                classNodes.add(classNode);
                log.debug("創建普通類別節點: {} (ID: {})", className, classNode.getId());
            }
        }

        log.info("普通類別轉換完成 - 類別數量: {}", classNodes.size());
        return classNodes;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, List<String>> extractMethodRelations(JsonNode rootNode, List<?> nodes,
            List<Neo4jMethodNode> methodNodes) {
        Map<String, List<String>> relations = new HashMap<>();
        List<Neo4jClassNode> classNodes = (List<Neo4jClassNode>) nodes;

        log.debug("開始提取類別與方法的關係，節點數量: {}, 方法數量: {}", classNodes.size(), methodNodes.size());

        for (Neo4jClassNode classNode : classNodes) {
            List<String> methodIds = new ArrayList<>();
            log.debug("處理類別: {} (ID: {})", classNode.getName(), classNode.getId());

            for (Neo4jMethodNode methodNode : methodNodes) {
                log.debug("比較類別名稱: '{}' 與方法類別名稱: '{}'", classNode.getName(), methodNode.getClassName());
                if (classNode.getName().equals(methodNode.getClassName())) {
                    methodIds.add(methodNode.getId());
                    log.debug("找到匹配的方法: {} (ID: {})", methodNode.getName(), methodNode.getId());
                }
            }
            if (!methodIds.isEmpty()) {
                relations.put(classNode.getId(), methodIds);
                log.debug("類別 {} 找到 {} 個方法", classNode.getName(), methodIds.size());
            } else {
                log.debug("類別 {} 沒有找到匹配的方法", classNode.getName());
            }
        }

        log.debug("提取完成，共建立 {} 個類別-方法關係", relations.size());
        return relations;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, List<String>> extractAnnotationRelations(JsonNode rootNode, List<?> nodes,
            List<Neo4jAnnotationNode> annotationNodes) {
        Map<String, List<String>> relations = new HashMap<>();
        List<Neo4jClassNode> classNodes = (List<Neo4jClassNode>) nodes;

        log.debug("開始提取類別與註解的關係，節點數量: {}, 註解數量: {}", classNodes.size(), annotationNodes.size());

        JsonNode sequenceData = rootNode.path("sequenceDiagramData");
        if (sequenceData.isObject()) {
            String className = sequenceData.path("classFqn").asText();

            // 找到對應的類別節點
            Neo4jClassNode classNode = classNodes.stream()
                    .filter(c -> c.getName().equals(className))
                    .findFirst()
                    .orElse(null);

            if (classNode != null) {
                List<String> annotationIds = new ArrayList<>();
                log.debug("處理類別: {} (ID: {})", classNode.getName(), classNode.getId());

                // 提取類別註解
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
                    relations.put(classNode.getId(), annotationIds);
                    log.debug("類別 {} 找到 {} 個註解", classNode.getName(), annotationIds.size());
                } else {
                    log.debug("類別 {} 沒有找到匹配的註解", classNode.getName());
                }
            } else {
                log.debug("沒有找到對應的類別節點: {}", className);
            }
        }

        log.debug("提取完成，共建立 {} 個類別-註解關係", relations.size());
        return relations;
    }

    @Override
    public String getHandlerType() {
        return "Class";
    }
}
