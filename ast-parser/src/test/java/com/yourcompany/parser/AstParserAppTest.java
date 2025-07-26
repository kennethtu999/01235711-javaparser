package com.yourcompany.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths; 
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourcompany.parser.model.AstNodeType; 
import com.yourcompany.parser.model.FileAstData;

public class AstParserAppTest {

    private Path currentProjectDir; // Path to the test-project subproject
    private Path testProjectRoot; // Path to the test-project subproject

    private final ObjectMapper objectMapper = new ObjectMapper();

    private List<String> testProjectSourcePaths;
    private List<String> testProjectClasspathPaths;

    @BeforeEach
    void setUp() throws Exception {
        // Locate the test-project subproject relative to the current project
        currentProjectDir = Paths.get(".").toAbsolutePath();
        testProjectRoot = currentProjectDir.getParent().getParent().resolve("test-project");

        assertTrue(Files.exists(testProjectRoot) && Files.isDirectory(testProjectRoot),
                "test-project directory should exist at: " + testProjectRoot.toAbsolutePath());

        testProjectSourcePaths = new ArrayList<>();
        testProjectClasspathPaths = new ArrayList<>();

        // 直接指定源码路径和 classpath
        Path srcMainJava = testProjectRoot.resolve("src/main/java");
        Path compiledClassesDir = testProjectRoot.resolve("build/classes/java/main");

        assertTrue(Files.exists(srcMainJava), "test-project/src/main/java should exist");
        assertTrue(Files.exists(compiledClassesDir), "test-project/build/classes/java/main should exist. Please build test-project first.");

        testProjectSourcePaths.add(srcMainJava.toAbsolutePath().toString());
        testProjectClasspathPaths.add(compiledClassesDir.toAbsolutePath().toString());

        // 如有需要，可添加 JDK 的类路径（通常不需要，除非你的 AstParserApp 需要完整 JDK 路径）
        // 例如：System.getProperty("java.home") + "/lib/rt.jar" （Java 8）
        // 但 Java 9+ 没有 rt.jar，通常不需要手动加
    }

    @AfterEach
    void tearDown() throws IOException {
        // @TempDir handles cleanup for output. test-project is a persistent subproject.
    }

    @Test
    void testParseFolder() throws IOException {
        String sourceRootsArg = String.join(",", testProjectSourcePaths);
        String outputDirArg = currentProjectDir.resolve("build/parsed-ast").toAbsolutePath().toString();
        String classpathArg = String.join(",", testProjectClasspathPaths);
        String javaComplianceLevel = "17";

        String[] args = { sourceRootsArg, outputDirArg, classpathArg, javaComplianceLevel };

        System.out.println("Running AstParserApp with args: " + String.join(" ", args));
        AstParserApp.main(args);

        // --- Assertions ---
        Optional<Path> hashCodeDir = Files.list(Path.of(outputDirArg))
                                          .filter(Files::isDirectory)
                                          .findFirst();
        assertTrue(hashCodeDir.isPresent(), "A hash code subdirectory should be created in the output directory.");

        Path actualOutputDir = hashCodeDir.get();

        Path myClassJson = actualOutputDir.resolve("com/example/test/MyClass.json");
        Path myInterfaceJson = actualOutputDir.resolve("com/example/test/MyInterface.json");

        assertTrue(Files.exists(myClassJson), "MyClass.json should be generated.");
        assertTrue(Files.exists(myInterfaceJson), "MyInterface.json should be generated.");

        FileAstData myClassData = objectMapper.readValue(myClassJson.toFile(), FileAstData.class);
        assertNotNull(myClassData, "Parsed MyClass JSON should not be null.");
        assertEquals("com.example.test", myClassData.getPackageName(), "Package name for MyClass should match.");
        assertTrue(myClassData.getImports().contains("java.util.List"), "MyClass should import java.util.List.");
        assertTrue(myClassData.getImports().contains("java.util.ArrayList"), "MyClass should import java.util.ArrayList.");

        boolean foundNameField = myClassData.getCompilationUnitNode().getChildren().stream()
            .filter(node -> node.getType() == AstNodeType.TYPE_DECLARATION && "MyClass".equals(node.getName()))
            .flatMap(typeNode -> typeNode.getChildren().stream())
            .filter(node -> node.getType() == AstNodeType.FIELD_DECLARATION && "name".equals(node.getChildren().get(1).getName()))
            .anyMatch(fieldNode -> "java.lang.String".equals(fieldNode.getChildren().get(0).getFullyQualifiedName()));
        assertTrue(foundNameField, "MyClass should have a 'name' field of type String with resolved FQN.");

        boolean foundCreateList = myClassData.getCompilationUnitNode().getChildren().stream()
            .filter(node -> node.getType() == AstNodeType.TYPE_DECLARATION && "MyClass".equals(node.getName()))
            .flatMap(typeNode -> typeNode.getChildren().stream())
            .filter(node -> node.getType() == AstNodeType.METHOD_DECLARATION && "createList".equals(node.getName()))
            .anyMatch(methodNode -> methodNode.getFullyQualifiedName() != null &&
                                     methodNode.getFullyQualifiedName().contains("createList(java.lang.String)"));
        assertTrue(foundCreateList, "MyClass should have createList method with correct FQN.");
    }
}