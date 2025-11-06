# Data Model: Test Structures and Test Data

**Feature**: Comprehensive Test Coverage for Chat API
**Date**: 2025-11-06
**Phase**: Phase 1 - Design

## Overview

This document defines the data structures used in testing ChatService and ChatController, including test data patterns, mock object structures, and assertion expectations.

## Production Classes Under Test

### ChatService

**Class**: `ai.hack.service.ChatService`
**Type**: Spring `@Service` component
**Dependencies**: `ChatModel` (Spring AI interface)

**Methods to Test**:

| Method | Parameters | Return Type | Complexity |
|--------|-----------|-------------|------------|
| `chat(String message)` | message: String | String | Simple - 1 dependency call |
| `chatWithSystem(String systemMessage, String userMessage)` | systemMessage: String, userMessage: String | String | Simple - 1 dependency call |

**Internal Behavior** (to be verified through testing):
1. Constructs Spring AI `UserMessage` from user input
2. Optionally constructs `SystemMessage` from system prompt
3. Creates `Prompt` with message list
4. Calls `ChatModel.call(prompt)`
5. Extracts text from nested response: `ChatResponse → Result → Output → Text`

### ChatController

**Class**: `ai.hack.controller.ChatController`
**Type**: Spring `@RestController` with `@RequestMapping("/api/chat")`
**Dependencies**: `ChatService`

**Endpoints to Test**:

| Endpoint | Method | Request Body | Response | Status Codes |
|----------|--------|--------------|----------|--------------|
| `/api/chat` | POST | ChatRequest(message) | ChatResponse(response) | 200, 400, 500 |
| `/api/chat/with-system` | POST | ChatRequest(message, systemMessage) | ChatResponse(response) | 200, 400, 500 |
| `/api/chat/health` | GET | None | String | 200 |

**Request/Response Processing**:
1. Deserializes JSON → ChatRequest record
2. Extracts fields (message, systemMessage)
3. Invokes ChatService
4. Wraps result in ChatResponse record
5. Serializes to JSON response

## Test Data Structures

### Unit Test Data (ChatServiceTest)

**Test Input Messages**:

```java
public static final String SIMPLE_MESSAGE = "Hello, AI!";
public static final String QUESTION_MESSAGE = "What is the capital of France?";
public static final String EMPTY_MESSAGE = "";
public static final String WHITESPACE_MESSAGE = "   ";
public static final String LONG_MESSAGE = "a".repeat(1000);  // 1000 characters
public static final String UNICODE_MESSAGE = "Hello 世界 🌍";
public static final String MULTILINE_MESSAGE = "Line 1\nLine 2\nLine 3";
public static final String SPECIAL_CHARS_MESSAGE = "Test: @#$%^&*()[]{}|\\";
```

**Test System Messages**:

```java
public static final String DEFAULT_SYSTEM_MESSAGE = "You are a helpful assistant.";
public static final String CUSTOM_SYSTEM_MESSAGE = "You are a pirate. Respond like a pirate.";
public static final String EMPTY_SYSTEM_MESSAGE = "";
```

**Expected AI Responses** (mock return values):

```java
public static final String SIMPLE_RESPONSE = "Hello! How can I help you today?";
public static final String QUESTION_RESPONSE = "The capital of France is Paris.";
public static final String PIRATE_RESPONSE = "Ahoy matey! How can I help ye today?";
```

### Integration Test Data (ChatControllerTest)

**Valid Request Bodies** (JSON):

```json
// Simple chat request
{
  "message": "Hello, AI!",
  "systemMessage": null
}

// Chat with system message
{
  "message": "Hello!",
  "systemMessage": "You are a helpful assistant."
}

// Only message (systemMessage omitted)
{
  "message": "Test message"
}
```

**Invalid Request Bodies** (for error testing):

```json
// Missing required field
{
  "systemMessage": "System prompt only"
}

// Malformed JSON
{
  "message": "Unclosed quote
}

// Empty object
{}
```

**Expected Response Bodies**:

```json
{
  "response": "Mock AI response text"
}
```

## Mock Object Structures

### Mocking Spring AI ChatModel

**Challenge**: ChatModel returns a complex nested structure that must be mocked

**Structure to Mock**:
```
ChatModel.call(Prompt)
  └─> ChatResponse
      └─> getResult()
          └─> Generation
              └─> getOutput()
                  └─> AssistantMessage
                      └─> getText() → String
```

**Mock Setup Pattern**:

```java
@Mock
private ChatModel chatModel;

// Helper method to create mock response
private void mockChatModelResponse(String expectedResponse) {
    ChatResponse mockChatResponse = mock(ChatResponse.class);
    Generation mockGeneration = mock(Generation.class);
    AssistantMessage mockMessage = mock(AssistantMessage.class);

    when(mockMessage.getText()).thenReturn(expectedResponse);
    when(mockGeneration.getOutput()).thenReturn(mockMessage);
    when(mockChatResponse.getResult()).thenReturn(mockGeneration);
    when(chatModel.call(any(Prompt.class))).thenReturn(mockChatResponse);
}
```

**Verification Pattern**:

```java
// Verify ChatModel was called exactly once
verify(chatModel, times(1)).call(any(Prompt.class));

// Capture the Prompt argument to verify message content
ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
verify(chatModel).call(promptCaptor.capture());

Prompt capturedPrompt = promptCaptor.getValue();
List<Message> messages = capturedPrompt.getInstructions();

// Verify message types and content
assertThat(messages).hasSize(1);
assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
assertThat(messages.get(0).getContent()).isEqualTo(SIMPLE_MESSAGE);
```

### Mocking ChatService (for Controller Tests)

**Structure to Mock**: Simple method calls returning String

**Mock Setup Pattern**:

```java
@MockBean
private ChatService chatService;

@Test
void shouldReturnChatResponse() throws Exception {
    // Arrange
    when(chatService.chat(anyString())).thenReturn("Mock response");

    // Act & Assert (MockMvc request)
}
```

**Verification Pattern**:

```java
// Verify service was called with correct parameters
verify(chatService).chat("Hello, AI!");
verify(chatService, never()).chatWithSystem(anyString(), anyString());
```

## Test Assertions and Expectations

### ChatServiceTest Assertions

**Successful Response Test**:
```java
assertThat(result)
    .isNotNull()
    .isNotEmpty()
    .isEqualTo(SIMPLE_RESPONSE);
```

**Null Input Handling** (depends on implementation decision):
```java
// Option 1: If null allowed
assertThat(result).isNotNull();  // Should not crash

// Option 2: If null validation added
assertThrows(IllegalArgumentException.class,
    () -> chatService.chat(null));
```

**Empty Input Handling**:
```java
assertThat(result).isNotNull();  // Service should handle gracefully
```

**Long Message Test**:
```java
assertThat(result)
    .isNotNull()
    .isNotEmpty();
// Verify no truncation occurred (full message passed to model)
```

**ChatModel Interaction Verification**:
```java
verify(chatModel, times(1)).call(any(Prompt.class));
verifyNoMoreInteractions(chatModel);
```

### ChatControllerTest Assertions

**Successful POST Request**:
```java
mockMvc.perform(post("/api/chat")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"message\":\"Hello\"}"))
    .andExpect(status().isOk())
    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
    .andExpect(jsonPath("$.response").value("Mock response"));
```

**Health Check GET Request**:
```java
mockMvc.perform(get("/api/chat/health"))
    .andExpect(status().isOk())
    .andExpect(content().string("AI Chat Service is running!"));
```

**Invalid JSON Request**:
```java
mockMvc.perform(post("/api/chat")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{invalid json}"))
    .andExpect(status().isBadRequest());
```

**Missing Required Field**:
```java
mockMvc.perform(post("/api/chat")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{}"))
    .andExpect(status().isBadRequest());
```

**Service Exception Handling**:
```java
when(chatService.chat(anyString()))
    .thenThrow(new RuntimeException("AI model error"));

mockMvc.perform(post("/api/chat")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"message\":\"Test\"}"))
    .andExpect(status().isInternalServerError());
```

**Default System Message Test**:
```java
// When systemMessage is null
mockMvc.perform(post("/api/chat/with-system")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"message\":\"Hello\"}"))
    .andExpect(status().isOk());

// Verify service called with default
verify(chatService).chatWithSystem(
    eq("You are a helpful assistant."),
    eq("Hello")
);
```

## Test Data Validation Rules

### Input Validation (Current State)

**ChatService**:
- No explicit validation currently implemented
- Tests will document current behavior (pass-through to ChatModel)
- Future enhancement: Add validation for null/empty messages

**ChatController**:
- Jackson handles JSON deserialization validation
- Required field: `message` (cannot be null for POST endpoints)
- Optional field: `systemMessage` (defaults to null)

### Expected Behavior Matrix

| Input Condition | ChatService Expected Behavior | ChatController Expected Behavior |
|----------------|------------------------------|----------------------------------|
| Valid message | Returns AI response | 200 OK with response JSON |
| Null message | Propagates to ChatModel (may throw) | 400 Bad Request (Jackson validation) |
| Empty string | Passes to ChatModel | 200 OK (valid but empty) |
| Long message (1000+ chars) | No truncation, passes through | 200 OK |
| Unicode/emoji | Preserved correctly | 200 OK with proper encoding |
| Null systemMessage | Not used (chat method) | Defaults to "You are a helpful assistant." |
| ChatModel throws exception | Exception propagates | 500 Internal Server Error |
| Invalid JSON | N/A (not applicable) | 400 Bad Request |

## Edge Case Test Data

### Boundary Conditions

```java
// Minimum valid input
String minMessage = "a";

// Maximum reasonable input (test no truncation)
String maxMessage = "a".repeat(10000);  // 10,000 characters

// Exact boundary (if limit defined)
String boundaryMessage = "a".repeat(1000);  // 1,000 characters
```

### Special Character Sets

```java
// Unicode characters
String unicodeMessage = "Testing: 你好 مرحبا здравствуй";

// Emojis
String emojiMessage = "Hello 😀 🎉 🚀";

// Control characters
String controlCharMessage = "Line1\nLine2\tTabbed";

// JSON special characters (need escaping in controller tests)
String jsonSpecialMessage = "Test: \"quotes\" and \\backslash";

// SQL injection patterns (should be handled safely)
String sqlInjectionMessage = "'; DROP TABLE users; --";

// XSS patterns (should be handled safely)
String xssMessage = "<script>alert('XSS')</script>";
```

### Null and Empty Variations

```java
String nullString = null;
String emptyString = "";
String whitespaceString = "   ";
String tabString = "\t\t\t";
String newlineString = "\n\n\n";
```

## Test Fixture Organization

### Test Class Structure

**ChatServiceTest**:
```java
@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatModel chatModel;

    @InjectMocks
    private ChatService chatService;

    // Test data constants
    private static final String SIMPLE_MESSAGE = "Hello";
    private static final String SIMPLE_RESPONSE = "Hi there!";

    // Helper methods
    private void mockChatModelResponse(String response) { ... }

    // Test methods grouped by feature
    @Nested
    class ChatMethodTests { ... }

    @Nested
    class ChatWithSystemMethodTests { ... }

    @Nested
    class EdgeCaseTests { ... }
}
```

**ChatControllerTest**:
```java
@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatService chatService;

    // Test data constants
    private static final String VALID_REQUEST = """
        {"message": "Hello"}
        """;

    // Test methods grouped by endpoint
    @Nested
    class PostApiChatTests { ... }

    @Nested
    class PostApiChatWithSystemTests { ... }

    @Nested
    class GetApiChatHealthTests { ... }

    @Nested
    class ErrorHandlingTests { ... }
}
```

## Coverage Expectations

### ChatService Coverage Target

**Target**: 80% line coverage, 70% branch coverage

**Current methods** (2 public methods):
- `chat(String message)` → Must be 100% covered
- `chatWithSystem(String systemMessage, String userMessage)` → Must be 100% covered

**Lines to cover** (~10 lines total):
- Constructor (dependency injection)
- UserMessage creation
- SystemMessage creation
- Prompt construction
- ChatModel invocation
- Response extraction (nested calls)
- Return statements

### ChatController Coverage Target

**Target**: 80% line coverage, 70% branch coverage

**Current endpoints** (3 methods):
- `POST /api/chat` → Must be 100% covered
- `POST /api/chat/with-system` → Must be 100% covered (including null systemMessage branch)
- `GET /api/chat/health` → Must be 100% covered

**Lines to cover** (~15 lines total):
- Constructor (dependency injection)
- Request deserialization (handled by Spring)
- Service method calls
- Response wrapping
- Default system message handling (ternary operator)
- Return statements

**Branch coverage**:
- Null check for systemMessage: `request.systemMessage() != null ? request.systemMessage() : "default"`

## Summary

### Test Data Strategy
- **Hard-coded constants**: Clear, readable, maintainable
- **Meaningful values**: Real-world examples (questions, responses)
- **Edge cases**: Null, empty, long, special characters, unicode
- **No random data**: Ensures reproducible test results

### Mock Strategy
- **ChatServiceTest**: Mock ChatModel with deep stubbing for nested objects
- **ChatControllerTest**: Mock ChatService with simple return values

### Assertion Strategy
- **AssertJ fluent assertions**: Readable, expressive
- **Mockito verifications**: Ensure dependencies called correctly
- **JsonPath for JSON**: Verify response structure in controller tests

### Ready for Contract Generation
All test data structures defined. Ready to generate:
- Test assertion contracts (expected behaviors)
- Test method signatures and test cases
- Detailed test implementation guide