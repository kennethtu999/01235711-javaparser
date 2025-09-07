package kai.javaparser.configuration;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.annotation.JsonInclude;

@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> builder.indentOutput(true)
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .serializationInclusion(JsonInclude.Include.NON_EMPTY)
                .serializationInclusion(JsonInclude.Include.NON_ABSENT)
                .serializationInclusion(JsonInclude.Include.NON_DEFAULT);
    }

}