package ai.hack;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;

@TestConfiguration
public class TestConfig {

    @Bean
    @Primary
    public ChatModel testChatModel() {
        return new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                // 返回一个模拟响应
                AssistantMessage message = new AssistantMessage("Test response");
                Generation generation = new Generation(message);
                return new ChatResponse(List.of(generation));
            }
        };
    }
}
