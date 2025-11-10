package ai.hack.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatRequestUnitTest {

    @Test
    void shouldCreateChatRequestWithMessageOnly() {
        // Arrange
        String message = "Hello, world!";

        // Act
        ChatRequest request = new ChatRequest(message, null);

        // Assert
        assertThat(request.message()).isEqualTo(message);
        assertThat(request.systemMessage()).isNull();
    }

    @Test
    void shouldCreateChatRequestWithBothMessageAndSystemMessage() {
        // Arrange
        String message = "Hello, world!";
        String systemMessage = "You are a helpful assistant.";

        // Act
        ChatRequest request = new ChatRequest(message, systemMessage);

        // Assert
        assertThat(request.message()).isEqualTo(message);
        assertThat(request.systemMessage()).isEqualTo(systemMessage);
    }

    @Test
    void shouldCreateChatRequestWithEmptyStrings() {
        // Act
        ChatRequest request = new ChatRequest("", "");

        // Assert
        assertThat(request.message()).isEmpty();
        assertThat(request.systemMessage()).isEmpty();
    }

    @Test
    void shouldCreateChatRequestWithNullValues() {
        // Act
        ChatRequest request = new ChatRequest(null, null);

        // Assert
        assertThat(request.message()).isNull();
        assertThat(request.systemMessage()).isNull();
    }

    @Test
    void shouldHaveProperEqualsAndHashCodeImplementation() {
        // Arrange
        ChatRequest request1 = new ChatRequest("message", "system");
        ChatRequest request2 = new ChatRequest("message", "system");
        ChatRequest request3 = new ChatRequest("different", "system");

        // Assert
        assertThat(request1).isEqualTo(request2);
        assertThat(request1).isNotEqualTo(request3);
        assertThat(request1.hashCode()).isEqualTo(request2.hashCode());
        assertThat(request1.hashCode()).isNotEqualTo(request3.hashCode());
    }

    @Test
    void shouldHaveProperToStringImplementation() {
        // Arrange
        ChatRequest request = new ChatRequest("message", "system");

        // Act
        String toStringResult = request.toString();

        // Assert
        assertThat(toStringResult).contains("message");
        assertThat(toStringResult).contains("system");
    }
}