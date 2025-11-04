package ai.hack.service;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatService {

    private final ChatModel chatModel;

    public ChatService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public String chat(String message) {
        UserMessage userMessage = new UserMessage(message);
        Prompt prompt = new Prompt(List.of(userMessage));
        return chatModel.call(prompt).getResult().getOutput().getText();
    }

    public String chatWithSystem(String systemMessage, String userMessage) {
        SystemMessage systemMsg = new SystemMessage(systemMessage);
        UserMessage userMsg = new UserMessage(userMessage);
        Prompt prompt = new Prompt(List.of(systemMsg, userMsg));
        return chatModel.call(prompt).getResult().getOutput().getText();
    }
}
