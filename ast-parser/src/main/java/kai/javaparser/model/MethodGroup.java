package kai.javaparser.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

/**
 * 代表方法分組，用於將互動按方法進行分組
 * 包含方法的開始和結束行號，以及該方法內的所有互動
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class MethodGroup {
    private String methodName; // 方法名稱
    private String methodSignature; // 方法簽名（包含參數類型）
    private String className; // 所在類名
    private String fullMethodName; // 完整方法名（類名.方法名）
    private int startLineNumber; // 方法開始行號
    private int endLineNumber; // 方法結束行號
    private List<InteractionModel> interactions; // 該方法內的互動
    private List<ControlFlowFragment> controlFlowFragments; // 該方法內的控制流程片段
    private List<String> thrownExceptions; // 該方法內的拋出異常

    public MethodGroup() {
    }

    public void addInteraction(InteractionModel interaction) {
        if (this.interactions == null) {
            this.interactions = new ArrayList<>();
        }
        this.interactions.add(interaction);
    }

    public void addControlFlowFragment(ControlFlowFragment fragment) {
        if (this.controlFlowFragments == null) {
            this.controlFlowFragments = new ArrayList<>();
        }
        this.controlFlowFragments.add(fragment);
    }

    /**
     * 獲取方法的行數範圍描述
     */
    public String getLineRangeDescription() {
        return "lines " + startLineNumber + "-" + endLineNumber;
    }

    /**
     * 獲取完整的方法描述
     */
    public String getFullDescription() {
        StringBuilder desc = new StringBuilder();
        desc.append(className).append(".").append(methodName);
        if (methodSignature != null && !methodSignature.isEmpty()) {
            desc.append("(").append(methodSignature).append(")");
        }
        desc.append(" [").append(getLineRangeDescription()).append("]");
        return desc.toString();
    }
}
