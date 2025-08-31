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
import kai.javaparser.diagram.SequenceOutputGenerator;
import kai.javaparser.diagram.SequenceOutputConfig;

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
                String methodSignature = "com.example.case2.LoginUser.getFXQueryAcntList()";

                SequenceOutputConfig config = SequenceOutputConfig.builder()
                                .depth(2)
                                .hideDetailsInConditionals(false)
                                .hideDetailsInChainExpression(false)
                                .basePackage("com.example")
                                .build();

                // Act: 執行 MermaidGenerator 的 main 方法
                SequenceOutputGenerator generator = SequenceOutputGenerator.builder()
                                .astDir(resourcePath.toAbsolutePath().toString())
                                .config(config)
                                .build();
                String output = generator.generate(methodSignature);

                // 為了方便除錯，可以在測試執行時將捕獲的內容印到標準錯誤流
                // System.err.println("--- Captured MermaidGenerator Output ---\n" + output);
                Files.writeString(new File("build/diagram.mermaid").toPath(), output);

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