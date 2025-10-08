package kai.javaparser.ast.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 代表註解信息
 * 包含註解的名稱、參數和位置信息
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class AnnotationInfo {
    private String annotationName; // 註解名稱（完整限定名）
    private String simpleName; // 註解簡單名稱
    private List<AnnotationParameter> parameters; // 註解參數
    private int startLineNumber; // 註解開始行號
    private int endLineNumber; // 註解結束行號
    private int startPosition; // 註解開始位置
    private int endPosition; // 註解結束位置

    public AnnotationInfo() {
        this.parameters = new ArrayList<>();
    }

    public AnnotationInfo(String annotationName, String simpleName) {
        this.annotationName = annotationName;
        this.simpleName = simpleName;
        this.parameters = new ArrayList<>();
    }

    public void addParameter(AnnotationParameter parameter) {
        if (this.parameters == null) {
            this.parameters = new ArrayList<>();
        }
        this.parameters.add(parameter);
    }

    /**
     * 代表註解參數
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    public static class AnnotationParameter {
        private String parameterName; // 參數名稱（如果有的話）
        private String parameterValue; // 參數值
        private String parameterType; // 參數類型

        public AnnotationParameter() {
        }

        public AnnotationParameter(String parameterName, String parameterValue, String parameterType) {
            this.parameterName = parameterName;
            this.parameterValue = parameterValue;
            this.parameterType = parameterType;
        }
    }
}
