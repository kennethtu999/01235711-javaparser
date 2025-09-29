package kai.javaparser.service;

import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.*;
import org.neo4j.driver.exceptions.Neo4jException;
import org.neo4j.driver.summary.SummaryCounters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Neo4j 數據庫服務類
 * 提供與 Neo4j 數據庫交互的基本功能
 */
@Slf4j
@Service
public class Neo4jService {

    private final Driver driver;
    private final SessionConfig sessionConfig;

    @Autowired
    public Neo4jService(Driver driver, SessionConfig sessionConfig) {
        this.driver = driver;
        this.sessionConfig = sessionConfig;
    }

    /**
     * 測試數據庫連接
     * 
     * @return 連接是否成功
     */
    public boolean testConnection() {
        try (Session session = driver.session(sessionConfig)) {
            Result result = session.run("RETURN 1 as test");
            org.neo4j.driver.Record record = result.next();
            log.info("Neo4j 連接測試成功: {}", record.get("test").asInt());
            return true;
        } catch (Neo4jException e) {
            log.error("Neo4j 連接測試失敗: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 執行 Cypher 查詢並返回結果
     * 
     * @param cypher     查詢語句
     * @param parameters 查詢參數
     * @return 查詢結果列表
     */
    public List<org.neo4j.driver.Record> executeQuery(String cypher, Map<String, Object> parameters) {
        try (Session session = driver.session(sessionConfig)) {
            Result result = session.run(cypher, parameters);
            return result.list();
        } catch (Neo4jException e) {
            log.error("執行 Cypher 查詢失敗: {}", e.getMessage());
            throw new RuntimeException("查詢執行失敗", e);
        }
    }

    /**
     * 執行 Cypher 查詢（無參數）
     * 
     * @param cypher 查詢語句
     * @return 查詢結果列表
     */
    public List<org.neo4j.driver.Record> executeQuery(String cypher) {
        return executeQuery(cypher, Map.of());
    }

    /**
     * 執行寫入操作（CREATE, UPDATE, DELETE）
     * 
     * @param cypher     寫入語句
     * @param parameters 查詢參數
     * @return 受影響的記錄數
     */
    public long executeWrite(String cypher, Map<String, Object> parameters) {
        try (Session session = driver.session(sessionConfig)) {
            Result result = session.run(cypher, parameters);
            SummaryCounters counters = result.consume().counters();
            long nodesCreated = counters.nodesCreated();
            long relationshipsCreated = counters.relationshipsCreated();
            long nodesDeleted = counters.nodesDeleted();
            long relationshipsDeleted = counters.relationshipsDeleted();

            log.info("寫入操作完成 - 節點創建: {}, 關係創建: {}, 節點刪除: {}, 關係刪除: {}",
                    nodesCreated, relationshipsCreated, nodesDeleted, relationshipsDeleted);

            return nodesCreated + relationshipsCreated + nodesDeleted + relationshipsDeleted;
        } catch (Neo4jException e) {
            log.error("執行寫入操作失敗: {}", e.getMessage());
            throw new RuntimeException("寫入操作失敗", e);
        }
    }

    /**
     * 執行寫入操作（無參數）
     * 
     * @param cypher 寫入語句
     * @return 受影響的記錄數
     */
    public long executeWrite(String cypher) {
        return executeWrite(cypher, Map.of());
    }

    /**
     * 異步執行查詢
     * 
     * @param cypher     查詢語句
     * @param parameters 查詢參數
     * @return CompletableFuture 包含查詢結果
     */
    public CompletableFuture<List<org.neo4j.driver.Record>> executeQueryAsync(String cypher,
            Map<String, Object> parameters) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return executeQuery(cypher, parameters);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        });
    }

    /**
     * 異步執行寫入操作
     * 
     * @param cypher     寫入語句
     * @param parameters 查詢參數
     * @return CompletableFuture 包含受影響的記錄數
     */
    public CompletableFuture<Long> executeWriteAsync(String cypher, Map<String, Object> parameters) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return executeWrite(cypher, parameters);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        });
    }

    /**
     * 獲取數據庫信息
     * 
     * @return 數據庫信息
     */
    public Map<String, Object> getDatabaseInfo() {
        String cypher = """
                CALL db.info() YIELD name, address, role, requestedStatus, currentStatus, error
                RETURN name, address, role, requestedStatus, currentStatus, error
                """;

        List<org.neo4j.driver.Record> records = executeQuery(cypher);
        if (!records.isEmpty()) {
            org.neo4j.driver.Record record = records.get(0);
            return Map.of(
                    "name", record.get("name").asString(),
                    "address", record.get("address").asString(),
                    "role", record.get("role").asString(),
                    "requestedStatus", record.get("requestedStatus").asString(),
                    "currentStatus", record.get("currentStatus").asString());
        }
        return Map.of();
    }

    /**
     * 清理數據庫（刪除所有節點和關係）
     * 注意：此操作會刪除所有數據，僅用於測試環境
     */
    public void clearDatabase() {
        log.warn("正在清理 Neo4j 數據庫 - 這將刪除所有數據！");
        executeWrite("MATCH (n) DETACH DELETE n");
        log.info("數據庫清理完成");
    }

    /**
     * 關閉驅動程序連接
     */
    public void close() {
        if (driver != null) {
            driver.close();
            log.info("Neo4j 驅動程序已關閉");
        }
    }
}
