package kai.javaparser.diagram;

import java.util.HashSet;

import kai.javaparser.diagram.filter.DefaultTraceFilter;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SequenceOutputConfig {
    /** 追蹤深度 */
    @Builder.Default
    private int depth = 1;

    /** 是否隱藏條件式中的細節 */
    @Builder.Default
    private boolean hideDetailsInConditionals = true;

    /** 是否隱藏Chain Expression中間的細節 */
    @Builder.Default
    private boolean hideDetailsInChainExpression = true;

    /** 基礎包 */
    private String basePackage;

    /** 過濾器 */
    @Builder.Default
    private TraceFilter filter = new DefaultTraceFilter(new HashSet<>(), new HashSet<>());

}
