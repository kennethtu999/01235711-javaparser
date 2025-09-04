package kai.javaparser.case9;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import kai.javaparser.diagram.SequenceOutputConfig;
import kai.javaparser.diagram.SequenceOutputGenerator;
import kai.javaparser.diagram.TraceFilter;
import kai.javaparser.diagram.filter.DefaultTraceFilter;

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
        @Test
        void testGenerateMermaidForCreateList() throws IOException, URISyntaxException {
                if (!Case9Const.RUNNABLE) {
                        return;
                }

                Path resourcePath = Paths.get("build/parsed-ast");
                String methodSignature = "pagecode.cac.cacq001.CACQ001_1.initViewForm()";
                String basePackage = "pagecode";

                // Set<String> exclusionClassSet = new HashSet<>(Arrays.asList(
                // "org",
                // "java",
                // "com.ibm.tw.commons",
                // "java.util.logging.Logger"));

                // Set<String> exclusionMethodSet = new HashSet<>(Arrays.asList(
                // "getBundleString",
                // "setWidth",
                // "setStyleClass",
                // "addHeader",
                // "setColspan",
                // "setAlign",
                // "getDisplayMoney",
                // "add",
                // "addRecord"));

                Set<String> exclusionClassSet = new HashSet<>(Arrays.asList(
                                "java",
                                "org",
                                "com.ibm.tw.commons.exception"));

                Set<String> exclusionMethodSet = new HashSet<>(Arrays.asList(
                                "getBundleString"));

                TraceFilter filter = new DefaultTraceFilter(exclusionClassSet, exclusionMethodSet);

                SequenceOutputConfig config = SequenceOutputConfig.builder()
                                .depth(10)
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

        }
}