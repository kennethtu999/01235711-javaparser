package kai.javaparser.ast.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Getter;
import lombok.Setter;

/**
 * 代表循序圖的數據結構，包含入口方法和所有互動
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@Setter
public class SequenceDiagramData {
    private String classFqn; // 入口方法
    private String classType; // 類別類型: "Class", "AbstractClass", "Interface"
    private String extendsClassFqn; // 繼承的類別 FQN (例如: "java.lang.Object")
    private List<String> implementsInterfaceFqns; // 實現的介面 FQN 列表 (例如: ["java.io.Serializable"])
    private List<MethodGroup> methodGroups; // 按方法分組的互動列表
    private List<AnnotationInfo> classAnnotations; // 類別上的註解

    public SequenceDiagramData() {
        this.methodGroups = new ArrayList<>();
        this.classAnnotations = new ArrayList<>();
        this.implementsInterfaceFqns = new ArrayList<>();
    }

    public void addMethodGroup(MethodGroup methodGroup) {
        if (this.methodGroups == null) {
            this.methodGroups = new ArrayList<>();
        }
        this.methodGroups.add(methodGroup);
    }

    public void addClassAnnotation(AnnotationInfo annotation) {
        if (this.classAnnotations == null) {
            this.classAnnotations = new ArrayList<>();
        }
        this.classAnnotations.add(annotation);
    }

    /**
     * 根據方法名查找方法分組
     */
    public MethodGroup findMethodGroup(String methodName) {
        if (methodGroups != null) {
            for (MethodGroup group : methodGroups) {
                if (methodName.equals(group.getMethodName())) {
                    return group;
                }
            }
        }
        return null;
    }

    /**
     * 根據行號查找所在的方法分組
     */
    public MethodGroup findMethodGroupByLineNumber(int lineNumber) {
        if (methodGroups != null) {
            for (MethodGroup group : methodGroups) {
                if (lineNumber >= group.getStartLineNumber() && lineNumber <= group.getEndLineNumber()) {
                    return group;
                }
            }
        }
        return null;
    }
}
