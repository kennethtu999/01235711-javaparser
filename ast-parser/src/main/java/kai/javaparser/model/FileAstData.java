package kai.javaparser.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonInclude;
import kai.javaparser.util.Util;

import lombok.Getter;
import lombok.Setter;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@Setter
public class FileAstData implements Serializable {
    private char[] fileContent;
    private String relativePath; // Relative path from the base directory
    private String absolutePath;
    private String packageName;
    private List<String> imports;
    private AstNode compilationUnitNode; // The root of the AST tree for this file

    public FileAstData() {
    }

    public FileAstData(char[] fileContent) {
        this.fileContent = fileContent;
        this.imports = new ArrayList<>();
    }

    /**
     * 是否為純屬性的判斷
     * TODO: 需要更精確的判斷，目前是先求有解，之後再優化
     * 
     * @param methodName
     * @return
     */
    public boolean isGetterSetter(String methodName) {
        List<AstNode> classNodes = new ArrayList<>();
            
        Optional<AstNode> current = compilationUnitNode.getChildren().stream()
            .filter(node -> node.getType() == AstNodeType.TYPE_DECLARATION)
            .findFirst();

        if (current.isPresent()) {
            for (AstNode classNode : current.get().getChildren()) {
                if (classNode.getType() == AstNodeType.FIELD_DECLARATION) {
                    for (AstNode fieldNode : classNode.getChildren()) {
                        if (fieldNode.getType() == AstNodeType.VARIABLE_DECLARATION_FRAGMENT && fieldNode.getName().equals(methodName)) {
                            classNodes.add(fieldNode);
                        }
                    }
                }
            }
        }

        return !classNodes.isEmpty();
    }
}