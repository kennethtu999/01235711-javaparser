package kai.javaparser.diagram.output.item;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MermailCall extends AbstractMermaidItem {

    private String actorName;
    private String calleeId;
    private String methodName;
    private List<String> arguments;

    public MermailCall(String actorName, String calleeId, String methodName,
            List<String> arguments) {
        this.actorName = actorName;
        this.calleeId = calleeId;
        this.methodName = methodName;
        this.arguments = arguments;
    }

    /**
     * 1. remove package name from argument
     * 2. trim method name to varible name
     * 
     * @return
     */
    private String getArgumentsString() {
        StringBuilder sb = new StringBuilder();
        for (String arg : arguments) {
            String argName = arg.replaceAll("[^\\].]*\\.", "");

            if (argName.endsWith("()")) {
                argName = argName.substring(0, argName.length() - 2);
            }

            if (argName.startsWith("get")) {
                argName = argName.substring(3);
                if (argName.length() > 1) {
                    argName = argName.substring(0, 1).toLowerCase() + argName.substring(1);
                }
            }

            sb.append(argName).append(", ");
        }
        return sb.toString().substring(0, sb.length() - 2);
    }

    @Override
    public String toDiagramString(int indentLevel) {
        if (arguments == null || arguments.isEmpty()) {
            return getFullContent(indentLevel, String.format("%s->>%s: %s()", actorName, calleeId,
                    methodName));
        }

        return getFullContent(indentLevel, String.format("%s->>%s: %s(%s)", actorName, calleeId,
                methodName, getArgumentsString()));
    }

}
