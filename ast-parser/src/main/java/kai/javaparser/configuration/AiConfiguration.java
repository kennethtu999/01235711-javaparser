package kai.javaparser.configuration;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AiConfiguration {

    @Bean
    @Primary
    public ChatClient defaultChatClient(OllamaChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("你是一個程式碼分析助手")
                .build();
    }

    @Bean("summaryChatClient")
    public ChatClient summaryChatClient(OllamaChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("條列式摘要內容")
                .build();
    }
}
