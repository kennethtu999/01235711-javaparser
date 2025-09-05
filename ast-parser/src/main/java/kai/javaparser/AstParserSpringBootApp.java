package kai.javaparser;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Spring Boot主應用類
 * 用於啟動AST解析器的Web服務
 */
@SpringBootApplication
@EnableAsync
public class AstParserSpringBootApp {

    public static void main(String[] args) {
        SpringApplication.run(AstParserSpringBootApp.class, args);
    }
}
