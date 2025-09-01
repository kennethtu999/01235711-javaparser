#!/bin/bash

# AST Parser Spring Boot 啟動腳本

echo "正在啟動 AST Parser Spring Boot 服務..."

# 檢查Java是否安裝
if ! command -v java &> /dev/null; then
    echo "錯誤: 未找到Java，請確保已安裝Java 17或更高版本"
    exit 1
fi

# 檢查Java版本
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "警告: 建議使用Java 17或更高版本，當前版本: $JAVA_VERSION"
fi

# 檢查jar文件是否存在
JAR_FILE="build/libs/ast-parser-1.0-SNAPSHOT.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo "錯誤: 未找到jar文件: $JAR_FILE"
    echo "請先運行: ./gradlew build -x test"
    exit 1
fi

echo "使用jar文件: $JAR_FILE"
echo "服務將在 http://localhost:8080 啟動"
echo "按 Ctrl+C 停止服務"
echo ""

# 啟動Spring Boot應用
java -jar "$JAR_FILE"

