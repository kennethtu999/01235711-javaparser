package kai.javaparser.diagram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import kai.javaparser.ast.service.SequenceTraceService;
import kai.javaparser.diagram.idx.AstIndex;
import kai.javaparser.model.TraceResult;

/**
 * 圖表服務
 * 提供統一的圖表生成介面，支援多種圖表格式
 */
@Service
public class DiagramService {
    private static final Logger logger = LoggerFactory.getLogger(DiagramService.class);

    private final SequenceTraceService sequenceTraceService;
    private final AstIndex astIndex;

    @Autowired
    public DiagramService(SequenceTraceService sequenceTraceService, AstIndex astIndex) {
        this.sequenceTraceService = sequenceTraceService;
        this.astIndex = astIndex;
    }

    /**
     * 生成圖表
     * 
     * @param entryPointMethodFqn 進入點方法的完整限定名
     * @param config              追蹤配置
     * @return 圖表字串
     */
    public String generateDiagram(String entryPointMethodFqn, SequenceOutputConfig config) {
        logger.info("開始生成Mermaid圖表，進入點: {}", entryPointMethodFqn);

        try {
            // 1. 追蹤並建立呼叫樹
            TraceResult traceResult = sequenceTraceService.trace(entryPointMethodFqn, config);

            // 2. 創建渲染器並渲染呼叫樹
            DiagramRenderer renderer = new MermaidRenderer(config, astIndex);
            String diagram = renderer.render(traceResult);

            logger.info("Mermaid圖表生成完成，長度: {} 字元", diagram.length());
            return diagram;

        } catch (Exception e) {
            logger.error("圖表生成失敗", e);
            throw new RuntimeException("圖表生成失敗: " + e.getMessage(), e);
        }
    }

    /**
     * 獲取當前使用的圖表格式名稱
     * 
     * @return 格式名稱
     */
    public String getFormatName() {
        return "Mermaid";
    }
}
