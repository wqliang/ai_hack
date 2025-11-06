# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Spring AI demo application (Java 21, Spring Boot 3.3.5, Spring AI 1.0.3) with RESTful API for AI chat interactions supporting both OpenAI and Ollama backends.

## Build & Development Commands

### Build
```bash
./gradlew build
```

### Run Application
```bash
./gradlew bootRun
```
Application runs on http://localhost:8080

### Run Tests
```bash
./gradlew test
```

### Run Single Test
```bash
./gradlew test --tests "ai.hack.AiHackApplicationTests"
```

### Clean Build
```bash
./gradlew clean build
```

## Architecture

### Package Structure
- `ai.hack.controller` - REST endpoints (ChatController)
- `ai.hack.service` - Business logic (ChatService)
- `ai.hack.dto` - Request/Response objects (Java records)
- `ai.hack.test` - Test utilities (EmbeddedRocketMQBroker)

### Spring AI Integration
The application uses Spring AI's `ChatModel` interface, which can be backed by:
- **OpenAI**: Configured via `spring.ai.openai.*` properties
- **Ollama**: Configured via `spring.ai.ollama.*` properties

Spring Boot auto-configuration determines which implementation to inject based on available dependencies and configuration. The `ChatService` is implementation-agnostic, accepting any `ChatModel` bean.

### Message Flow
1. REST request hits `ChatController`
2. Controller delegates to `ChatService`
3. `ChatService` constructs Spring AI `Prompt` with `SystemMessage` and/or `UserMessage`
4. `ChatModel` (OpenAI/Ollama) processes the prompt
5. Response text extracted and returned as `ChatResponse`

## Configuration

### Environment Variables
- `OPENAI_API_KEY` - Required for OpenAI backend
- `OLLAMA_BASE_URL` - Ollama server URL (default: http://localhost:11434)
- `OLLAMA_MODEL` - Ollama model to use (default: llama3.2)

### Application Profiles
- Default profile: `application.yml` - Production configuration
- Test profile: `application-test.yml` - Uses test API key for OpenAI

## Git Workflow

This project follows Git Flow:
- `main` - Production-ready code
- `develop` - Integration branch
- `feature/*` - Feature branches (branch from develop, merge to develop)
- `release/*` - Release preparation (branch from develop, merge to main and develop)
- `hotfix/*` - Production fixes (branch from main, merge to main and develop)

### Commit Convention
Use conventional commits:
- `feat:` - New feature
- `fix:` - Bug fix
- `docs:` - Documentation
- `refactor:` - Code refactoring
- `test:` - Test changes
- `chore:` - Maintenance

### Merging
Always use `--no-ff` flag for merges to maintain branch history.

## API Endpoints

- `GET /api/chat/health` - Health check
- `POST /api/chat` - Simple chat (requires `message` in request body)
- `POST /api/chat/with-system` - Chat with custom system prompt (requires `message` and optional `systemMessage`)

## Additional Components

### Embedded RocketMQ Broker
Located at `src/main/java/ai/hack/test/EmbeddedRocketMQBroker.java`, this utility provides a minimal embedded RocketMQ broker for testing purposes. It configures both NameServer and Broker with resource-minimized settings suitable for development environments.

## Active Technologies
- Java 21 (existing project standard) + Spring Boot 3.3.5, Spring AI 1.0.3, JUnit 5, Mockito, MockMvc, AssertJ (001-chat-service-controller-tests)
- N/A (testing feature, no new storage requirements) (001-chat-service-controller-tests)

## Recent Changes
- 001-chat-service-controller-tests: Added Java 21 (existing project standard) + Spring Boot 3.3.5, Spring AI 1.0.3, JUnit 5, Mockito, MockMvc, AssertJ
