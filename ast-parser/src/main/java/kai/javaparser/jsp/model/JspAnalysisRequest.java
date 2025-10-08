package kai.javaparser.jsp.model;

import lombok.Data;

/**
 * JSP 分析請求模型
 */
@Data
public class JspAnalysisRequest {

    /**
     * JSP 檔案路徑
     */
    private String filePath;

    /**
     * JSP 檔案名稱
     */
    private String fileName;
}
