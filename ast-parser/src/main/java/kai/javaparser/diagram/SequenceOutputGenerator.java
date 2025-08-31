package kai.javaparser.diagram;

import java.io.IOException;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kai.javaparser.diagram.idx.AstIndex;
import kai.javaparser.model.TraceResult;
import lombok.Builder;

/**
 * 序列圖生成器（重構後的協調器）：
 * 1. 協調 SequenceTracer 進行方法呼叫追蹤
 * 2. 協調 MermaidRenderer 進行 Mermaid 語法生成
 * 3. 提供統一的對外介面
 */
@Builder
public class SequenceOutputGenerator {
    private static final Logger logger = LoggerFactory.getLogger(SequenceOutputGenerator.class);

    private SequenceOutputConfig config;
    private String astDir;
    private AstIndex astIndex;

    public void startup() {
        if (astIndex != null) {
            return;
        }

        try {
            astIndex = new AstIndex(Path.of(astDir));
            astIndex.loadOrBuild();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("執行期間發生錯誤: " + e.getMessage(), e);
        }
    }

    public String generate(String entryPointMethodFqn) {
        startup();

        // 階段一：追蹤並建立呼叫樹
        SequenceTracer tracer = SequenceTracer.builder()
                .astIndex(astIndex)
                .astDir(astDir)
                .config(config)
                .build();
        TraceResult traceResult = tracer.trace(entryPointMethodFqn);

        // 階段二：渲染呼叫樹
        MermaidRenderer renderer = new MermaidRenderer(config);
        String mermaidSyntax = renderer.render(traceResult);

        logger.info("\n---O Mermaid 序列圖語法 ---");
        logger.info(mermaidSyntax);

        return mermaidSyntax;
    }

}
