package kai.javaparser.case3;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import kai.javaparser.controller.AstParserController.CodeExtractionRequest;

/**
 * 案例 #3 API 測試：測試代碼提取的 REST API 端點
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CodeExtractorApiTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    /**
     * 測試代碼提取 API 端點
     */
    @Test
    void testExtractCodeApi() throws Exception {
        // Arrange: 準備請求
        CodeExtractionRequest request = new CodeExtractionRequest();
        request.setEntryPointMethodFqn("com.example.case2.LoginUser.getLevel1()");
        request.setAstDir("build/parsed-ast");
        request.setBasePackage("com.example");
        request.setMaxDepth(2);
        request.setIncludeImports(true);
        request.setIncludeComments(true);

        // 設置 HTTP 標頭
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<CodeExtractionRequest> entity = new HttpEntity<>(request, headers);

        // Act: 發送請求
        String url = "http://localhost:" + port + "/api/ast/extract-code";
        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                String.class);

        // Assert: 驗證回應
        assertEquals(200, response.getStatusCode().value(), "API 應返回 200 OK");
        assertNotNull(response.getBody(), "回應內容不應為 null");

        String responseBody = response.getBody();
        assertTrue(responseBody.contains("entryPointMethodFqn"), "回應應包含進入點方法");
        assertTrue(responseBody.contains("involvedClasses"), "回應應包含涉及的類別");
        assertTrue(responseBody.contains("mergedSourceCode"), "回應應包含合併後的原始碼");
        assertTrue(responseBody.contains("totalClasses"), "回應應包含總類別數");
        assertTrue(responseBody.contains("totalLines"), "回應應包含總行數");

        System.out.println("=== API 測試回應 ===");
        System.out.println(responseBody);
    }

    /**
     * 測試無效請求的錯誤處理
     */
    @Test
    void testExtractCodeApiWithInvalidRequest() throws Exception {
        // Arrange: 準備無效請求
        CodeExtractionRequest request = new CodeExtractionRequest();
        request.setEntryPointMethodFqn("invalid.method()");
        request.setAstDir("/nonexistent/path");
        request.setBasePackage("com.example");
        request.setMaxDepth(1);
        request.setIncludeImports(true);
        request.setIncludeComments(true);

        // 設置 HTTP 標頭
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<CodeExtractionRequest> entity = new HttpEntity<>(request, headers);

        // Act: 發送請求
        String url = "http://localhost:" + port + "/api/ast/extract-code";
        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                String.class);

        // Assert: 驗證錯誤回應
        assertEquals(200, response.getStatusCode().value(), "無效請求應返回 200 OK 但包含錯誤訊息");
        assertNotNull(response.getBody(), "錯誤回應內容不應為 null");

        String responseBody = response.getBody();
        assertTrue(responseBody.contains("errorMessage"), "錯誤回應應包含錯誤訊息");

        System.out.println("=== API 錯誤測試回應 ===");
        System.out.println(responseBody);
    }
}
