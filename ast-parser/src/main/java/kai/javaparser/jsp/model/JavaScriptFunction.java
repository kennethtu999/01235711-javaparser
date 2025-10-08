package kai.javaparser.jsp.model;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * JavaScript 函式模型
 */
@Getter
@Setter
public class JavaScriptFunction {
    private String name;
    private String content;
    private int lineNumber;
    private boolean containsAjaxCall = false;
    private boolean containsNavigation = false;
    private List<String> ajaxCalls = new ArrayList<>();
    private String aiAnalysis;
}
