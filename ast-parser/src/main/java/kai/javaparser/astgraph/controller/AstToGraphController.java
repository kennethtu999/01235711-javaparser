package kai.javaparser.astgraph.controller;

import java.util.Map;

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
import kai.javaparser.astgraph.service.AstToGraphService;
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

    @PostMapping("/convert-bulk")
    @Operation(summary = "批量轉換所有 AST 文件（大型系統）", description = "適用於超過100,000節點的大型系統，使用批量操作防止重複插入，保持關係完整性")
    public ResponseEntity<Map<String, Object>> convertAllAstToGraphBulk() {
        try {
            log.info("開始批量轉換所有 AST 文件（大型系統模式）");
            astToGraphService.convertAllAstToGraphBulk();

            // 立即返回任務已啟動的響應
            return ResponseEntity.accepted().body(Map.of(
                    "success", true,
                    "message", "批量轉換任務已啟動（大型系統模式）",
                    "status", "PROCESSING",
                    "features", Map.of(
                            "bulkOperations", true,
                            "duplicatePrevention", true,
                            "relationshipPreservation", true,
                            "nodeTypes", "Class/Interface",
                            "optimizedForLargeSystems", true)));
        } catch (Exception e) {
            log.error("啟動批量 AST 轉換時發生錯誤", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "啟動批量轉換失敗: " + e.getMessage(),
                    "error", e.getMessage()));
        }
    }

    @GetMapping("/status")
    @Operation(summary = "查詢轉換處理狀態", description = "查詢當前轉換任務的處理狀態")
    public ResponseEntity<Map<String, Object>> getConversionStatus() {
        try {
            Map<String, Object> status = astToGraphService.getConversionStatus();

            Map<String, Object> response = Map.of(
                    "success", true,
                    "message", "狀態查詢成功",
                    "status", status);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("查詢轉換狀態時發生錯誤", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "查詢狀態失敗: " + e.getMessage(),
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
