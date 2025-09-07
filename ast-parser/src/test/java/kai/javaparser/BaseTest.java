package kai.javaparser;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import kai.javaparser.repository.FileSystemAstRepository;
import kai.javaparser.service.AstParserService;
import kai.javaparser.service.TaskManagementService;

public class BaseTest {

  @Value("${app.astDir}")
  protected String astDir;

  @Autowired
  private FileSystemAstRepository repository;

  @Autowired
  private TaskManagementService taskManagementService;

  // 使用靜態變量確保AST只被解析一次
  private static final AtomicBoolean astParsed = new AtomicBoolean(false);
  private static final Object parseLock = new Object();

  @BeforeEach
  public void setUp() throws Exception {
    // 使用同步機制確保AST只被解析一次
    if (!astParsed.get()) {
      synchronized (parseLock) {
        if (!astParsed.get()) {
          setupAstParsing();
          astParsed.set(true);
        }
      }
    }

    // 清理可能影響測試的共享狀態
    cleanupSharedState();
  }

  private void setupAstParsing() throws Exception {
    // Locate the test-project subproject relative to the current project
    Path currentProjectDir = Paths.get("").toAbsolutePath();

    // 清理並重新創建AST目錄
    if (Files.exists(currentProjectDir.resolve(astDir))) {
      deleteDirectory(currentProjectDir.resolve(astDir).toFile());
    }

    Path testProjectRoot = Paths.get(currentProjectDir.toString() + "/../test-project");

    assertTrue(Files.exists(testProjectRoot) && Files.isDirectory(testProjectRoot));

    String outputDirArg = currentProjectDir.resolve(astDir).toAbsolutePath().toString();

    System.out.println("Running AstParserService for test project: " + testProjectRoot);

    // 建立測試用的 repository
    repository.initialize(Paths.get(outputDirArg));
    AstParserService astParserService = new AstParserService(repository);
    String result = astParserService.parseSourceDirectory(testProjectRoot.toString(), outputDirArg);
    System.out.println("AST parsing result: " + result);
  }

  @AfterEach
  public void tearDown() throws Exception {
    // 清理測試後的狀態
    cleanupSharedState();
  }

  /**
   * 清理可能影響測試的共享狀態
   */
  private void cleanupSharedState() {
    try {
      // 清理任務管理服務中的任務
      if (taskManagementService != null) {
        taskManagementService.cleanupExpiredTasks(0); // 清理所有任務
      }

      // 清理AST緩存
      if (repository != null) {
        repository.clearCache();
      }
    } catch (Exception e) {
      System.err.println("清理共享狀態時發生錯誤: " + e.getMessage());
    }
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