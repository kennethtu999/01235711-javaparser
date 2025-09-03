package kai.javaparser.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 代表方法呼叫的互動模型，用於生成循序圖
 * 
 * 重新設計的資料結構：
 * - nextChainedCall: 用於表示鏈式呼叫的下一個環節
 * - internalCalls: 用於表示被呼叫方法內部的所有活動
 * - 移除模糊的 children 欄位
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class InteractionModel implements DiagramNode {
    private String sequenceId;
    private String caller; // 呼叫者類型
    private String callee; // 被呼叫者類型
    private String callerVariable; // 呼叫者變數名稱 (e.g., "dataList")
    private String calleeVariable; // 被呼叫者變數名稱 (e.g., "input")
    private String callerInstanceId; // 呼叫者實例ID (e.g., "dataList1", "dataList2")
    private String calleeInstanceId; // 被呼叫者實例ID
    private String methodName; // 方法名稱
    private List<String> arguments; // 參數列表
    private String returnValue; // 回傳值類型
    private int lineNumber; // 行號
    private String assignedToVariable; // 被賦值的變數名稱

    // 重新設計的欄位，語義明確
    private InteractionModel nextChainedCall; // 鏈式呼叫的下一個環節
    private List<DiagramNode> internalCalls; // 被呼叫方法內部的所有活動

    // 向後兼容的 getter，用於現有代碼
    @JsonIgnore
    public List<InteractionModel> getChildren() {
        List<InteractionModel> result = new ArrayList<>();
        if (nextChainedCall != null) {
            result.add(nextChainedCall);
        }
        return result;
    }

    // 向後兼容的 setter，用於現有代碼
    @JsonIgnore
    public void setChildren(List<InteractionModel> children) {
        if (children != null && !children.isEmpty()) {
            this.nextChainedCall = children.get(0);
        }
    }

    // 向後兼容的 addChild 方法
    @JsonIgnore
    public void addChild(InteractionModel child) {
        if (this.nextChainedCall == null) {
            this.nextChainedCall = child;
        } else {
            // 如果已經有鏈式呼叫，則添加到鏈的末尾
            InteractionModel current = this.nextChainedCall;
            while (current.nextChainedCall != null) {
                current = current.nextChainedCall;
            }
            current.nextChainedCall = child;
        }
    }

    public InteractionModel() {
    }

    public void addArgument(String argument) {
        if (this.arguments == null) {
            this.arguments = new ArrayList<>();
        }
        this.arguments.add(argument);
    }

    /**
     * 添加內部呼叫活動
     */
    public void addInternalCall(DiagramNode internalCall) {
        if (this.internalCalls == null) {
            this.internalCalls = new ArrayList<>();
        }
        this.internalCalls.add(internalCall);
    }

    /**
     * 設置鏈式呼叫的下一個環節
     */
    public void setNextChainedCall(InteractionModel nextCall) {
        this.nextChainedCall = nextCall;
    }

    /**
     * Get enhanced caller representation with variable name and instance ID
     */
    @JsonIgnore
    public String getEnhancedCaller() {
        if (callerVariable != null && !callerVariable.isEmpty()) {
            if (callerInstanceId != null && !callerInstanceId.isEmpty()) {
                return callerVariable + ":" + callerInstanceId + ":" + caller;
            } else {
                return callerVariable + ":" + caller;
            }
        }
        return caller;
    }

    /**
     * Get enhanced callee representation with variable name and instance ID
     */
    @JsonIgnore
    public String getEnhancedCallee() {
        if (calleeVariable != null && !calleeVariable.isEmpty()) {
            if (calleeInstanceId != null && !calleeInstanceId.isEmpty()) {
                return calleeVariable + ":" + calleeInstanceId + ":" + callee;
            } else {
                return calleeVariable + ":" + callee;
            }
        }
        return callee;
    }

    @Override
    @JsonIgnore
    public int getStartLineNumber() {
        return this.lineNumber;
    }
}
