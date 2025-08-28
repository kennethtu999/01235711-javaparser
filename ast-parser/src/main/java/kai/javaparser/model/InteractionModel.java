package kai.javaparser.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 代表方法呼叫的互動模型，用於生成循序圖
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
    private List<InteractionModel> children; // 巢狀呼叫

    public InteractionModel() {
    }

    public void addArgument(String argument) {
        if (this.arguments == null) {
            this.arguments = new ArrayList<>();
        }
        this.arguments.add(argument);
    }

    public void addChild(InteractionModel child) {
        if (this.children == null) {
            this.children = new ArrayList<>();
        }
        this.children.add(child);
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
