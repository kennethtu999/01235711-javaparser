package kai.javaparser.ast.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import kai.javaparser.service.Neo4jService;
import lombok.extern.slf4j.Slf4j;

/**
 * Neo4j 索引管理服務
 * 負責創建和管理 Neo4j 數據庫索引，提高查詢性能
 */
@Slf4j
@Service
public class Neo4jIndexService {

    @Autowired
    private Neo4jService neo4jService;

    /**
     * 創建所有必要的索引
     */
    public void createAllIndexes() {
        log.info("開始創建 Neo4j 索引");

        try {
            // 類別節點索引
            createClassIndexes();

            // 方法節點索引
            createMethodIndexes();

            // 註解節點索引
            createAnnotationIndexes();

            // 介面節點索引
            createInterfaceIndexes();

            log.info("所有索引創建完成");
        } catch (Exception e) {
            log.error("創建索引失敗", e);
            throw new RuntimeException("創建索引失敗", e);
        }
    }

    /**
     * 創建類別節點索引
     */
    private void createClassIndexes() {
        log.info("創建類別節點索引");

        // 主要查詢字段索引
        createIndexIfNotExists("Class", "id", "class_id_index");
        createIndexIfNotExists("Class", "name", "class_name_index");
        createIndexIfNotExists("Class", "package", "class_package_index");
        createIndexIfNotExists("Class", "sourceFile", "class_source_file_index");
        createIndexIfNotExists("Class", "nodeType", "class_node_type_index");

        // 複合索引
        createCompositeIndexIfNotExists("Class", List.of("name", "package"), "class_name_package_index");
        createCompositeIndexIfNotExists("Class", List.of("package", "nodeType"), "class_package_type_index");
    }

    /**
     * 創建方法節點索引
     */
    private void createMethodIndexes() {
        log.info("創建方法節點索引");

        // 主要查詢字段索引
        createIndexIfNotExists("Method", "id", "method_id_index");
        createIndexIfNotExists("Method", "name", "method_name_index");
        createIndexIfNotExists("Method", "className", "method_class_name_index");
        createIndexIfNotExists("Method", "package", "method_package_index");
        createIndexIfNotExists("Method", "sourceFile", "method_source_file_index");
        createIndexIfNotExists("Method", "returnType", "method_return_type_index");

        // 複合索引
        createCompositeIndexIfNotExists("Method", List.of("name", "className"), "method_name_class_index");
        createCompositeIndexIfNotExists("Method", List.of("className", "package"), "method_class_package_index");
    }

    /**
     * 創建註解節點索引
     */
    private void createAnnotationIndexes() {
        log.info("創建註解節點索引");

        // 主要查詢字段索引
        createIndexIfNotExists("Annotation", "id", "annotation_id_index");
        createIndexIfNotExists("Annotation", "name", "annotation_name_index");
        createIndexIfNotExists("Annotation", "targetType", "annotation_target_type_index");
        createIndexIfNotExists("Annotation", "sourceFile", "annotation_source_file_index");

        // 複合索引
        createCompositeIndexIfNotExists("Annotation", List.of("name", "targetType"), "annotation_name_target_index");
    }

    /**
     * 創建介面節點索引
     */
    private void createInterfaceIndexes() {
        log.info("創建介面節點索引");

        // 主要查詢字段索引
        createIndexIfNotExists("Interface", "id", "interface_id_index");
        createIndexIfNotExists("Interface", "name", "interface_name_index");
        createIndexIfNotExists("Interface", "package", "interface_package_index");
        createIndexIfNotExists("Interface", "sourceFile", "interface_source_file_index");

        // 複合索引
        createCompositeIndexIfNotExists("Interface", List.of("name", "package"), "interface_name_package_index");
    }

    /**
     * 創建單一字段索引
     */
    private void createIndexIfNotExists(String label, String property, String indexName) {
        String cypher = String.format(
                "CREATE INDEX %s IF NOT EXISTS FOR (n:%s) ON (n.%s)",
                indexName, label, property);

        try {
            neo4jService.executeWrite(cypher);
            log.debug("創建索引成功: {} on {}.{}", indexName, label, property);
        } catch (Exception e) {
            log.warn("創建索引失敗: {} on {}.{} - {}", indexName, label, property, e.getMessage());
        }
    }

    /**
     * 創建複合索引
     */
    private void createCompositeIndexIfNotExists(String label, List<String> properties, String indexName) {
        String propertiesStr = String.join(", ", properties.stream()
                .map(prop -> "n." + prop)
                .toArray(String[]::new));

        String cypher = String.format(
                "CREATE INDEX %s IF NOT EXISTS FOR (n:%s) ON (%s)",
                indexName, label, propertiesStr);

        try {
            neo4jService.executeWrite(cypher);
            log.debug("創建複合索引成功: {} on {}.({})", indexName, label, String.join(", ", properties));
        } catch (Exception e) {
            log.warn("創建複合索引失敗: {} on {}.({}) - {}", indexName, label, String.join(", ", properties), e.getMessage());
        }
    }

    /**
     * 刪除所有索引
     */
    public void dropAllIndexes() {
        log.info("開始刪除所有索引");

        try {
            String cypher = "SHOW INDEXES";
            List<org.neo4j.driver.Record> indexes = neo4jService.executeQuery(cypher);

            for (org.neo4j.driver.Record index : indexes) {
                String indexName = index.get("name").asString();
                if (indexName != null && !indexName.startsWith("system")) {
                    try {
                        String dropCypher = "DROP INDEX " + indexName;
                        neo4jService.executeWrite(dropCypher);
                        log.debug("刪除索引: {}", indexName);
                    } catch (Exception e) {
                        log.warn("刪除索引失敗: {} - {}", indexName, e.getMessage());
                    }
                }
            }

            log.info("所有索引刪除完成");
        } catch (Exception e) {
            log.error("刪除索引失敗", e);
            throw new RuntimeException("刪除索引失敗", e);
        }
    }

    /**
     * 獲取所有索引信息
     */
    public List<org.neo4j.driver.Record> getAllIndexes() {
        String cypher = "SHOW INDEXES";
        return neo4jService.executeQuery(cypher);
    }

    /**
     * 檢查索引是否存在
     */
    public boolean indexExists(String indexName) {
        String cypher = "SHOW INDEXES";
        List<org.neo4j.driver.Record> indexes = neo4jService.executeQuery(cypher);

        return indexes.stream()
                .anyMatch(index -> indexName.equals(index.get("name").asString()));
    }
}
