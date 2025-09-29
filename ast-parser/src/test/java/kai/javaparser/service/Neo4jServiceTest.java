package kai.javaparser.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import kai.javaparser.config.Neo4jConfig;

/**
 * Neo4j 服務測試類
 * 測試 Neo4j 數據庫連接和基本操作
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class Neo4jServiceTest {

    @Autowired
    private Neo4jService neo4jService;

    @Autowired
    private Neo4jConfig neo4jConfig;

    @BeforeEach
    void setUp() {
        // 確保測試前數據庫是乾淨的
        try {
            neo4jService.clearDatabase();
        } catch (Exception e) {
            // 如果清理失敗，繼續測試
            System.out.println("清理數據庫時出現錯誤: " + e.getMessage());
        }
    }

    @Test
    void testConnection() {
        // 測試數據庫連接
        boolean isConnected = neo4jService.testConnection();
        assertTrue(isConnected, "Neo4j 連接應該成功");
    }

    @Test
    void testCreateNode() {
        // 測試創建節點
        String cypher = "CREATE (n:TestNode {name: $name, value: $value}) RETURN n";
        Map<String, Object> parameters = Map.of(
                "name", "測試節點",
                "value", 123);

        long affectedRecords = neo4jService.executeWrite(cypher, parameters);
        assertEquals(1, affectedRecords, "應該創建 1 個節點");
    }

    @Test
    void testQueryNode() {
        // 先創建一個節點
        String createCypher = "CREATE (n:TestNode {name: $name}) RETURN n";
        Map<String, Object> createParams = Map.of("name", "查詢測試節點");
        neo4jService.executeWrite(createCypher, createParams);

        // 查詢節點
        String queryCypher = "MATCH (n:TestNode {name: $name}) RETURN n.name as name";
        Map<String, Object> queryParams = Map.of("name", "查詢測試節點");

        List<org.neo4j.driver.Record> results = neo4jService.executeQuery(queryCypher, queryParams);

        assertFalse(results.isEmpty(), "應該找到創建的節點");
        assertEquals("查詢測試節點", results.get(0).get("name").asString());
    }

    @Test
    void testCreateRelationship() {
        // 創建兩個節點和它們之間的關係
        String cypher = """
                CREATE (a:Person {name: $person1})
                CREATE (b:Person {name: $person2})
                CREATE (a)-[r:KNOWS {since: $since}]->(b)
                RETURN a, r, b
                """;

        Map<String, Object> parameters = Map.of(
                "person1", "張三",
                "person2", "李四",
                "since", 2024);

        long affectedRecords = neo4jService.executeWrite(cypher, parameters);
        assertTrue(affectedRecords > 0, "應該創建節點和關係");
    }

    @Test
    void testQueryRelationship() {
        // 先創建關係
        String createCypher = """
                CREATE (a:Person {name: '王五'})
                CREATE (b:Person {name: '趙六'})
                CREATE (a)-[r:WORKS_WITH {department: 'IT'}]->(b)
                """;
        neo4jService.executeWrite(createCypher);

        // 查詢關係
        String queryCypher = """
                MATCH (a:Person)-[r:WORKS_WITH]->(b:Person)
                WHERE a.name = '王五' AND b.name = '趙六'
                RETURN a.name as person1, r.department as department, b.name as person2
                """;

        List<org.neo4j.driver.Record> results = neo4jService.executeQuery(queryCypher);

        assertFalse(results.isEmpty(), "應該找到創建的關係");
        org.neo4j.driver.Record record = results.get(0);
        assertEquals("王五", record.get("person1").asString());
        assertEquals("趙六", record.get("person2").asString());
        assertEquals("IT", record.get("department").asString());
    }

    @Test
    void testAsyncQuery() {
        // 創建測試數據
        String createCypher = "CREATE (n:AsyncTest {id: $id, data: $data})";
        Map<String, Object> createParams = Map.of("id", 1, "data", "異步測試數據");
        neo4jService.executeWrite(createCypher, createParams);

        // 異步查詢
        String queryCypher = "MATCH (n:AsyncTest) WHERE n.id = $id RETURN n.data as data";
        Map<String, Object> queryParams = Map.of("id", 1);

        neo4jService.executeQueryAsync(queryCypher, queryParams)
                .thenAccept(results -> {
                    assertFalse(results.isEmpty(), "異步查詢應該返回結果");
                    assertEquals("異步測試數據", results.get(0).get("data").asString());
                })
                .join(); // 等待異步操作完成
    }

    @Test
    void testClearDatabase() {
        // 先創建一些數據
        String createCypher = "CREATE (n:TestNode {name: '測試'})";
        neo4jService.executeWrite(createCypher);

        // 驗證數據存在
        String countCypher = "MATCH (n) RETURN count(n) as count";
        List<org.neo4j.driver.Record> beforeResults = neo4jService.executeQuery(countCypher);
        assertTrue(beforeResults.get(0).get("count").asInt() > 0, "清理前應該有數據");

        // 清理數據庫
        neo4jService.clearDatabase();

        // 驗證數據已被清理
        List<org.neo4j.driver.Record> afterResults = neo4jService.executeQuery(countCypher);
        assertEquals(0, afterResults.get(0).get("count").asInt(), "清理後應該沒有數據");
    }
}
