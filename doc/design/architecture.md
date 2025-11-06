# System Architecture Documentation

## 1. Overview

This document describes the architecture of the AI Hack application, a Spring Boot-based demo application that provides RESTful APIs for AI chat interactions. The application supports both OpenAI and Ollama backends through Spring AI abstraction layer.

## 2. System Context

The AI Hack application serves as a lightweight API gateway for AI chat services, allowing clients to interact with different AI models through standardized REST endpoints. It abstracts the complexity of different AI provider implementations behind a unified interface.

## 3. Architectural Style

The application follows a layered architecture pattern with clear separation of concerns:

1. **Presentation Layer**: REST controllers handling HTTP requests
2. **Business Logic Layer**: Service classes implementing domain logic
3. **Data Transfer Layer**: DTOs for data exchange between layers
4. **Integration Layer**: Spring AI abstractions for AI model interactions

## 4. Component Diagram

### 4.1 Core Components

```
┌─────────────────────────────────────────────────────────────┐
│                    Presentation Layer                       │
├─────────────────────────────────────────────────────────────┤
│  ChatController                                             │
│  - Handles REST API endpoints                               │
│  - Delegates to ChatService                                 │
└─────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────┐
│                     Business Logic Layer                    │
├─────────────────────────────────────────────────────────────┤
│  ChatService                                                │
│  - Constructs prompts for AI models                         │
│  - Interacts with Spring AI ChatModel                       │
└─────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────┐
│                      Integration Layer                      │
├─────────────────────────────────────────────────────────────┤
│  Spring AI Abstraction                                      │
│  - ChatModel interface                                      │
│  - Prompt construction                                      │
│  - Message types (SystemMessage, UserMessage)               │
└─────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────┐
│                      AI Provider Layer                      │
├─────────────────────────────────────────────────────────────┤
│  OpenAI Implementation         Ollama Implementation        │
│  - GPT models                  - Local LLM models           │
│  - Cloud-based                 - Self-hosted                │
└─────────────────────────────────────────────────────────────┘
```

### 4.2 Data Structures

```
ChatRequest {
  message: String
  systemMessage: String (optional)
}

ChatResponse {
  response: String
}
```

## 5. Deployment Architecture

The application is designed as a standalone Spring Boot application that can be deployed in various environments:

1. **Development**: Direct execution with embedded Tomcat
2. **Production**: Containerized deployment (Docker) or traditional JAR deployment
3. **Cloud**: Deployable to cloud platforms supporting Java applications

## 6. Technology Stack

### 6.1 Backend Framework
- **Java 21**: Primary programming language
- **Spring Boot 3.3.5**: Application framework
- **Spring Web**: RESTful API implementation
- **Spring AI 1.0.3**: AI integration abstraction layer

### 6.2 AI Providers
- **OpenAI**: Cloud-based AI models (GPT series)
- **Ollama**: Local/self-hosted LLM models

### 6.3 Build Tools
- **Gradle 9**: Build automation
- **JUnit 5**: Testing framework

### 6.4 Messaging (Optional)
- **Apache RocketMQ**: Embedded messaging system for potential future enhancements

## 7. Key Design Decisions

### 7.1 AI Model Abstraction
The application uses Spring AI's `ChatModel` interface to abstract different AI providers. This allows switching between OpenAI and Ollama without changing business logic.

### 7.2 Configuration-Based Selection
AI provider selection is done through configuration properties, enabling runtime flexibility without code changes.

### 7.3 Record-Based DTOs
Using Java records for DTOs reduces boilerplate code and improves immutability.

### 7.4 Constructor Injection
Dependencies are injected through constructors, promoting testability and clear dependency management.

## 8. Data Flow

1. **Client Request**: HTTP POST to `/api/chat` or `/api/chat/with-system`
2. **Controller Processing**: `ChatController` receives request and creates `ChatRequest` object
3. **Service Delegation**: Controller delegates to `ChatService`
4. **Prompt Construction**: Service builds appropriate `Prompt` with `UserMessage` and optionally `SystemMessage`
5. **AI Processing**: `ChatModel` processes the prompt using configured AI provider
6. **Response Creation**: Service extracts text response and wraps in `ChatResponse`
7. **Client Response**: Controller returns JSON response to client

## 9. Scalability Considerations

### 9.1 Horizontal Scaling
The stateless nature of the application allows horizontal scaling behind load balancers.

### 9.2 AI Provider Limitations
Scaling is primarily limited by AI provider quotas and rate limits rather than application capacity.

### 9.3 Connection Pooling
Spring AI handles connection pooling to AI providers for efficient resource utilization.

## 10. Security Aspects

### 10.1 API Security
- No built-in authentication/authorization (demo application)
- Should be enhanced with proper security mechanisms in production

### 10.2 Configuration Security
- API keys managed through environment variables
- Sensitive configuration externalized from code

## 11. Future Extensibility

### 11.1 Additional AI Providers
Easy to add support for other AI providers through Spring AI

### 11.2 Advanced Features
- Streaming responses
- Conversation history
- Multi-modal inputs
- Fine-tuning capabilities

### 11.3 Enterprise Features
- Rate limiting
- Caching
- Monitoring and metrics
- Advanced logging