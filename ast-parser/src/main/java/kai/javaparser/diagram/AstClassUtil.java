package kai.javaparser.diagram;

public class AstClassUtil {

    public static String getClassFqnFromMethodFqn(String methodFqn) {
        int lastDot = methodFqn.lastIndexOf('.', methodFqn.indexOf('('));
        return lastDot == -1 ? "" : methodFqn.substring(0, lastDot);
    }

    /**
     * 進來可能是 Class FQN 或 Method FQN
     * 
     * @param fqn
     * @return
     */
    public static String getSimpleClassName(String fqn) {
        String classFqn = getClassFqnFromMethodFqn(fqn);
        classFqn = "".equals(classFqn) ? fqn : classFqn;

        int lastDot = classFqn.lastIndexOf('.');
        if (lastDot == -1) {
            return classFqn;
        } else {
            // Check if the FQN is a generic class
            int genericStart = classFqn.indexOf('<');
            if (genericStart != -1) {
                lastDot = classFqn.substring(0, genericStart).lastIndexOf('.');
                return classFqn.substring(lastDot + 1, genericStart);
            } else {
                return classFqn.substring(lastDot + 1);
            }
        }
    }

    public static String getMethodSignature(String fqn) {
        String classFqn = getClassFqnFromMethodFqn(fqn);
        if (fqn.length() <= classFqn.length()) { // Handle cases where FQN might be malformed
            return fqn;
        }
        return fqn.substring(classFqn.length() + 1);
    }
}