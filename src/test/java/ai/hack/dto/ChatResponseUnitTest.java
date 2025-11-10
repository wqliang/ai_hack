package ai.hack.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatResponseUnitTest {

    @Test
    void shouldCreateChatResponseWithResponseText() {
        // Arrange
        String responseText = "Hello, this is a response!";

        // Act
        ChatResponse response = new ChatResponse(responseText);

        // Assert
        assertThat(response.response()).isEqualTo(responseText);
    }

    @Test
    void shouldCreateChatResponseWithEmptyString() {
        // Act
        ChatResponse response = new ChatResponse("");

        // Assert
        assertThat(response.response()).isEmpty();
    }

    @Test
    void shouldCreateChatResponseWithNullValue() {
        // Act
        ChatResponse response = new ChatResponse(null);

        // Assert
        assertThat(response.response()).isNull();
    }

    @Test
    void shouldHaveProperEqualsAndHashCodeImplementation() {
        // Arrange
        ChatResponse response1 = new ChatResponse("response");
        ChatResponse response2 = new ChatResponse("response");
        ChatResponse response3 = new ChatResponse("different");

        // Assert
        assertThat(response1).isEqualTo(response2);
        assertThat(response1).isNotEqualTo(response3);
        assertThat(response1.hashCode()).isEqualTo(response2.hashCode());
        assertThat(response1.hashCode()).isNotEqualTo(response3.hashCode());
    }

    @Test
    void shouldHaveProperToStringImplementation() {
        // Arrange
        ChatResponse response = new ChatResponse("response text");

        // Act
        String toStringResult = response.toString();

        // Assert
        assertThat(toStringResult).contains("response text");
    }
}