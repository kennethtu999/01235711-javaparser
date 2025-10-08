package kai.javaparser.jsp.model;

import lombok.Getter;
import lombok.Setter;

/**
 * JSF 元件模型
 */
@Getter
@Setter
public class JsfComponent {
    private String id;
    private String type;
    private String action;
    private String eventType;
    private int lineNumber;
}
