# Java AST 解析器

本專案旨在解析 Java 原始碼檔案的抽象語法樹 (AST)，並將其結構化資訊儲存為本地 JSON 檔案，以供後續的語言模型 (LLM) 或其他分析工具使用。

## 功能

* 解析 Java 8 及 Java 17 版本的原始碼。
* 支援大型多模組 (Gradle/Maven) 專案。
* 利用 Eclipse JDT Core 進行精確的 AST 解析和類型綁定 (Type Binding Resolution)，能夠理解類、方法、字段的完全限定名 (FQN) 及彼此間的關係。
* 將解析結果輸出為易於機器讀取的 JSON 格式。
* 解決不同子專案中可能存在的同套件名、同檔案名衝突問題。

## 專案結構

本專案採用 Gradle 多專案設定：

* `ast-parser/`: 核心解析器應用程式，負責執行 AST 解析並輸出 JSON。
* `test-project/`: 作為解析目標的範例 Java 專案，用於測試解析器的功能。

## 如何建置 (Build)

在專案的根目錄下執行 Gradle 命令：

```bash
./gradlew build
```

這會建置 `ast-parser` 的可執行 JAR 檔 (位於 `ast-parser/build/libs/`) 以及 `test-project`。

## 如何使用

### 執行解析器

解析器是一個命令列工具。您需要提供待解析的 Java 原始碼根目錄、輸出目錄，以及一個包含所有編譯依賴的 classpath 列表。

```bash
java -jar ast-parser/build/libs/ast-parser-1.0-SNAPSHOT.jar \
  <source_root_dir1,source_root_dir2,...> \
  <output_dir> \
  <classpath_item1,classpath_item2,...> \
  [java_compliance_level]
```

**參數說明：**

* `<source_root_dir1,...>` (必填): 逗號分隔的所有 Java 原始碼根目錄路徑（例如：`projectA/src/main/java,projectB/src/main/java`）。
* `<output_dir>` (必填): 儲存 AST JSON 檔案的目標目錄。
* `<classpath_item1,...>` (必填): 逗號分隔的 classpath 列表。**這是 JDT 進行類型解析的關鍵。** 它應包含所有專案依賴的 JAR 檔，以及多模組專案中其他模組已編譯的 `.class` 檔案目錄（例如：`moduleA/build/classes/java/main`）。
* `[java_compliance_level]` (選填): 指定 Java 語法版本（例如：`8`, `17`）。預設為 `17`。

**範例：**

```bash
# 假設您的主應用程式源碼在 /path/to/my-app/module-core/src/main/java 和 /path/to/my-app/module-web/src/main/java
# 並且所有編譯後的 class 和依賴 JARs 在 /path/to/my-app/build/classes 和 /path/to/my-app/libs
java -jar ast-parser/build/libs/ast-parser-1.0-SNAPSHOT.jar \
  /path/to/my-app/module-core/src/main/java,/path/to/my-app/module-web/src/main/java \
  ./parsed_asts \
  "/path/to/my-app/module-core/build/classes/java/main,/path/to/my-app/module-web/build/classes/java/main,/path/to/my-app/libs/spring-boot.jar" \
  17
```

**重要提示：** `classpath_item` 列表對於精確的類型綁定解析至關重要。建議使用 Gradle Tooling API 或 Maven Model Builder 等工具來自動獲取大型專案的完整 classpath。

### 執行測試

本專案包含一個整合測試，它使用 Gradle Tooling API 來動態建置 `test-project` 並獲取其 classpath，然後運行解析器。

在專案的根目錄下執行：

```bash
./gradlew :ast-parser:test
```