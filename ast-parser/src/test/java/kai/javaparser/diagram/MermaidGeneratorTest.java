package kai.javaparser.diagram;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MermaidGeneratorTest {

    // 用於捕獲 console 輸出的流
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;

    @BeforeEach
    public void setUpStreams() {
        // 將 System.out 重定向到我們的輸出流
        System.setOut(new PrintStream(outContent));
    }

    @AfterEach
    public void restoreStreams() {
        // 測試結束後恢復原始的 System.out
        System.setOut(originalOut);
    }

    @Test
    void testGenerateMermaidForCreateList() throws IOException, URISyntaxException {
        Path resourcePath = Paths.get("build/parsed-ast");
        String methodSignature = "pagecode.cac.cacq001.CACQ001_1.initViewForm()";
        String basePackage = "pagecode";
        String[] exclusionClassSet = {
            "org",
            "java",
            "com.ibm.tw.commons",
            "com.scsb.ewb.j2ee"};

        String[] exclusionMethodSet = {
            "getBundleString",
            "setWidth",
            "setStyleClass",
            "addHeader",
            "setColspan",
            "setAlign",
            "getDisplayMoney",
            "add"
        };

        String[] args = { 
            resourcePath.toAbsolutePath().toString(), 
            methodSignature, 
            basePackage, 
            String.join(",", exclusionClassSet),
            String.join(",", exclusionMethodSet)
        };

        
        // Act: 執行 MermaidGenerator 的 main 方法
        SequenceDiagramGenerator.main(args);

        // Assert: 驗證捕獲到的輸出內容
        String output = outContent.toString();

        // 為了方便除錯，可以在測試執行時將捕獲的內容印到標準錯誤流
        //System.err.println("--- Captured MermaidGenerator Output ---\n" + output);
        Files.writeString(new File("build/diagram.mermaid").toPath(), output);


        // assertTrue(output.contains("graph TD"), "輸出應包含 Mermaid 圖表類型宣告 'graph TD'");
        // assertTrue(output.contains("([開始: createList])"), "輸出應包含 'createList' 方法的開始節點");
        // assertTrue(output.contains("[\"宣告變數: List list = new ArrayList<>()\"]"), "輸出應包含變數宣告的節點");
        // assertTrue(output.contains("[\"呼叫方法: list.add(...)\"]"), "輸出應包含方法呼叫的節點");
        // assertTrue(output.contains("([結束])"), "輸出應包含結束節點");
        // assertTrue(output.contains("-->"), "輸出應包含表示流程的箭頭 '-->'");
    }
}