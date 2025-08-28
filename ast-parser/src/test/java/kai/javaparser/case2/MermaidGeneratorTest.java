package kai.javaparser.case2;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
// import static org.junit.jOupiter.api.Assertions.assertTrue;

import kai.javaparser.AstParserLauncher;
import kai.javaparser.diagram.SequenceDiagramGenerator;

public class MermaidGeneratorTest {

        @BeforeEach
        public void setUp() throws Exception {
                // Locate the test-project subproject relative to the current project
                Path currentProjectDir = Paths.get("").toAbsolutePath();
                Path testProjectRoot = Paths.get(currentProjectDir.toString() + "/../test-project");

                // remove the build directory
                deleteDirectory(currentProjectDir.resolve("build/parsed-ast").toFile());

                assertTrue(Files.exists(testProjectRoot) && Files.isDirectory(testProjectRoot),
                                "test-project directory should exist at: " + testProjectRoot.toAbsolutePath());

                String outputDirArg = currentProjectDir.resolve("build/parsed-ast").toAbsolutePath().toString();
                String javaComplianceLevel = "8";

                String[] args = { testProjectRoot.toString(), outputDirArg, javaComplianceLevel };

                System.out.println("Running AstParserLauncher with args: " + String.join(" ", args));
                AstParserLauncher.main(args);
        }

        @AfterEach
        public void restoreStreams() {
        }

        /**
         * 依照指定的Method，生成對應的Sequence Diagram
         * 
         * @throws IOException
         * @throws URISyntaxException
         */
        @Test
        void testGenerateMermaidForCreateList() throws IOException, URISyntaxException {
                Path resourcePath = Paths.get("build/parsed-ast");
                String methodSignature = "com.example.case2.CASEMain2.initViewForm2()";
                String basePackage = "com.example";
                String[] exclusionClassSet = {
                                "org",
                                "java",
                                "com.ibm.tw.commons",
                                "com.scsb.ewb.j2ee" };

                String[] exclusionMethodSet = {
                                "getBundleString",
                                "setWidth",
                                "setStyleClass",
                                "addHeader",
                                "setColspan",
                                "setAlign",
                                "getDisplayMoney",
                                "add",
                                "addRecord"
                };

                // Act: 執行 MermaidGenerator 的 main 方法
                String output = SequenceDiagramGenerator.generate(
                                resourcePath.toAbsolutePath().toString(),
                                methodSignature,
                                basePackage,
                                String.join(",", exclusionClassSet),
                                String.join(",", exclusionMethodSet),
                                2);

                // 為了方便除錯，可以在測試執行時將捕獲的內容印到標準錯誤流
                // System.err.println("--- Captured MermaidGenerator Output ---\n" + output);
                Files.writeString(new File("build/diagram.mermaid").toPath(), output);

                // assertTrue(output.contains("sequenceDiagram"), "輸出應包含 Mermaid 序列圖類型宣告
                // 'sequenceDiagram'");
                // assertTrue(output.contains("participant"), "輸出應包含 participant 宣告");
                // assertTrue(output.contains("User"), "輸出應包含 User actor");
                // assertTrue(output.contains("MyClass"), "輸出應包含 MyClass 類別");
                // assertTrue(output.contains("createList"), "輸出應包含 createList 方法");
                // assertTrue(output.contains("->>"), "輸出應包含表示流程的箭頭 '->>'");
        }

        boolean deleteDirectory(File directoryToBeDeleted) {
                if (!Files.exists(directoryToBeDeleted.toPath())) {
                        return true;
                }

                File[] allContents = directoryToBeDeleted.listFiles();
                if (allContents != null) {
                        for (File file : allContents) {
                                deleteDirectory(file);
                        }
                }
                return directoryToBeDeleted.delete();
        }
}