# AI Hack - Spring AI Demo

A demo project built with Spring AI 1.0.3, demonstrating AI chat capabilities using OpenAI and Ollama.

## Technologies

- **Java**: JDK 21
- **Framework**: Spring Boot 3.3.5
- **AI Library**: Spring AI 1.0.3
- **Build Tool**: Gradle 9
- **Version Control**: Git with Git Flow

## Features

- RESTful API for AI chat interactions
- Support for both OpenAI and Ollama backends
- Customizable system prompts
- Clean architecture with service and controller layers

## Prerequisites

- JDK 21 or higher
- Gradle 9.x
- OpenAI API key (for OpenAI backend) or Ollama running locally (for Ollama backend)

## Configuration

Configure your AI backend in `src/main/resources/application.yml`:

### OpenAI
Set your API key as an environment variable:
```bash
export OPENAI_API_KEY=your-api-key-here
```

### Ollama
Ensure Ollama is running on your machine:
```bash
ollama serve
```

## Build and Run

### Build the project
```bash
./gradlew build
```

### Run the application
```bash
./gradlew bootRun
```

The application will start on `http://localhost:8080`

## API Endpoints

### Health Check
```bash
curl http://localhost:8080/api/chat/health
```

### Simple Chat
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Hello, how are you?"}'
```

### Chat with System Message
```bash
curl -X POST http://localhost:8080/api/chat/with-system \
  -H "Content-Type: application/json" \
  -d '{
    "message": "What is the capital of France?",
    "systemMessage": "You are a geography expert."
  }'
```

## Project Structure

```
ai-hack/
├── src/
│   ├── main/
│   │   ├── java/ai/hack/
│   │   │   ├── AiHackApplication.java
│   │   │   ├── controller/
│   │   │   │   └── ChatController.java
│   │   │   ├── service/
│   │   │   │   └── ChatService.java
│   │   │   └── dto/
│   │   │       ├── ChatRequest.java
│   │   │       └── ChatResponse.java
│   │   └── resources/
│   │       └── application.yml
│   └── test/
│       ├── java/ai/hack/
│       │   └── AiHackApplicationTests.java
│       └── resources/
│           └── application-test.yml
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## Git Flow Branches

- `main` - Production-ready code
- `develop` - Development branch
- `feature/*` - Feature branches
- `release/*` - Release branches
- `hotfix/*` - Hotfix branches

## License

See LICENSE file for details.
