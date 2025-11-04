package ai.hack.dto;

public record ChatRequest(
        String message,
        String systemMessage
) {
}
