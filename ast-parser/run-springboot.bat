@echo off
REM AST Parser Spring Boot 啟動腳本 (Windows)

echo 正在啟動 AST Parser Spring Boot 服務...

REM 檢查Java是否安裝
java -version >nul 2>&1
if errorlevel 1 (
    echo 錯誤: 未找到Java，請確保已安裝Java 17或更高版本
    pause
    exit /b 1
)

REM 檢查jar文件是否存在
set JAR_FILE=build\libs\ast-parser-1.0-SNAPSHOT.jar
if not exist "%JAR_FILE%" (
    echo 錯誤: 未找到jar文件: %JAR_FILE%
    echo 請先運行: gradlew.bat build -x test
    pause
    exit /b 1
)

echo 使用jar文件: %JAR_FILE%
echo 服務將在 http://localhost:8080 啟動
echo 按 Ctrl+C 停止服務
echo.

REM 啟動Spring Boot應用
java -jar "%JAR_FILE%"

pause

