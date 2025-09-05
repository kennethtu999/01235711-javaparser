package kai.javaparser;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public class BaseTest {
  public static final String PARSED_AST_DIR = "parsed-ast";

  @BeforeEach
  public void setUp() throws Exception {
    // Locate the test-project subproject relative to the current project
    Path currentProjectDir = Paths.get("").toAbsolutePath();

    // if existed, do nothing and return
    if (Files.exists(currentProjectDir.resolve(PARSED_AST_DIR))) {
      return;
    }

    Path testProjectRoot = Paths.get(currentProjectDir.toString() + "/../test-project");

    // remove the build directory
    deleteDirectory(currentProjectDir.resolve(PARSED_AST_DIR).toFile());

    assertTrue(Files.exists(testProjectRoot) && Files.isDirectory(testProjectRoot));

    String outputDirArg = currentProjectDir.resolve(PARSED_AST_DIR).toAbsolutePath().toString();
    String javaComplianceLevel = "8";

    String[] args = { testProjectRoot.toString(), outputDirArg, javaComplianceLevel };

    System.out.println("Running AstParserLauncher with args: " + String.join(" ", args));
    AstParserLauncher.main(args);
  }

  @AfterEach
  public void tearDown() throws Exception {
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