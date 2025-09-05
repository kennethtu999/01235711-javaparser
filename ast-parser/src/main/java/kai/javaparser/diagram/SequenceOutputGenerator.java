package kai.javaparser.diagram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 序列圖生成器（重構後的協調器）：
 * 1. 協調 DiagramService 進行圖表生成
 * 2. 提供統一的對外介面
 * 
 * @deprecated 建議直接使用 DiagramService
 */
@Component
@Deprecated
public class SequenceOutputGenerator {
    private static final Logger logger = LoggerFactory.getLogger(SequenceOutputGenerator.class);

    private final DiagramService diagramService;

    @Autowired
    public SequenceOutputGenerator(DiagramService diagramService) {
        this.diagramService = diagramService;
    }

    /**
     * 生成序列圖
     * 
     * @param entryPointMethodFqn 進入點方法的完整限定名
     * @param config              追蹤配置
     * @return 圖表語法字串
     */
    public String generate(String entryPointMethodFqn, SequenceOutputConfig config) {
        logger.info("使用 DiagramService 生成 {} 圖表", diagramService.getFormatName());

        // 委託給 DiagramService
        String diagram = diagramService.generateDiagram(entryPointMethodFqn, config);

        logger.info("\n---O {} 序列圖語法 ---", diagramService.getFormatName());
        logger.info(diagram);

        return diagram;
    }

}
