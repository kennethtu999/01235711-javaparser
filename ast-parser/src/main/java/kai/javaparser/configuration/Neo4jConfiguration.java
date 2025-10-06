package kai.javaparser.configuration;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Config;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Neo4j 數據庫配置類
 * 負責創建和管理 Neo4j Driver 實例
 */
@Configuration
public class Neo4jConfiguration {

    @Value("${spring.neo4j.uri}")
    private String uri;

    @Value("${spring.neo4j.authentication.username}")
    private String username;

    @Value("${spring.neo4j.authentication.password}")
    private String password;

    @Value("${spring.neo4j.database}")
    private String database;

    @Value("${spring.neo4j.connection.max-connection-pool-size:50}")
    private int maxConnectionPoolSize;

    @Value("${spring.neo4j.connection.connection-acquisition-timeout:30}")
    private int connectionAcquisitionTimeout;

    @Value("${spring.neo4j.connection.max-connection-lifetime:3600}")
    private int maxConnectionLifetime;

    @Value("${spring.neo4j.connection.connection-timeout:30}")
    private int connectionTimeout;

    /**
     * 創建 Neo4j Driver Bean
     * 使用配置的連接參數創建驅動程序實例
     */
    @Bean
    @Primary
    public Driver neo4jDriver() {
        Config config = Config.builder()
                .withMaxConnectionPoolSize(maxConnectionPoolSize)
                .withConnectionAcquisitionTimeout(connectionAcquisitionTimeout, java.util.concurrent.TimeUnit.SECONDS)
                .withMaxConnectionLifetime(maxConnectionLifetime, java.util.concurrent.TimeUnit.SECONDS)
                .withConnectionTimeout(connectionTimeout, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        return GraphDatabase.driver(
                URI.create(uri),
                AuthTokens.basic(username, password),
                config);
    }

    /**
     * 創建默認的 Session 配置
     * 使用配置的數據庫名稱
     */
    @Bean
    public SessionConfig sessionConfig() {
        return SessionConfig.builder()
                .withDatabase(database)
                .build();
    }
}
