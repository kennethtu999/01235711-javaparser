package kai.javaparser;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import kai.javaparser.java2ast.JavaToAstFile;
import kai.javaparser.model.AnnotationInfo;
import kai.javaparser.model.FileAstData;
import kai.javaparser.model.MethodGroup;

/**
 * 測試真實案例2文件中的註解解析
 */
public class RealCase2AnnotationTest {

    private JavaToAstFile javaToAstFile;

    @BeforeEach
    void setUp() {
        javaToAstFile = new JavaToAstFile();
    }

    @Test
    void testRealLoginUserFile() throws IOException {
        // 使用真實的 LoginUser.java 文件
        Path loginUserFile = Paths.get("test-project/src/main/java/com/example/case2/LoginUser.java");

        if (!Files.exists(loginUserFile)) {
            System.out.println("⚠️  LoginUser.java 文件不存在，跳過測試");
            return;
        }

        // 解析文件
        FileAstData result = javaToAstFile.parseJavaFile(
                loginUserFile,
                new String[] { "test-project/src/main/java" },
                new String[] {},
                "17");

        // 驗證結果
        assertNotNull(result, "解析結果不應為空");
        assertNotNull(result.getSequenceDiagramData(), "序列圖數據不應為空");

        System.out.println("📁 解析文件: " + loginUserFile.getFileName());
        System.out.println("📦 包名: " + result.getPackageName());
        System.out.println("🏷️  類別註解數量: " +
                (result.getSequenceDiagramData().getClassAnnotations() != null
                        ? result.getSequenceDiagramData().getClassAnnotations().size()
                        : 0));

        // 檢查方法註解
        List<MethodGroup> methodGroups = result.getSequenceDiagramData().getMethodGroups();
        assertNotNull(methodGroups, "方法分組不應為空");

        System.out.println("🔧 方法數量: " + methodGroups.size());

        // 查找有註解的方法
        for (MethodGroup method : methodGroups) {
            if (method.getAnnotations() != null && !method.getAnnotations().isEmpty()) {
                System.out.println("✅ 方法 '" + method.getMethodName() + "' 有 " +
                        method.getAnnotations().size() + " 個註解:");

                for (AnnotationInfo annotation : method.getAnnotations()) {
                    System.out.println("   📝 @" + annotation.getSimpleName());
                    if (annotation.getParameters() != null && !annotation.getParameters().isEmpty()) {
                        for (AnnotationInfo.AnnotationParameter param : annotation.getParameters()) {
                            System.out
                                    .println("      - " + param.getParameterName() + " = " + param.getParameterValue());
                        }
                    }
                }
            }
        }

        // 檢查欄位註解
        if (result.getFields() != null) {
            System.out.println("🏗️  欄位數量: " + result.getFields().size());
            for (var field : result.getFields()) {
                if (field.getAnnotations() != null && !field.getAnnotations().isEmpty()) {
                    System.out.println("✅ 欄位 '" + field.getFieldName() + "' 有 " +
                            field.getAnnotations().size() + " 個註解");
                }
            }
        }

        // 特別檢查 getLevel1 方法的 @SuppressWarnings 註解
        MethodGroup getLevel1Method = methodGroups.stream()
                .filter(m -> "getLevel1".equals(m.getMethodName()))
                .findFirst()
                .orElse(null);

        if (getLevel1Method != null) {
            assertNotNull(getLevel1Method.getAnnotations(), "getLevel1 方法應該有註解列表");
            assertTrue(getLevel1Method.getAnnotations().size() >= 1,
                    "getLevel1 方法應該至少有1個註解 (@SuppressWarnings)");

            // 檢查 @SuppressWarnings 註解
            boolean hasSuppressWarnings = getLevel1Method.getAnnotations().stream()
                    .anyMatch(a -> "SuppressWarnings".equals(a.getSimpleName()));

            assertTrue(hasSuppressWarnings, "getLevel1 方法應該有 @SuppressWarnings 註解");

            System.out.println("🎉 成功找到 @SuppressWarnings 註解！");
        } else {
            System.out.println("⚠️  未找到 getLevel1 方法");
        }
    }

    @Test
    void testAllCase2Files() throws IOException {
        // 測試所有案例2文件
        String[] files = {
                "test-project/src/main/java/com/example/case2/LoginUser.java",
                "test-project/src/main/java/com/example/case2/Company.java",
                "test-project/src/main/java/com/example/case2/AccessLog.java"
        };

        for (String filePath : files) {
            Path file = Paths.get(filePath);
            if (Files.exists(file)) {
                System.out.println("\n🔍 解析文件: " + file.getFileName());

                try {
                    FileAstData result = javaToAstFile.parseJavaFile(
                            file,
                            new String[] { "test-project/src/main/java" },
                            new String[] {},
                            "17");

                    // 統計註解信息
                    int classAnnotationCount = result.getSequenceDiagramData().getClassAnnotations() != null
                            ? result.getSequenceDiagramData().getClassAnnotations().size()
                            : 0;
                    int fieldAnnotationCount = result.getFields() != null
                            ? (int) result.getFields().stream()
                                    .filter(f -> f.getAnnotations() != null && !f.getAnnotations().isEmpty()).count()
                            : 0;
                    int methodAnnotationCount = result.getSequenceDiagramData().getMethodGroups() != null
                            ? (int) result.getSequenceDiagramData().getMethodGroups().stream()
                                    .filter(m -> m.getAnnotations() != null && !m.getAnnotations().isEmpty()).count()
                            : 0;

                    System.out.println("   📊 統計: 類別註解=" + classAnnotationCount +
                            ", 欄位註解=" + fieldAnnotationCount +
                            ", 方法註解=" + methodAnnotationCount);

                } catch (Exception e) {
                    System.out.println("   ❌ 解析失敗: " + e.getMessage());
                }
            } else {
                System.out.println("   ⚠️  文件不存在: " + filePath);
            }
        }
    }
}
