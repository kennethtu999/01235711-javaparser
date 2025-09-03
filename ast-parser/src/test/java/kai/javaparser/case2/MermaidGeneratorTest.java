package kai.javaparser.case2;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;

import kai.javaparser.AstParserLauncher;
import kai.javaparser.diagram.SequenceOutputConfig;
import kai.javaparser.diagram.SequenceOutputGenerator;
import kai.javaparser.diagram.TraceFilter;
import kai.javaparser.diagram.filter.DefaultTraceFilter;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class MermaidGeneratorTest {
  private static final Logger logger = LoggerFactory.getLogger(MermaidGeneratorTest.class);

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
    String methodSignature = "com.example.case2.LoginUser.getLevel1()";
    String basePackage = "com.example";

    Set<String> exclusionClassSet = new HashSet<>(Arrays.asList("java.lang"));

    Set<String> exclusionMethodSet = new HashSet<>(Arrays.asList());

    TraceFilter filter = new DefaultTraceFilter(exclusionClassSet, exclusionMethodSet);

    SequenceOutputConfig config = SequenceOutputConfig.builder()
        .depth(4)
        .hideDetailsInConditionals(false)
        .hideDetailsInChainExpression(false)
        .basePackage(basePackage)
        .filter(filter)
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

    System.out.println("output: " + output);
    Assertions.assertEquals(
        """
                        sequenceDiagram
            actor User
            participant com_example_case2_LoginUser as com_example_case2_LoginUser
            participant java_util_logging_Logger as java.util.logging.Logger
            participant java_util_ArrayListcom_example_case2_AccountItem as java.util.ArrayList<com.example.case2.AccountItem>
            participant com_example_case2_AccountItem as com.example.case2.AccountItem
            User->>com_example_case2_LoginUser: getLevel1()
            activate com_example_case2_LoginUser
              com_example_case2_LoginUser->>com_example_case2_LoginUser: authzAcntList : getLevel2()
              activate com_example_case2_LoginUser
                alt x >= 0
                  com_example_case2_LoginUser->>java_util_logging_Logger: info("==== x >=0, current value:" + x)
                end
                com_example_case2_LoginUser->>com_example_case2_LoginUser: getLevel3()
                activate com_example_case2_LoginUser
                  alt x >= 0
                    com_example_case2_LoginUser->>java_util_logging_Logger: info("==== x >=0, current value:" + x)
                  end
                  com_example_case2_LoginUser->>java_util_ArrayListcom_example_case2_AccountItem: java.util.ArrayList<com.example.case2.AccountItem>()
                deactivate com_example_case2_LoginUser
              deactivate com_example_case2_LoginUser
              loop accountItem : authzAcntList
                alt accountItem.getIsRelated().intValue() == AAConstants.YES
                  com_example_case2_LoginUser-->>com_example_case2_AccountItem: getIsRelated()
                  com_example_case2_LoginUser->>java_util_logging_Logger: info("==== add 母子公司log accountList的A/C LIST:" + accountItem)
                end
              end
            deactivate com_example_case2_LoginUser
                        """
            .replaceAll("\\s+", " ").trim(),
        output.replaceAll("\\s+", " ").trim());
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