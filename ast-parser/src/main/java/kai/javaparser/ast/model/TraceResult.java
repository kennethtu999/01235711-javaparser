package kai.javaparser.ast.model;

import java.util.List;
import lombok.Data;

/**
 * 存放序列追蹤結果的容器，代表一個完整的呼叫樹。
 */
@Data
public class TraceResult {
    private final String entryPointMethodFqn;
    private final List<DiagramNode> sequenceNodes; // 頂層的節點列表
}
