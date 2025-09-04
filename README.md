# Java AST Parser with Spring Boot 3

## Use Case

### case01: 解析各式Java專案

1. 能解析各式Java專案

### case02: 能經由Java Method 生成對應的 Sequence Diagram

### case03: 能經由 Java Method 取出對應的程式碼 ✅ **已完成**

1. 所有引用的Method都要有 ✅
2. 以Class為單位 ✅
3. 為確保各Method的解析正常，Class屬性也要取出 ✅
4. 整併成一個檔案供AI Prompt使用 ✅

## 核心實作

- `CodeExtractorService`：核心代碼提取服務
- REST API 端點：`POST /api/ast/extract-code`
- 依賴追蹤：基於 SequenceTracer 實現
- 智能過濾：支援包名過濾和深度控制
- 格式化輸出：適合 AI Prompt 使用的合併代碼

**使用範例：** 詳見 [CASE3_USAGE_EXAMPLE.md](./CASE3_USAGE_EXAMPLE.md) 

## API 服務

### 主要端點

#### 1. 健康檢查
- **端點**: `GET /api/ast/health`
- **功能**: 檢查服務運行狀態
- **回應**: `"AST Parser Service is running"`

#### 2. 基本 AST 解析
- **端點**: `POST /api/ast/parse`
- **功能**: 解析單個源碼目錄
- **請求體**:
```json
{
  "sourceRoot": "/path/to/source",
  "outputDir": "/path/to/output"
}
```

#### 3. 高級 AST 解析
- **端點**: `POST /api/ast/parse/advanced`
- **功能**: 執行高級 AST 解析，支援多源碼目錄和自定義類路徑
- **請求體**:
```json
{
  "baseFolder": "/path/to/base",
  "sourceRootDirs": ["/path/to/src1", "/path/to/src2"],
  "outputDir": "/path/to/output",
  "classpath": "/path/to/lib1.jar,/path/to/lib2.jar",
  "javaComplianceLevel": "17"
}
```

#### 4. 代碼提取 (Case 3)
- **端點**: `POST /api/ast/extract-code`
- **功能**: 從 Java Method 提取相關程式碼，供 AI Prompt 使用
- **請求體**:
```json
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

## 安裝與設定

### 系統需求
- Java 17 或更高版本
- Gradle 7.0 或更高版本

### 建置專案
```bash
# 克隆專案
git clone <repository-url>
cd 01235711-javaparser

# 建置專案
./gradlew build

# 執行測試
./gradlew test
```

### 啟動服務

#### 方式一：Spring Boot 應用
```bash
# 使用 Gradle
./gradlew bootRun

# 或使用腳本
cd ast-parser
./run-springboot.sh  # Linux/Mac
# 或
run-springboot.bat   # Windows
```

#### 方式二：命令列工具
```bash
# 基本用法
java -jar ast-parser/build/libs/ast-parser-1.0-SNAPSHOT.jar \
  /path/to/project /path/to/output

# 高級用法
java -jar ast-parser/build/libs/ast-parser-1.0-SNAPSHOT.jar \
  /path/to/base /path/to/src1,/path/to/src2 /path/to/output \
  /path/to/lib1.jar,/path/to/lib2.jar 17
```

## 使用範例

### Case 1: 解析 Java 專案
```bash
# 透過 REST API
curl -X POST http://localhost:8080/api/ast/parse \
  -H "Content-Type: application/json" \
  -d '{
    "sourceRoot": "/path/to/your/java/project/src/main/java",
    "outputDir": "/path/to/output"
  }'
```

### Case 2: 生成 Sequence Diagram
```bash
# 解析後會自動生成 Mermaid 格式的序列圖
# 輸出檔案：build/diagram.mermaid
```

### Case 3: 代碼提取
```bash
# 提取特定方法的相關代碼
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

## 技術架構

### 核心依賴
- **Spring Boot 3.5.5**: Web 框架
- **Eclipse JDT Core 3.42.0**: Java AST 解析
- **Apache Commons Lang 3.15.0**: 字串處理工具
- **Jackson**: JSON 序列化
- **SLF4J + Logback**: 日誌框架

### 主要服務
- `AstParserService`: AST 解析核心服務
- `CodeExtractorService`: 代碼提取服務
- `AstExtractor`: AST 提取器
- `EnhancedInteractionModelVisitor`: 互動模型訪問者

### 支援的專案類型
- Gradle 專案
- Maven 專案  
- Eclipse 專案

## 開發與測試

### 執行測試
```bash
# 執行所有測試
./gradlew test

# 執行特定測試類別
./gradlew test --tests "kai.javaparser.case3.CodeExtractorServiceTest"

# 生成測試報告
./gradlew test jacocoTestReport
```

### 專案結構
```
ast-parser/
├── src/main/java/kai/javaparser/
│   ├── controller/          # REST 控制器
│   ├── service/            # 業務邏輯服務
│   ├── model/              # 資料模型
│   ├── handler/            # 處理器
│   └── util/               # 工具類別
├── src/test/java/          # 測試程式碼
└── src/main/resources/     # 配置檔案
```

### 日誌配置
- 開發環境：DEBUG 級別
- 生產環境：INFO 級別
- 日誌檔案：`logs/ast-parser.log`

## 注意事項

1. **Java 版本**: 必須使用 Java 17 或更高版本
2. **記憶體需求**: 大型專案建議配置至少 2GB 堆記憶體
3. **類路徑**: 確保所有依賴的 JAR 檔案都在類路徑中
4. **權限**: 確保對輸出目錄有寫入權限

## 授權

本專案採用 MIT 授權條款。