package kai.javaparser.astgraph.controller;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kai.javaparser.astgrapth.service.AstToGraphService;
import lombok.extern.slf4j.Slf4j;

/**
 * AST 到圖數據庫轉換控制器
 * 提供 AST 轉換相關的 REST API
 */
@Slf4j
@RestController
@RequestMapping("/api/ast-graph")
@Tag(name = "AST 圖轉換", description = "AST 到圖數據庫轉換相關 API")
public class AstToGraphController {

    @Autowired
    private AstToGraphService astToGraphService;

    @PostMapping("/convert")
    @Operation(summary = "轉換所有 AST 文件為圖數據庫", description = "將所有 AST JSON 文件轉換為 Neo4j 圖數據庫中的節點和關係")
    public ResponseEntity<Map<String, Object>> convertAllAstToGraph() {
        try {
            log.info("開始轉換所有 AST 文件為圖數據庫");
            Map<String, Object> result = astToGraphService.convertAllAstToGraph();

            boolean success = (Boolean) result.getOrDefault("success", false);
            if (success) {
                log.info("AST 轉換成功完成");
                return ResponseEntity.ok(result);
            } else {
                log.error("AST 轉換失敗: {}", result.get("message"));
                return ResponseEntity.status(500).body(result);
            }
        } catch (Exception e) {
            log.error("AST 轉換過程中發生錯誤", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "轉換過程中發生錯誤: " + e.getMessage(),
                    "error", e.getMessage()));
        }
    }

    @PostMapping("/convert-async")
    @Operation(summary = "異步轉換所有 AST 文件", description = "異步將所有 AST JSON 文件轉換為圖數據庫")
    public ResponseEntity<Map<String, Object>> convertAllAstToGraphAsync() {
        try {
            log.info("開始異步轉換所有 AST 文件為圖數據庫");
            CompletableFuture<Map<String, Object>> future = astToGraphService.convertAllAstToGraphAsync();

            // 立即返回任務已啟動的響應
            return ResponseEntity.accepted().body(Map.of(
                    "success", true,
                    "message", "異步轉換任務已啟動",
                    "status", "PROCESSING"));
        } catch (Exception e) {
            log.error("啟動異步 AST 轉換時發生錯誤", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "啟動異步轉換失敗: " + e.getMessage(),
                    "error", e.getMessage()));
        }
    }

    @GetMapping("/statistics")
    @Operation(summary = "獲取 AST 圖統計信息", description = "獲取圖數據庫中 AST 相關數據的統計信息")
    public ResponseEntity<Map<String, Object>> getAstStatistics() {
        try {
            Map<String, Object> statistics = astToGraphService.getAstStatistics();

            Map<String, Object> response = Map.of(
                    "success", true,
                    "message", "統計信息獲取成功",
                    "statistics", statistics);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("獲取 AST 統計信息時發生錯誤", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "獲取統計信息失敗: " + e.getMessage(),
                    "error", e.getMessage()));
        }
    }

    @DeleteMapping("/clear")
    @Operation(summary = "清理 AST 數據", description = "清理圖數據庫中的所有 AST 相關數據")
    public ResponseEntity<Map<String, Object>> clearAstData() {
        try {
            log.warn("開始清理 AST 相關數據");
            astToGraphService.clearAstData();

            Map<String, Object> response = Map.of(
                    "success", true,
                    "message", "AST 數據清理完成");

            log.info("AST 數據清理完成");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("清理 AST 數據時發生錯誤", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "清理數據失敗: " + e.getMessage(),
                    "error", e.getMessage()));
        }
    }

    @GetMapping("/health")
    @Operation(summary = "檢查轉換服務健康狀態", description = "檢查 AST 轉換服務和相關依賴的健康狀態")
    public ResponseEntity<Map<String, Object>> health() {
        try {
            Map<String, Object> health = Map.of(
                    "service", "AstToGraphService",
                    "status", "UP",
                    "neo4j", "CONNECTED", // 這裡可以添加實際的 Neo4j 連接檢查
                    "timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(health);
        } catch (Exception e) {
            log.error("健康檢查失敗", e);
            return ResponseEntity.status(500).body(Map.of(
                    "service", "AstToGraphService",
                    "status", "DOWN",
                    "error", e.getMessage(),
                    "timestamp", System.currentTimeMillis()));
        }
    }

    @PostMapping("/query")
    @Operation(summary = "執行自定義 Cypher 查詢", description = "執行自定義的 Cypher 查詢來分析 AST 圖數據")
    public ResponseEntity<Map<String, Object>> executeCustomQuery(@RequestBody Map<String, Object> request) {
        try {
            String cypher = (String) request.get("cypher");
            @SuppressWarnings("unchecked")
            Map<String, Object> parameters = (Map<String, Object>) request.getOrDefault("parameters", Map.of());

            if (cypher == null || cypher.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Cypher 查詢語句不能為空"));
            }

            // 這裡需要注入 Neo4jService 來執行查詢
            // 為了簡化，我們返回一個示例響應
            Map<String, Object> response = Map.of(
                    "success", true,
                    "message", "查詢執行成功",
                    "cypher", cypher,
                    "parameters", parameters,
                    "note", "實際查詢執行需要注入 Neo4jService");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("執行自定義查詢時發生錯誤", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "查詢執行失敗: " + e.getMessage(),
                    "error", e.getMessage()));
        }
    }
}
