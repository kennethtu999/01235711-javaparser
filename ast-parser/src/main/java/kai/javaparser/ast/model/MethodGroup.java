package kai.javaparser.ast.model;

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
    private List<AnnotationInfo> annotations; // 方法上的註解

    public MethodGroup() {
        this.annotations = new ArrayList<>();
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

    public void addAnnotation(AnnotationInfo annotation) {
        if (this.annotations == null) {
            this.annotations = new ArrayList<>();
        }
        this.annotations.add(annotation);
    }

}
