
### **重構指導原則**

*   **完成階段任務，不做額外的範圍外功能異動**
*   **小步快跑，持續整合**：每個階段都應該是一個相對較小的、可獨立完成的工作單元。完成後應立即進行測試和整合。
*   **向下相容**：在每個階段結束時，應用程式對外的功能和 API 應保持不變或向下相容，確保不影響現有使用者。
*   **測試驅動**：在進行重構前，先確保有足夠的整合測試覆蓋現有功能。每完成一個階段的重構，都應執行這些測試，並補充新的單元測試。

---

### **階段一：奠定基礎 — 儲存抽象與解析服務化**

**目標**：將 AST 解析的核心邏輯與資料儲存徹底解耦，為後續所有模組建立一個穩定、抽象的資料來源。這是整個重構中最關鍵的一步。

**具體任務**：
1.  **定義儲存契約**：
    *   建立 `AstRepository` 介面，定義 `save(FileAstData)` 和 `findByFqn(String)` 等核心方法。
2.  **封裝現有邏輯**：
    *   建立 `FileSystemAstRepository` 類別，實現 `AstRepository` 介面。將目前所有讀寫 `.json` 檔案和處理快取 (`ast-index.cache`) 的邏輯從 `AstIndex` 和 `AstParserApp`/`AstParserService` 中遷移至此類別。
3.  **重構 AST 索引**：
    *   改造 `AstIndex` 類別。移除其檔案系統掃描的邏輯，改為在建構函式中注入 `AstRepository`，並透過它來載入所有 AST 資料並建立索引。
4.  **重構解析服務**：
    *   改造 `AstParsingService`。移除其直接寫入檔案的邏輯，改為注入並呼叫 `astRepository.save()` 方法。
5.  **組態 Spring IoC 容器**：
    *   確保 `AstParsingService`, `AstIndex`, `FileSystemAstRepository` 都被註冊為 Spring Bean，並正確設定依賴注入關係。

**階段性成果**：
*   應用程式的**外部行為完全沒有改變**，但內部架構已發生質變。
*   AST 的產生和消費之間有了一層清晰的儲存抽象，不再直接依賴檔案系統。
*   `AstIndex` 和 `AstParsingService` 的職責更單一，且更容易進行單元測試（只需 Mock `AstRepository`）。

---

### **階段二：核心業務抽象 — 序列追蹤服務化**

**目標**：將方法呼叫的追蹤邏輯抽象成一個獨立、可重用的 `SequenceTraceService`。

**具體任務**：
1.  **建立服務門面 (Facade)**：
    *   建立 `SequenceTraceService` 類別。
    *   將 `SequenceTracer` 的核心追蹤邏輯遷移到 `SequenceTraceService` 中。
2.  **注入依賴**：
    *   `SequenceTraceService` 應依賴於**階段一**完成的 `AstIndex`。
3.  **定義資料傳輸物件 (DTO)**：
    *   正式將 `TraceResult` 定義為此服務的標準輸出，確保它是一個與具體格式無關的純資料結構。
4.  **改造上層呼叫者**：
    *   重構 `SequenceOutputGenerator` 和 `CodeExtractorService`，讓它們不再直接建立 `SequenceTracer` 實例，而是注入 `SequenceTraceService` 並呼叫其方法來獲取 `TraceResult`。

**階段性成果**：
*   擁有了一個獨立、可測試、可重用的核心業務服務 (`SequenceTraceService`)。
*   圖表產生和程式碼提取的邏輯與底層的追蹤邏輯進一步解耦。

---

### **階段三：輸出端精煉 — 渲染器與程式碼編織器**

**目標**：將最終產出（圖表和程式碼）的生成邏輯進行精細化拆分，提升其靈活性和可維護性。

**具體任務**：
1.  **圖表產生器重構**：
    *   建立 `DiagramRenderer` 介面和 `MermaidRenderer` 實現。
    *   建立 `DiagramService`，注入 `MermaidRenderer`，並提供一個統一的 `generateDiagram(TraceResult)` 方法。
    *   `AstParserController` 中的相關端點應注入並呼叫 `DiagramService`。
2.  **程式碼提取器重構**：
    *   建立 `SourceProvider` 介面和 `FileSystemSourceProvider` 實現，用於抽象原始碼的來源。
    *   建立 `SourceCodeWeaver` 介面，用於抽象程式碼的過濾與重組邏輯。
    *   建立 `JdtBasedSourceCodeWeaver` 實現，將 `CodeExtractorService` 中基於行號的複雜程式碼篩選邏輯遷移至此。
    *   重構 `CodeExtractorService`，使其職責簡化為：
        a. 呼叫 `SequenceTraceService` 獲取 `TraceResult`。
        b. 根據 `TraceResult` 決定 `WeavingRules`。
        c. 透過 `SourceProvider` 獲取原始碼。
        d. 呼叫 `SourceCodeWeaver` 處理原始碼。
        e. 合併最終結果。

**階段性成果**：
*   整個後端處理流程被拆分為一條清晰的、由多個微小服務組成的管道：`Parsing -> Indexing -> Tracing -> Rendering/Weaving`。
*   每個元件都遵循單一職責原則，易於理解、修改和測試。
*   新增圖表格式 (如 PlantUML) 或新的程式碼過濾規則變得非常簡單。

---

### **階段四：使用者體驗優化 — API 非同步化**

**目標**：解決長時間執行的任務阻塞 HTTP 請求的問題，提升 Web API 的響應能力和穩定性。

**具體任務**：
1.  **啟用非同步支援**：
    *   在主應用程式類別上添加 `@EnableAsync` 註解。
2.  **改造核心服務**：
    *   在 `AstParsingService` 的主解析方法上添加 `@Async` 註解，並將其返回類型改為 `CompletableFuture<ParseResult>`。
3.  **改造 API Controller**：
    *   建立一個簡單的任務管理服務（初期可使用 `ConcurrentHashMap`），用於儲存非同步任務的狀態和結果。
    *   修改 `/api/ast/parse` 端點：
        a. 呼叫 `@Async` 服務方法。
        b. 產生一個唯一的任務 ID。
        c. 立即返回 **HTTP 202 (Accepted)** 以及該任務 ID。
    *   新增一個 `/api/ast/parse/status/{taskId}` 端點，用於客戶端輪詢任務進度（等待中、處理中、已完成、失敗）和最終結果。

**階段性成果**：
*   一個健壯、非阻塞的 Web API，能夠處理大型專案的解析請求而不會超時。
*   應用程式架構更符合現代雲原生應用的設計模式。
*   使用者體驗得到極大提升。

---

### **計畫總結**

| 階段 | 核心目標 | 涉及模組 | 主要產出 | 風險等級 |
| :--- | :--- | :--- | :--- | :--- |
| **一** | **儲存抽象** | Ast Parser | `AstRepository` 介面及實現 | **低** (純內部重構) |
| **二** | **追蹤服務化** | Sequence Builder | `SequenceTraceService` | **低** (核心邏輯遷移) |
| **三** | **輸出端精煉** | Diagram Builder, Code Extract Builder | `DiagramService`, `SourceCodeWeaver` | **中** (邏輯拆分較多) |
| **四** | **API 非同步化** | Controller, Service | 非阻塞的 Web API | **中** (引入非同步複雜性) |

建議嚴格按照此順序執行，因為後續階段都依賴於前一階段的抽象基礎。每完成一個階段，您都會得到一個更健壯、更靈活的系統。