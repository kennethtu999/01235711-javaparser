package kai.javaparser.diagram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import kai.javaparser.model.TraceResult;
import kai.javaparser.service.SequenceTraceService;

/**
 * 序列圖生成器（重構後的協調器）：
 * 1. 協調 SequenceTraceService 進行方法呼叫追蹤
 * 2. 協調 MermaidRenderer 進行 Mermaid 語法生成
 * 3. 提供統一的對外介面
 */
@Component
public class SequenceOutputGenerator {
    private static final Logger logger = LoggerFactory.getLogger(SequenceOutputGenerator.class);

    private final SequenceTraceService sequenceTraceService;

    @Autowired
    public SequenceOutputGenerator(SequenceTraceService sequenceTraceService) {
        this.sequenceTraceService = sequenceTraceService;
    }

    /**
     * 生成序列圖
     * 
     * @param entryPointMethodFqn 進入點方法的完整限定名
     * @param config              追蹤配置
     * @return Mermaid 語法字串
     */
    public String generate(String entryPointMethodFqn, SequenceOutputConfig config) {
        // 階段一：追蹤並建立呼叫樹
        TraceResult traceResult = sequenceTraceService.trace(entryPointMethodFqn, config);

        // 階段二：渲染呼叫樹
        MermaidRenderer renderer = new MermaidRenderer(config);
        String mermaidSyntax = renderer.render(traceResult);

        logger.info("\n---O Mermaid 序列圖語法 ---");
        logger.info(mermaidSyntax);

        return mermaidSyntax;
    }

}
