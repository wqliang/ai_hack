package ai.hack.controller;

import ai.hack.dto.ChatRequest;
import ai.hack.dto.ChatResponse;
import ai.hack.service.ChatService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String response = chatService.chat(request.message());
        return new ChatResponse(response);
    }

    @PostMapping("/with-system")
    public ChatResponse chatWithSystem(@RequestBody ChatRequest request) {
        String response = chatService.chatWithSystem(
                request.systemMessage() != null ? request.systemMessage() : "You are a helpful assistant.",
                request.message()
        );
        return new ChatResponse(response);
    }

    @GetMapping("/health")
    public String health() {
        return "AI Chat Service is running!";
    }
}
