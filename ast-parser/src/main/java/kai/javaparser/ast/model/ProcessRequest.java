package kai.javaparser.ast.model;

import java.util.Map;

/**
 * 統一處理請求DTO
 * 用於AstProcessingFacadeService的統一API端點
 */
public class ProcessRequest {

    /**
     * 要解析的專案在伺服器上的路徑
     */
    private String projectPath;

    /**
     * 分析的入口方法完整限定名
     */
    private String entryPointMethodFqn;

    /**
     * 輸出類型
     */
    private OutputType outputType;

    /**
     * 靈活的參數map，用於傳遞特定輸出類型需要的額外配置
     * 例如：追蹤深度 depth、過濾規則等
     */
    private Map<String, Object> params;

    // 建構子
    public ProcessRequest() {
    }

    public ProcessRequest(String projectPath, String entryPointMethodFqn, OutputType outputType,
            Map<String, Object> params) {
        this.projectPath = projectPath;
        this.entryPointMethodFqn = entryPointMethodFqn;
        this.outputType = outputType;
        this.params = params;
    }

    // Getters and Setters
    public String getProjectPath() {
        return projectPath;
    }

    public void setProjectPath(String projectPath) {
        this.projectPath = projectPath;
    }

    public String getEntryPointMethodFqn() {
        return entryPointMethodFqn;
    }

    public void setEntryPointMethodFqn(String entryPointMethodFqn) {
        this.entryPointMethodFqn = entryPointMethodFqn;
    }

    public OutputType getOutputType() {
        return outputType;
    }

    public void setOutputType(OutputType outputType) {
        this.outputType = outputType;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    @Override
    public String toString() {
        return "ProcessRequest{" +
                "projectPath='" + projectPath + '\'' +
                ", entryPointMethodFqn='" + entryPointMethodFqn + '\'' +
                ", outputType=" + outputType +
                ", params=" + params +
                '}';
    }
}
