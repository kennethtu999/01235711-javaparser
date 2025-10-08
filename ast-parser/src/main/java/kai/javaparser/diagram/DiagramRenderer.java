package kai.javaparser.diagram;

import kai.javaparser.ast.model.TraceResult;

/**
 * 圖表渲染器介面
 * 定義將 TraceResult 轉換為不同格式圖表的契約
 */
public interface DiagramRenderer {

    /**
     * 渲染圖表
     * 
     * @param traceResult 追蹤結果
     * @return 渲染後的圖表字串
     */
    String render(TraceResult traceResult);

    /**
     * 獲取渲染器支援的格式名稱
     * 
     * @return 格式名稱，如 "Mermaid", "PlantUML" 等
     */
    String getFormatName();
}
