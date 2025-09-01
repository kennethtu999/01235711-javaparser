package kai.javaparser;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Spring Boot 上下文測試
 * 驗證應用是否能正確啟動和掃描控制器
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "spring.main.web-application-type=servlet",
    "logging.level.kai.javaparser=DEBUG"
})
public class SpringBootContextTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void contextLoads() {
        // 測試 Spring 上下文是否能正確加載
        assertNotNull(restTemplate);
    }

    @Test
    void controllerIsAccessible() {
        // 測試控制器端點是否可訪問
        String url = "http://localhost:" + port + "/api/ast/health";
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        
        // 應該返回 200 OK
        assertEquals(200, response.getStatusCodeValue());
        assertTrue(response.getBody().contains("AST Parser Service is running"));
    }
}
