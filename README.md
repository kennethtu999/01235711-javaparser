# Java AST Parser with Spring Boot 3

這是一個基於 Spring Boot 3 的 Java AST 解析器專案，用於分析和解析 Java 代碼的抽象語法樹。

## 系統要求

- **Java**: 17 或更高版本 (Spring Boot 3 要求)
- **Gradle**: 8.0 或更高版本
- **作業系統**: Windows, macOS, Linux

## 專案結構

```
├── ast-parser/           # 主要的 Spring Boot 應用
│   ├── src/main/java/   # Java 源碼
│   ├── src/main/resources/ # 配置文件和資源
│   └── src/test/java/   # 測試代碼
├── test-project/         # 測試用的 Java 專案
└── gradle/              # Gradle 配置
```

## 快速開始

### 1. 檢查 Java 版本

```bash
java -version
```

確保版本為 17 或更高。

### 2. 構建專案

```bash
# 在根目錄執行
./gradlew build
```

### 3. 運行 Spring Boot 應用

#### Linux/macOS:
```bash
cd ast-parser
./run-springboot.sh
```

#### Windows:
```cmd
cd ast-parser
run-springboot.bat
```

#### 或使用 Gradle:
```bash
cd ast-parser
./gradlew bootRun
```

### 4. 訪問應用

應用將在 `http://localhost:8080/api` 啟動

## 主要功能

- **AST 解析**: 使用 Eclipse JDT Core 解析 Java 代碼
- **Web API**: 提供 RESTful API 接口
- **Mermaid 圖表**: 生成序列圖和類圖
- **控制流分析**: 分析方法的控制流程

## 技術棧

- **Spring Boot**: 3.5.5
- **Java**: 17
- **Gradle**: 8.14.3
- **Eclipse JDT Core**: 3.42.0
- **Jackson**: JSON 序列化
- **Logback**: 日誌框架
- **JUnit 5**: 測試框架

## 配置

### 應用配置 (`application.properties`)

- 服務端口: 8080
- API 路徑: `/api`
- 日誌級別: DEBUG (kai.javaparser 包)

### Gradle 配置

- 啟用並行構建
- 啟用構建緩存
- 優化記憶體使用

## 開發

### 添加依賴

在 `ast-parser/build.gradle` 中添加新的依賴：

```gradle
dependencies {
    implementation 'your.dependency:artifact:version'
}
```

### 運行測試

```bash
./gradlew test
```

### 清理構建

```bash
./gradlew clean
```

## 故障排除

### 常見問題

1. **Java 版本錯誤**
   - 確保使用 Java 17 或更高版本
   - 檢查 `JAVA_HOME` 環境變數

2. **端口衝突**
   - 修改 `application.properties` 中的 `server.port`

3. **記憶體不足**
   - 調整 `gradle.properties` 中的記憶體設定

### 日誌

日誌文件位於 `ast-parser/logs/` 目錄，使用 Logback 配置。

## 授權

此專案僅供學習和研究使用。