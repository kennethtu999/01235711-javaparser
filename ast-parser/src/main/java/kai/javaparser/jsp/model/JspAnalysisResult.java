package kai.javaparser.jsp.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * JSP 分析結果模型
 */
@Getter
@Setter
public class JspAnalysisResult {
    private String fileName;
    private Date analysisTimestamp;
    private String pagecodeClass;
    private List<JsfComponent> jsfComponents = new ArrayList<>();
    private List<JavaScriptFunction> javascriptFunctions = new ArrayList<>();
    private List<String> externalJsReferences = new ArrayList<>();
    private String error;
}
