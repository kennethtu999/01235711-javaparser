package com.yourcompany.parser;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AstParserLauncherPrjTest {

    private Path currentProjectDir; // Path to the test-project subproject
    private Path testProjectRoot; // Path to the test-project subproject

    @BeforeEach
    void setUp() throws Exception {
        // Locate the test-project subproject relative to the current project
        currentProjectDir = Paths.get(".").toAbsolutePath();
        testProjectRoot = Paths.get("/Users/xxx/git/uuuu");

        assertTrue(Files.exists(testProjectRoot) && Files.isDirectory(testProjectRoot),
                "test-project directory should exist at: " + testProjectRoot.toAbsolutePath());
    }

    @AfterEach
    void tearDown() throws IOException {
        // @TempDir handles cleanup for output. test-project is a persistent subproject.
    }

    @Test
    void testParseFolder()  {
        try {
            String outputDirArg = currentProjectDir.resolve("build/parsed-ast").toAbsolutePath().toString();
            String javaComplianceLevel = "8";

            String[] args = { testProjectRoot.toString(), outputDirArg, javaComplianceLevel };

            System.out.println("Running AstParserLauncher with args: " + String.join(" ", args));
            AstParserLauncher.main(args);
        } catch (Exception e) {
            e.printStackTrace();
            fail("Failed to parse project", e);
        }
    }
}