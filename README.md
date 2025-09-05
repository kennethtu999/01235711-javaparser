# Java AST Parser with Spring Boot 3

一個基於 Spring Boot 3 和 Eclipse JDT Core 的 Java AST 解析器，提供 REST API 服務，支援多種 Java 專案解析、序列圖生成和代碼提取功能。

## 📋 目錄

- [功能特色](#功能特色)
- [快速開始](#快速開始)
- [API 文檔](#api-文檔)
- [使用案例](#使用案例)
- [技術架構](#技術架構)
- [專案結構](#專案結構)
- [開發指南](#開發指南)
- [常見問題](#常見問題)

## 🚀 功能特色

### ✅ 已實現功能

#### Case 1: Java 專案解析

- 支援多種 Java 專案類型（Gradle、Maven、Eclipse）
- 基於 Eclipse JDT Core 的高精度 AST 解析
- 支援自定義類路徑和 Java 合規性等級
- 多源碼目錄並行解析

#### Case 2: 序列圖生成

- 自動生成 Mermaid 格式的序列圖
- 支援複雜的控制流程（條件、迴圈、異常處理）
- 可視化方法調用關係和執行流程

#### Case 3: 智能代碼提取 ✅ **核心功能**

- 基於方法入口點的依賴追蹤
- 提取所有相關的方法和類別
- 支援包名過濾和深度控制
- 格式化輸出適合 AI Prompt 使用
- 包含類別屬性和完整的方法實現

### 🔄 進行中功能

- 更精確的依賴分析算法
- 支援更多 Java 語言特性
- 性能優化和記憶體管理

## 🚀 快速開始

### 系統需求

- **Java**: 17 或更高版本
- **Gradle**: 7.0 或更高版本
- **記憶體**: 建議至少 2GB 堆記憶體

### 安裝與建置

```bash
# 1. 克隆專案
git clone <repository-url>
cd 01235711-javaparser

# 2. 建置專案
./gradlew build

# 3. 執行測試
./gradlew test
```

### 啟動服務

#### 方式一：Spring Boot 應用（推薦）

```bash
# 使用 Gradle 啟動
./gradlew bootRun

# 或使用腳本
cd ast-parser
./run-springboot.sh  # Linux/Mac
# 或
run-springboot.bat   # Windows
```

#### 方式二：JAR 檔案執行

```bash
# 建置 JAR 檔案
./gradlew jar

# 執行 JAR 檔案
java -jar ast-parser/build/libs/ast-parser-1.0-SNAPSHOT.jar
```

服務啟動後，可透過 `http://localhost:8080` 存取 API。

## 📚 API 文檔

### 基礎端點

#### 健康檢查

```http
GET /api/ast/health
```

**回應**: `"AST Parser Service is running"`

### 核心功能端點

#### 1. 基本 AST 解析

```http
POST /api/ast/parse
Content-Type: application/json

{
  "sourceRoot": "/path/to/source",
  "outputDir": "/path/to/output"
}
```

#### 2. 高級 AST 解析

```http
POST /api/ast/parse/advanced
Content-Type: application/json

{
  "baseFolder": "/path/to/base",
  "sourceRootDirs": ["/path/to/src1", "/path/to/src2"],
  "outputDir": "/path/to/output",
  "classpath": "/path/to/lib1.jar,/path/to/lib2.jar",
  "javaComplianceLevel": "17"
}
```

#### 3. 代碼提取（Case 3 核心功能）

```http
POST /api/ast/extract-code
Content-Type: application/json

{
  "entryPointMethodFqn": "com.example.MyClass.myMethod",
  "astDir": "/path/to/ast/output",
  "basePackage": "com.example",
  "maxDepth": 3,
  "includeImports": true,
  "includeComments": true,
  "extractOnlyUsedMethods": true
}
```

**回應範例**:

```json
{
  "success": true,
  "extractedCode": "// 提取的完整代碼...",
  "methodCount": 15,
  "classCount": 8,
  "processingTime": "2.5s"
}
```

## 💡 使用案例

### Case 1: 解析 Java 專案

```bash
# 透過 REST API 解析專案
curl -X POST http://localhost:8080/api/ast/parse \
  -H "Content-Type: application/json" \
  -d '{
    "sourceRoot": "/path/to/your/java/project/src/main/java",
    "outputDir": "/path/to/output"
  }'
```

### Case 2: 生成序列圖

解析完成後，系統會自動在 `build/diagram.mermaid` 生成序列圖檔案。

### Case 3: 代碼提取（AI 輔助開發）

```bash
# 提取特定方法的所有相關代碼
curl -X POST http://localhost:8080/api/ast/extract-code \
  -H "Content-Type: application/json" \
  -d '{
    "entryPointMethodFqn": "com.example.LoginUser.login",
    "astDir": "/path/to/ast/output",
    "basePackage": "com.example",
    "maxDepth": 2,
    "includeImports": true,
    "includeComments": true,
    "extractOnlyUsedMethods": true
  }'
```

**使用場景**:

- AI 代碼分析和重構
- 代碼審查和文檔生成
- 複雜業務邏輯理解
- 遺留系統代碼分析

## 🏗️ 技術架構

### 核心依賴

| 依賴 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.5.5 | Web 框架和依賴注入 |
| Eclipse JDT Core | 3.42.0 | Java AST 解析引擎 |
| Apache Commons Lang | 3.15.0 | 字串處理工具 |
| Jackson | 3.x | JSON 序列化 |
| SLF4J + Logback | 3.x | 日誌框架 |
| Lombok | 1.18.36 | 代碼生成 |

### 主要服務層

```
┌─────────────────────────────────────────┐
│              Controller Layer            │
│         (AstParserController)           │
└─────────────────┬───────────────────────┘
                  │
┌─────────────────▼───────────────────────┐
│              Service Layer               │
│  ┌─────────────────┬─────────────────┐  │
│  │ AstParserService│CodeExtractorSvc │  │
│  │                 │                 │  │
│  │ SequenceTraceSvc│TaskManagementSvc│  │
│  └─────────────────┴─────────────────┘  │
└─────────────────┬───────────────────────┘
                  │
┌─────────────────▼───────────────────────┐
│            Repository Layer             │
│        (FileSystemAstRepository)       │
└─────────────────┬───────────────────────┘
                  │
┌─────────────────▼───────────────────────┐
│            Java2AST Layer               │
│  ┌─────────────────┬─────────────────┐  │
│  │ JavaToAstFile   │EnhancedVisitor  │  │
│  │                 │                 │  │
│  │ ControlFlowHdlr │InvocationHdlr   │  │
│  └─────────────────┴─────────────────┘  │
└─────────────────────────────────────────┘
```

### 支援的專案類型

- ✅ **Gradle 專案** - 完整支援
- ✅ **Maven 專案** - 完整支援  
- ✅ **Eclipse 專案** - 完整支援
- 🔄 **IntelliJ IDEA 專案** - 部分支援

## 📁 專案結構

```
01235711-javaparser/
├── ast-parser/                          # 主要 Spring Boot 應用
│   ├── src/main/java/kai/javaparser/
│   │   ├── controller/                  # REST API 控制器
│   │   │   └── AstParserController.java
│   │   ├── service/                     # 業務邏輯服務
│   │   │   ├── AstParserService.java
│   │   │   ├── CodeExtractorService.java
│   │   │   ├── SequenceTraceService.java
│   │   │   └── TaskManagementService.java
│   │   ├── model/                       # 資料模型
│   │   │   ├── ProcessRequest.java
│   │   │   ├── TraceResult.java
│   │   │   └── ...
│   │   ├── java2ast/                    # Java 到 AST 轉換
│   │   │   ├── JavaToAstFile.java
│   │   │   ├── EnhancedInteractionModelVisitor.java
│   │   │   └── handler/
│   │   ├── diagram/                     # 序列圖生成
│   │   │   ├── DiagramService.java
│   │   │   ├── MermaidRenderer.java
│   │   │   └── output/
│   │   ├── repository/                  # 資料存取層
│   │   │   ├── AstRepository.java
│   │   │   └── FileSystemAstRepository.java
│   │   └── util/                        # 工具類別
│   ├── src/test/java/                   # 測試程式碼
│   ├── src/main/resources/
│   │   ├── application.yml              # 應用配置
│   │   └── logback.xml                  # 日誌配置
│   └── build.gradle                     # 建置配置
├── test-project/                        # 測試專案
│   └── src/main/java/com/example/       # 範例 Java 代碼
├── build.gradle                         # 根專案建置配置
└── settings.gradle                      # Gradle 設定
```

## 🛠️ 開發指南

### 執行測試

```bash
# 執行所有測試
./gradlew test

# 執行特定測試類別
./gradlew test --tests "kai.javaparser.CodeExtractorServiceTest"

# 生成測試報告
./gradlew test jacocoTestReport
```

### 開發環境設定

1. **IDE 設定**:
   - 推薦使用 IntelliJ IDEA 或 Eclipse
   - 確保 Java 17 和 Gradle 7+ 已安裝
   - 啟用 Lombok 支援

2. **日誌配置**:
   - 開發環境：DEBUG 級別
   - 生產環境：INFO 級別
   - 日誌檔案：`logs/ast-parser.log`

3. **記憶體配置**:

   ```bash
   # 大型專案建議配置
   export GRADLE_OPTS="-Xmx4g -XX:MaxMetaspaceSize=1g"
   ```

### 程式碼風格

- 遵循 Java 標準命名慣例
- 使用 Lombok 減少樣板代碼
- 完整的 JavaDoc 註解
- 單元測試覆蓋率 > 80%

## ❓ 常見問題

### Q: 如何處理大型專案的記憶體問題？

A: 可以透過以下方式優化：

```bash
# 增加 JVM 記憶體
export GRADLE_OPTS="-Xmx4g -XX:MaxMetaspaceSize=1g"

# 或在 application.yml 中配置
app:
  maxMemoryUsage: 4g
  batchSize: 100
```

### Q: 支援哪些 Java 版本？

A: 目前支援 Java 8-21。

### Q: 如何自定義序列圖樣式？

A: 可以透過修改 `MermaidRenderer` 類別來自定義輸出格式。

### Q: 代碼提取的深度限制？

A: 預設最大深度為 10，可透過 `maxDepth` 參數調整。建議設定為 3-5 以平衡性能和完整性。

---

**最後更新**: 2025年09月
**版本**: 0.0.1
**維護者**: Kenneth
