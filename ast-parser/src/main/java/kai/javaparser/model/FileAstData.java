package kai.javaparser.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import kai.javaparser.diagram.AstClassUtil;
import lombok.Getter;
import lombok.Setter;

/**
 * 代表單一 Java 原始檔解析後的結構化資料。
 * <p>
 * 這個類別不僅是資料的容器，還提供了一系列輔助方法來查詢其內部的 AST (抽象語法樹)，
 * 從而將 AST 的複雜結構封裝起來，讓外部呼叫者 (如 AstIndex、SequenceDiagramGenerator)
 * 的邏輯更清晰。
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@Setter
public class FileAstData implements Serializable {
    @JsonIgnore
    private char[] fileContent;
    private String relativePath;
    private String absolutePath;
    private String packageName;
    private List<String> imports;
    private SequenceDiagramData sequenceDiagramData; // 檔案 AST 的根節點
    private List<FieldInfo> fields; // 類別的所有屬性

    public FileAstData() {
        this.imports = new ArrayList<>();
        this.fields = new ArrayList<>();
    }

    public FileAstData(char[] fileContent) {
        this.fileContent = fileContent;
        this.imports = new ArrayList<>();
        this.fields = new ArrayList<>();
    }

    /**
     * 從 AST 中尋找並返回頂層類別的完整限定名 (FQN)。
     * 通常一個 .java 檔案對應一個 public 的頂層類別。
     *
     * @return 包含 FQN 的 Optional，如果找不到則為空。
     */
    public Optional<String> findTopLevelClassFqn() {
        if (sequenceDiagramData == null || packageName == null) {
            return Optional.empty();
        }
        return Optional.of(sequenceDiagramData.getClassFqn());
    }

    /**
     * TODO 根據方法的完整限定名 (FQN) 尋找對應的方法宣告節點。
     *
     * @param methodFqn 方法的 FQN，例如 "com.example.MyClass.myMethod(int)"
     * @return 包含方法 AST 節點的 Optional，如果找不到則為空。
     */
    public Optional<SequenceDiagramData> findMethodNode(String methodFqn) {
        String methodSignature = AstClassUtil.getMethodSignature(methodFqn);
        String simpleMethodName = methodSignature.split("\\(")[0];

        System.out.println("尋找方法: " + methodFqn);
        System.out.println("方法簽名: " + methodSignature);
        System.out.println("簡單方法名: " + simpleMethodName);

        if (sequenceDiagramData != null && sequenceDiagramData.getMethodGroups() != null) {
            System.out.println("找到 " + sequenceDiagramData.getMethodGroups().size() + " 個方法組");
            for (MethodGroup group : sequenceDiagramData.getMethodGroups()) {
                System.out.println("檢查方法組: " + group.getMethodName() + " vs " + simpleMethodName);
                if (simpleMethodName.equals(group.getMethodName())) {
                    System.out.println("找到匹配的方法: " + group.getMethodName());
                    // 創建一個新的 SequenceDiagramData 來代表這個方法
                    SequenceDiagramData methodData = new SequenceDiagramData();
                    methodData.setClassFqn(group.getFullMethodName());
                    methodData.addMethodGroup(group);
                    return Optional.of(methodData);
                }
            }
        } else {
            System.out.println("sequenceDiagramData 或 methodGroups 為 null");
        }
        return Optional.empty();
    }

    /**
     * 根據方法節點尋找該方法中的所有方法呼叫。
     *
     * @param methodNode 方法節點
     * @return 方法呼叫列表
     */
    public List<InteractionModel> findMethodInvocations(SequenceDiagramData methodNode) {
        List<InteractionModel> invocations = new ArrayList<>();

        if (methodNode != null && methodNode.getMethodGroups() != null) {
            for (MethodGroup group : methodNode.getMethodGroups()) {
                if (group.getInteractions() != null) {
                    invocations.addAll(group.getInteractions());
                }
            }
        }

        return invocations;
    }
}