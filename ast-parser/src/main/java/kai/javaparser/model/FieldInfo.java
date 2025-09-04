package kai.javaparser.model;

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

    public FieldInfo() {
    }

    public FieldInfo(String fieldName, String fieldType, String modifiers,
            int startLineNumber, int endLineNumber) {
        this.fieldName = fieldName;
        this.fieldType = fieldType;
        this.modifiers = modifiers;
        this.startLineNumber = startLineNumber;
        this.endLineNumber = endLineNumber;
    }

    /**
     * 獲取屬性的行數範圍描述
     */
    public String getLineRangeDescription() {
        return "lines " + startLineNumber + "-" + endLineNumber;
    }

    /**
     * 獲取完整的屬性描述
     */
    public String getFullDescription() {
        StringBuilder sb = new StringBuilder();
        if (modifiers != null && !modifiers.isEmpty()) {
            sb.append(modifiers).append(" ");
        }
        if (fieldType != null) {
            sb.append(fieldType).append(" ");
        }
        if (fieldName != null) {
            sb.append(fieldName);
        }
        if (defaultValue != null && !defaultValue.isEmpty()) {
            sb.append(" = ").append(defaultValue);
        }
        return sb.toString();
    }
}
