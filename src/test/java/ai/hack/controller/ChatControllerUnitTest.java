package ai.hack.controller;

import ai.hack.dto.ChatRequest;
import ai.hack.dto.ChatResponse;
import ai.hack.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatController.class)
class ChatControllerUnitTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatService chatService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String HEALTH_ENDPOINT = "/api/chat/health";
    private static final String CHAT_ENDPOINT = "/api/chat";
    private static final String CHAT_WITH_SYSTEM_ENDPOINT = "/api/chat/with-system";

    private static final String TEST_MESSAGE = "Hello, AI!";
    private static final String TEST_RESPONSE = "Hello! How can I help you?";
    private static final String TEST_SYSTEM_MESSAGE = "You are a pirate";
    private static final String PIRATE_RESPONSE = "Ahoy matey!";

    @BeforeEach
    void setUp() {
        // Reset mocks before each test
        reset(chatService);
    }

    @Test
    void shouldReturnHealthStatus_WhenHealthEndpointCalled() throws Exception {
        // Act & Assert
        mockMvc.perform(get(HEALTH_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(content().string("AI Chat Service is running!"));
    }

    @Test
    void shouldCallChatService_WhenValidChatRequestProvided() throws Exception {
        // Arrange
        ChatRequest request = new ChatRequest(TEST_MESSAGE, null);
        String requestBody = objectMapper.writeValueAsString(request);

        when(chatService.chat(TEST_MESSAGE)).thenReturn(TEST_RESPONSE);

        // Act & Assert
        mockMvc.perform(post(CHAT_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value(TEST_RESPONSE));

        // Verify chat service was called with correct message
        verify(chatService, times(1)).chat(TEST_MESSAGE);
    }

    @Test
    void shouldCallChatServiceWithSystemMessage_WhenChatWithSystemEndpointCalled() throws Exception {
        // Arrange
        ChatRequest request = new ChatRequest(TEST_MESSAGE, TEST_SYSTEM_MESSAGE);
        String requestBody = objectMapper.writeValueAsString(request);

        when(chatService.chatWithSystem(TEST_SYSTEM_MESSAGE, TEST_MESSAGE)).thenReturn(PIRATE_RESPONSE);

        // Act & Assert
        mockMvc.perform(post(CHAT_WITH_SYSTEM_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value(PIRATE_RESPONSE));

        // Verify chat service was called with correct parameters
        verify(chatService, times(1)).chatWithSystem(TEST_SYSTEM_MESSAGE, TEST_MESSAGE);
    }

    @Test
    void shouldUseDefaultSystemMessage_WhenSystemMessageIsNull() throws Exception {
        // Arrange
        ChatRequest request = new ChatRequest(TEST_MESSAGE, null);
        String requestBody = objectMapper.writeValueAsString(request);

        when(chatService.chatWithSystem("You are a helpful assistant.", TEST_MESSAGE)).thenReturn(TEST_RESPONSE);

        // Act & Assert
        mockMvc.perform(post(CHAT_WITH_SYSTEM_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value(TEST_RESPONSE));

        // Verify chat service was called with default system message
        verify(chatService, times(1)).chatWithSystem("You are a helpful assistant.", TEST_MESSAGE);
    }

    @Test
    void shouldHandleEmptyMessage_WhenEmptyStringProvided() throws Exception {
        // Arrange
        ChatRequest request = new ChatRequest("", null);
        String requestBody = objectMapper.writeValueAsString(request);

        when(chatService.chat("")).thenReturn("Response to empty message");

        // Act & Assert
        mockMvc.perform(post(CHAT_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk());

        // Verify chat service was called with empty string
        verify(chatService, times(1)).chat("");
    }

    @Test
    void shouldHandleSpecialCharacters_WhenUnicodeMessageProvided() throws Exception {
        // Arrange
        String unicodeMessage = "Hello 世界 🌍";
        ChatRequest request = new ChatRequest(unicodeMessage, null);
        String requestBody = objectMapper.writeValueAsString(request);

        when(chatService.chat(unicodeMessage)).thenReturn("Response with unicode");

        // Act & Assert
        mockMvc.perform(post(CHAT_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk());

        // Verify chat service was called with unicode message
        verify(chatService, times(1)).chat(unicodeMessage);
    }

    @Test
    void shouldHandleLongMessage_WhenMessageExceedsNormalLength() throws Exception {
        // Arrange
        String longMessage = "a".repeat(1000);
        ChatRequest request = new ChatRequest(longMessage, null);
        String requestBody = objectMapper.writeValueAsString(request);

        when(chatService.chat(longMessage)).thenReturn("Response to long message");

        // Act & Assert
        mockMvc.perform(post(CHAT_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk());

        // Verify chat service was called with long message
        verify(chatService, times(1)).chat(longMessage);
    }

    @Test
    void shouldHandleMultilineMessage_WhenMessageContainsNewlines() throws Exception {
        // Arrange
        String multilineMessage = "Line 1\nLine 2\nLine 3";
        ChatRequest request = new ChatRequest(multilineMessage, null);
        String requestBody = objectMapper.writeValueAsString(request);

        when(chatService.chat(multilineMessage)).thenReturn("Response to multiline");

        // Act & Assert
        mockMvc.perform(post(CHAT_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk());

        // Verify chat service was called with multiline message
        verify(chatService, times(1)).chat(multilineMessage);
    }


    @Test
    void shouldReturnBadRequest_WhenInvalidJsonProvided() throws Exception {
        // Arrange
        String invalidJson = "{ invalid json }";

        // Act & Assert
        mockMvc.perform(post(CHAT_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldHandleNullMessage_WhenMessageIsNull() throws Exception {
        // Arrange
        ChatRequest request = new ChatRequest(null, null);
        String requestBody = objectMapper.writeValueAsString(request);

        when(chatService.chat(null)).thenReturn("Response to null message");

        // Act & Assert
        mockMvc.perform(post(CHAT_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk());

        // Verify chat service was called with null message
        verify(chatService, times(1)).chat(null);
    }
}