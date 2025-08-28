package kai.javaparser.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Getter;
import lombok.Setter;

/**
 * Represents control flow fragments like if/else blocks and loops
 * Maps to UML Combined Fragments in sequence diagrams
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@Setter
public class ControlFlowFragment implements DiagramNode {
    private String sequenceId;
    private ControlFlowType type;
    private String condition; // The condition expression (e.g., "!input.isEmpty()")
    private List<InteractionModel> interactions; // Interactions within this fragment
    private List<ControlFlowFragment> alternatives; // For ALTERNATIVE type (if/else)

    // 新增：上下文信息
    private String callerClass; // 控制流程所在的類
    private String callerMethod; // 控制流程所在的方法
    private String contextPath; // 完整的上下文路徑
    private int startLineNumber; // 控制流程開始的行號
    private int endLineNumber; // 控制流程結束的行號

    public ControlFlowFragment() {
        this.interactions = new ArrayList<>();
        this.alternatives = new ArrayList<>();
    }

    public void addInteraction(InteractionModel interaction) {
        if (this.interactions == null) {
            this.interactions = new ArrayList<>();
        }
        this.interactions.add(interaction);
    }

    public void addAlternative(ControlFlowFragment alternative) {
        if (this.alternatives == null) {
            this.alternatives = new ArrayList<>();
        }
        this.alternatives.add(alternative);
    }

    /**
     * Get full context path for this control flow fragment
     */
    @JsonIgnore
    public String getFullContextPath() {
        if (contextPath != null && !contextPath.isEmpty()) {
            return contextPath;
        }

        StringBuilder path = new StringBuilder();
        if (callerClass != null) {
            path.append(callerClass);
        }

        if (callerMethod != null) {
            path.append(".").append(callerMethod);
        }

        return path.toString();
    }

    /**
     * Get context-aware description
     */
    @JsonIgnore
    public String getContextAwareDescription() {
        StringBuilder desc = new StringBuilder();

        if (callerMethod != null) {
            desc.append(callerMethod).append(": ");
        }

        desc.append(type.toString().toLowerCase());

        if (condition != null && !condition.isEmpty()) {
            desc.append(" (").append(condition).append(")");
        }

        return desc.toString();
    }

    @Override
    public int getStartLineNumber() {
        return this.startLineNumber;
    }

    public enum ControlFlowType {
        ALTERNATIVE, // if/else blocks
        LOOP, // for/while loops
        OPTIONAL // optional blocks (single if without else)
    }
}
