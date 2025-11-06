package ai.hack.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatModel chatModel;

    @InjectMocks
    private ChatService chatService;

    /**
     * Helper method to mock ChatModel response chain
     */
    private void mockChatModelResponse(String responseText) {
        ChatResponse mockChatResponse = mock(ChatResponse.class);
        Generation mockGeneration = mock(Generation.class);
        AssistantMessage mockMessage = mock(AssistantMessage.class);

        when(mockMessage.getText()).thenReturn(responseText);
        when(mockGeneration.getOutput()).thenReturn(mockMessage);
        when(mockChatResponse.getResult()).thenReturn(mockGeneration);
        when(chatModel.call(any(Prompt.class))).thenReturn(mockChatResponse);
    }

    // Test constants
    private static final String SIMPLE_MESSAGE = "Hello, AI!";
    private static final String SIMPLE_RESPONSE = "Hello! How can I help you?";
    private static final String PIRATE_SYSTEM = "You are a pirate";
    private static final String PIRATE_RESPONSE = "Ahoy matey!";

    @Test
    void shouldReturnResponseText_WhenValidMessageProvided() {
        // Arrange
        mockChatModelResponse(SIMPLE_RESPONSE);

        // Act
        String result = chatService.chat(SIMPLE_MESSAGE);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(SIMPLE_RESPONSE);
        verify(chatModel, times(1)).call(any(Prompt.class));
    }

    @Test
    void shouldConstructUserMessageCorrectly_WhenChatCalled() {
        // Arrange
        mockChatModelResponse("Mock response");
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);

        // Act
        chatService.chat("Test message");

        // Assert
        verify(chatModel).call(promptCaptor.capture());
        Prompt capturedPrompt = promptCaptor.getValue();
        List<Message> messages = capturedPrompt.getInstructions();

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(0).getText()).isEqualTo("Test message");
    }

    @Test
    void shouldReturnResponseText_WhenSystemAndUserMessagesProvided() {
        // Arrange
        mockChatModelResponse(PIRATE_RESPONSE);

        // Act
        String result = chatService.chatWithSystem(PIRATE_SYSTEM, "Hello");

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(PIRATE_RESPONSE);
        verify(chatModel, times(1)).call(any(Prompt.class));
    }

    @Test
    void shouldConstructBothMessagesCorrectly_WhenChatWithSystemCalled() {
        // Arrange
        mockChatModelResponse("Mock response");
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);

        // Act
        chatService.chatWithSystem("System prompt", "User message");

        // Assert
        verify(chatModel).call(promptCaptor.capture());
        List<Message> messages = promptCaptor.getValue().getInstructions();

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(messages.get(0).getText()).isEqualTo("System prompt");
        assertThat(messages.get(1)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(1).getText()).isEqualTo("User message");
    }

    @Test
    void shouldHandleLongMessage_WhenMessageExceeds1000Characters() {
        // Arrange
        String longMessage = "a".repeat(1000);
        mockChatModelResponse("Response to long message");
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);

        // Act
        String result = chatService.chat(longMessage);

        // Assert
        assertThat(result).isNotNull();
        verify(chatModel).call(promptCaptor.capture());
        assertThat(promptCaptor.getValue().getInstructions().get(0).getText())
                .hasSize(1000);
    }

    @Test
    void shouldPreserveSpecialCharacters_WhenMessageContainsUnicode() {
        // Arrange
        String unicodeMessage = "Hello 世界 🌍";
        mockChatModelResponse("Response with unicode");
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);

        // Act
        chatService.chat(unicodeMessage);

        // Assert
        verify(chatModel).call(promptCaptor.capture());
        assertThat(promptCaptor.getValue().getInstructions().get(0).getText())
                .isEqualTo(unicodeMessage);
    }

    @Test
    void shouldPreserveMultilineText_WhenMessageContainsNewlines() {
        // Arrange
        String multilineMessage = "Line 1\nLine 2\nLine 3";
        mockChatModelResponse("Response to multiline");
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);

        // Act
        chatService.chat(multilineMessage);

        // Assert
        verify(chatModel).call(promptCaptor.capture());
        assertThat(promptCaptor.getValue().getInstructions().get(0).getText())
                .isEqualTo(multilineMessage);
    }

    @Test
    void shouldHandleEmptyMessage_WhenEmptyStringProvided() {
        // Arrange
        mockChatModelResponse("Response to empty");

        // Act
        String result = chatService.chat("");

        // Assert
        assertThat(result).isNotNull();
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    void shouldHandleNullMessage_WhenNullProvided() {
        // This test documents current behavior - Spring AI UserMessage rejects null
        // IllegalArgumentException is thrown by UserMessage constructor

        // Act & Assert
        assertThatThrownBy(() -> chatService.chat(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldPropagateException_WhenChatModelThrowsRuntimeException() {
        // Arrange
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(new RuntimeException("AI model error"));

        // Act & Assert
        assertThatThrownBy(() -> chatService.chat("Test message"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("AI model error");

        verify(chatModel).call(any(Prompt.class));
    }
}