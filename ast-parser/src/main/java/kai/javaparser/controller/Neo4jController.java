package kai.javaparser.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kai.javaparser.service.Neo4jService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Neo4j 數據庫控制器
 * 提供 Neo4j 數據庫的基本操作 API
 */
@Slf4j
@RestController
@RequestMapping("/api/neo4j")
@Tag(name = "Neo4j 數據庫", description = "Neo4j 圖數據庫操作 API")
public class Neo4jController {

    @Autowired
    private Neo4jService neo4jService;

    @GetMapping("/health")
    @Operation(summary = "檢查 Neo4j 連接狀態", description = "測試與 Neo4j 數據庫的連接是否正常")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();

        try {
            boolean isConnected = neo4jService.testConnection();
            response.put("status", isConnected ? "UP" : "DOWN");
            response.put("connected", isConnected);
            response.put("message", isConnected ? "Neo4j 連接正常" : "Neo4j 連接失敗");

            if (isConnected) {
                Map<String, Object> dbInfo = neo4jService.getDatabaseInfo();
                response.put("database", dbInfo);
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Neo4j 健康檢查失敗", e);
            response.put("status", "DOWN");
            response.put("connected", false);
            response.put("message", "Neo4j 連接錯誤: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/query")
    @Operation(summary = "執行 Cypher 查詢", description = "執行指定的 Cypher 查詢語句")
    public ResponseEntity<Map<String, Object>> executeQuery(@RequestBody Map<String, Object> request) {
        try {
            String cypher = (String) request.get("cypher");
            @SuppressWarnings("unchecked")
            Map<String, Object> parameters = (Map<String, Object>) request.getOrDefault("parameters", new HashMap<>());

            if (cypher == null || cypher.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Cypher 查詢語句不能為空"));
            }

            List<org.neo4j.driver.Record> results = neo4jService.executeQuery(cypher, parameters);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("results", results);
            response.put("count", results.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("執行 Cypher 查詢失敗", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "查詢執行失敗: " + e.getMessage()));
        }
    }
}
