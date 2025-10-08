package kai.javaparser.ast.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 代表類別中的屬性（欄位）信息
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class FieldInfo {
    private String fieldName; // 屬性名稱
    private String fieldType; // 屬性類型
    private String modifiers; // 修飾符（public, private, protected, static, final 等）
    private int startLineNumber; // 屬性開始行號
    private int endLineNumber; // 屬性結束行號
    private String defaultValue; // 預設值（如果有）
    private String comment; // 註解（如果有）
    private List<AnnotationInfo> annotations; // 屬性上的註解

    public FieldInfo() {
        this.annotations = new ArrayList<>();
    }

    public FieldInfo(String fieldName, String fieldType, String modifiers,
            int startLineNumber, int endLineNumber) {
        this.fieldName = fieldName;
        this.fieldType = fieldType;
        this.modifiers = modifiers;
        this.startLineNumber = startLineNumber;
        this.endLineNumber = endLineNumber;
        this.annotations = new ArrayList<>();
    }

    public void addAnnotation(AnnotationInfo annotation) {
        if (this.annotations == null) {
            this.annotations = new ArrayList<>();
        }
        this.annotations.add(annotation);
    }

}
