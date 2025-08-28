package kai.javaparser.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
    private List<MethodGroup> methodGroups; // 按方法分組的互動列表

    public SequenceDiagramData() {
        this.methodGroups = new ArrayList<>();
    }

    public void addMethodGroup(MethodGroup methodGroup) {
        if (this.methodGroups == null) {
            this.methodGroups = new ArrayList<>();
        }
        this.methodGroups.add(methodGroup);
    }

    /**
     * 獲取所有互動（扁平化，用於向後兼容）
     */
    @JsonIgnore
    public List<InteractionModel> getAllInteractions() {
        List<InteractionModel> allInteractions = new ArrayList<>();
        if (methodGroups != null) {
            for (MethodGroup group : methodGroups) {
                if (group.getInteractions() != null) {
                    allInteractions.addAll(group.getInteractions());
                }
            }
        }
        return allInteractions;
    }

    /**
     * 獲取所有控制流程片段（扁平化，用於向後兼容）
     */
    @JsonIgnore
    public List<ControlFlowFragment> getAllControlFlowFragments() {
        List<ControlFlowFragment> allFragments = new ArrayList<>();
        if (methodGroups != null) {
            for (MethodGroup group : methodGroups) {
                if (group.getControlFlowFragments() != null) {
                    allFragments.addAll(group.getControlFlowFragments());
                }
            }
        }
        return allFragments;
    }

    /**
     * 根據方法名查找方法分組
     */
    @JsonIgnore
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
    @JsonIgnore
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
