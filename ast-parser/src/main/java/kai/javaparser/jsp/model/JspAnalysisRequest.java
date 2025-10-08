package kai.javaparser.jsp.model;

import java.util.Set;

import lombok.Data;

/**
 * JSP 分析請求模型
 */
@Data
public class JspAnalysisRequest {

    /**
     * JSP 檔案路徑或資料夾路徑
     */
    private String filePath;

    /**
     * JSP 檔案名稱 (單一檔案模式時使用)
     */
    private String fileName;

    /**
     * 檔案副檔名過濾器 (資料夾模式時使用)
     */
    private Set<String> fileExtensions;
}
