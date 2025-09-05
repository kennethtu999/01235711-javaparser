#!/bin/bash

# 測試非同步API的腳本
# 需要先啟動Spring Boot應用程式

echo "=== 測試AST解析器非同步API ==="

# 設定API基礎URL
API_BASE="http://localhost:8080/api/ast"

# 測試健康檢查
echo "1. 測試健康檢查..."
curl -s "$API_BASE/health"
echo -e "\n"

# 測試解析請求
echo "2. 發送解析請求..."
PROJECT_PATH="/Users/kenneth/git/01235711-javaparser/test-project/src/main/java"

# 創建解析請求JSON
cat > parse_request.json << EOF
{
    "projectPath": "$PROJECT_PATH",
    "entryPointMethodFqn": "com.example.test.Main.main",
    "outputType": "MERMAID",
    "params": {
        "basePackage": "com.example",
        "depth": 3
    }
}
EOF

# 發送解析請求
echo "發送解析請求到 $API_BASE/parse"
RESPONSE=$(curl -s -X POST "$API_BASE/parse" \
    -H "Content-Type: application/json" \
    -d @parse_request.json)

echo "解析響應: $RESPONSE"

# 提取任務ID
TASK_ID=$(echo "$RESPONSE" | grep -o '"taskId":"[^"]*"' | cut -d'"' -f4)
echo "任務ID: $TASK_ID"

if [ -n "$TASK_ID" ]; then
    echo -e "\n3. 輪詢任務狀態..."
    
    # 輪詢任務狀態
    for i in {1..10}; do
        echo "第 $i 次查詢任務狀態..."
        STATUS_RESPONSE=$(curl -s "$API_BASE/parse/status/$TASK_ID")
        echo "狀態響應: $STATUS_RESPONSE"
        
        # 檢查是否完成
        if echo "$STATUS_RESPONSE" | grep -q '"status":"COMPLETED"'; then
            echo "任務完成！"
            break
        elif echo "$STATUS_RESPONSE" | grep -q '"status":"FAILED"'; then
            echo "任務失敗！"
            break
        fi
        
        echo "等待5秒後再次查詢..."
        sleep 5
    done
else
    echo "無法獲取任務ID，請檢查應用程式是否正常運行"
fi

# 清理臨時文件
rm -f parse_request.json

echo -e "\n=== 測試完成 ==="
