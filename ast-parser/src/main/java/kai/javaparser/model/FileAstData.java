package kai.javaparser.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import kai.javaparser.diagram.AstClassUtil;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.*;

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
    private char[] fileContent;
    private String relativePath;
    private String absolutePath;
    private String packageName;
    private List<String> imports;
    private AstNode compilationUnitNode; // 檔案 AST 的根節點

    public FileAstData() {
        this.imports = new ArrayList<>();
    }

    public FileAstData(char[] fileContent) {
        this.fileContent = fileContent;
        this.imports = new ArrayList<>();
    }

    /**
     * 從 AST 中尋找並返回頂層類別的完整限定名 (FQN)。
     * 通常一個 .java 檔案對應一個 public 的頂層類別。
     *
     * @return 包含 FQN 的 Optional，如果找不到則為空。
     */
    @JsonIgnore // 此方法是輔助方法，不需要序列化到 JSON 中
    public Optional<String> findTopLevelClassFqn() {
        if (compilationUnitNode == null || packageName == null) {
            return Optional.empty();
        }
        return compilationUnitNode.getChildren().stream()
                .filter(node -> node.getType() == AstNodeType.TYPE_DECLARATION)
                .findFirst()
                .map(AstNode::getName)
                .map(className -> packageName + "." + className);
    }

    /**
     * 根據方法的完整限定名 (FQN) 尋找對應的方法宣告節點。
     *
     * @param methodFqn 方法的 FQN，例如 "com.example.MyClass.myMethod(int)"
     * @return 包含方法 AST 節點的 Optional，如果找不到則為空。
     */
    @JsonIgnore
    public Optional<AstNode> findMethodNode(String methodFqn) {
        String methodSignature = AstClassUtil.getMethodSignature(methodFqn);
        String simpleMethodName = methodSignature.split("\\(")[0];

        return compilationUnitNode.getChildren().stream()
                .filter(node -> node.getType() == AstNodeType.TYPE_DECLARATION) // 找到類別宣告
                .flatMap(classNode -> classNode.getChildren().stream()) // 進入類別的子節點
                .filter(node -> node.getType() == AstNodeType.METHOD_DECLARATION) // 找到方法宣告
                .filter(methodNode -> methodNode.getName().equals(simpleMethodName)) // 簡單名稱匹配
                // TODO: 可以在此處加入更精確的參數匹配邏輯來處理方法重載 (overloading)
                .findFirst();
    }

    /**
     * 遞迴地從一個起始節點開始，尋找其下所有的方法呼叫 (Method Invocation) 節點。
     *
     * @param startNode 搜尋的起始節點 (通常是一個方法宣告節點)。
     * @return 找到的所有方法呼叫節點的列表。
     */
    @JsonIgnore
    public List<AstNode> findMethodInvocations(AstNode startNode) {
        List<AstNode> invocations = new ArrayList<>();
        Queue<AstNode> queue = new LinkedList<>();
        queue.add(startNode);

        while (!queue.isEmpty()) {
            AstNode current = queue.poll();
            if (current.getType() == AstNodeType.METHOD_INVOCATION) {
                invocations.add(current);
            }
            if (current.getChildren() != null) {
                queue.addAll(current.getChildren());
            }
        }
        return invocations;
    }

    /**
     * 檢查此檔案代表的類別中是否包含指定名稱的欄位 (Field)。
     * 這是一個簡化的檢查，主要用於輔助判斷 getter/setter。
     *
     * @param fieldName 要檢查的欄位名稱。
     * @return 如果存在該欄位，則返回 true。
     */
    @JsonIgnore
    public boolean hasField(String fieldName) {
        if (compilationUnitNode == null) {
            return false;
        }

        return compilationUnitNode.getChildren().stream()
            .filter(node -> node.getType() == AstNodeType.TYPE_DECLARATION) // 找到類別
            .flatMap(classNode -> classNode.getChildren().stream()) // 進入類別成員
            .filter(memberNode -> memberNode.getType() == AstNodeType.FIELD_DECLARATION) // 找到欄位宣告
            .flatMap(fieldDeclNode -> fieldDeclNode.getChildren().stream()) // 進入欄位片段
            .filter(fragmentNode -> fragmentNode.getType() == AstNodeType.VARIABLE_DECLARATION_FRAGMENT)
            .anyMatch(varNode -> fieldName.equals(varNode.getName())); // 檢查名稱是否匹配
    }
}