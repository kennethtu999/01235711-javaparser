package kai.javaparser.project;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import kai.javaparser.diagram.SequenceDiagramGenerator;

public class MermaidGeneratorPrjTest {

        @BeforeEach
        public void setUpStreams() {
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
        @Disabled
        @Test
        void testGenerateMermaidForCreateList() throws IOException, URISyntaxException {
                Path resourcePath = Paths.get("build/parsed-ast");
                String methodSignature = "pagecode.cac.cacq001.CACQ001_1.initViewForm()";
                String basePackage = "pagecode";
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
                                3);

                // 為了方便除錯，可以在測試執行時將捕獲的內容印到標準錯誤流
                // System.err.println("--- Captured MermaidGenerator Output ---\n" + output);
                Files.writeString(new File("build/diagram.mermaid").toPath(), output);

        }
}