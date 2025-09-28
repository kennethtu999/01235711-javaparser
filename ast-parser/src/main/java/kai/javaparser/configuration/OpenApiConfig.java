package kai.javaparser.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI (Swagger) 配置類
 * 配置API文檔的基本信息和服務器設定
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Java AST Parser API")
                        .description("提供Java代碼AST解析、序列圖生成和代碼提取功能的REST API服務")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("AST Parser Team")
                                .email("support@astparser.com")
                                .url("https://github.com/astparser"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("本地開發環境"),
                        new Server()
                                .url("https://api.astparser.com")
                                .description("生產環境")));
    }
}
