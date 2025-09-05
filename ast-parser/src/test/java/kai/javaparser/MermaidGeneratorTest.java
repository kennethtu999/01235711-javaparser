package kai.javaparser;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import kai.javaparser.diagram.SequenceOutputConfig;
import kai.javaparser.diagram.SequenceOutputGenerator;
import kai.javaparser.diagram.TraceFilter;
import kai.javaparser.diagram.filter.DefaultTraceFilter;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class MermaidGeneratorTest extends BaseTest {

  /**
   * 依照指定的Method，生成對應的Sequence Diagram
   * 
   * @throws IOException
   * @throws URISyntaxException
   */
  @Test
  void testGenerateMermaidForCreateList() throws IOException, URISyntaxException {
    Path resourcePath = Paths.get(PARSED_AST_DIR);
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
    Assertions.assertNotNull(output);
    Assertions.assertFalse(output.isEmpty());
    Assertions.assertTrue(output.contains("sequenceDiagram"));
    Assertions.assertTrue(output.contains("com_example_case2_LoginUser"));
  }

}