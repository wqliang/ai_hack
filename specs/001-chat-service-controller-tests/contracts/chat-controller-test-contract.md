# Test Contract: ChatControllerTest

**Class Under Test**: `ai.hack.controller.ChatController`
**Test Class**: `ai.hack.controller.ChatControllerTest`
**Testing Approach**: Integration testing with MockMvc and mocked service layer
**Coverage Target**: 100% (simple controller with 3 endpoints)

## Test Suite Structure

```java
@WebMvcTest(ChatController.class)
class ChatControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private ChatService chatService;

    // Test methods...
}
```

## Test Methods Contract

### Test Group 1: POST /api/chat Endpoint

#### Test 1.1: shouldReturnChatResponse_WhenValidMessageProvided

**Purpose**: Verify POST /api/chat returns 200 OK with correct JSON response

**Preconditions**:
- ChatService mock configured to return "Mock AI response"

**Test Steps**:
1. Arrange: Mock chatService.chat() to return "Mock AI response"
2. Act: POST to /api/chat with JSON body: `{"message": "Hello"}`
3. Assert: HTTP 200, Content-Type application/json, response.response = "Mock AI response"

**Expected Result**: 200 OK with ChatResponse JSON

**Assertions**:
```java
when(chatService.chat("Hello")).thenReturn("Mock AI response");

mockMvc.perform(post("/api/chat")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"message\":\"Hello\"}"))
    .andExpect(status().isOk())
    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
    .andExpect(jsonPath("$.response").value("Mock AI response"));

verify(chatService).chat("Hello");
```

---

#### Test 1.2: shouldInvokeChatServiceWithCorrectMessage_WhenPostApiChat

**Purpose**: Verify controller correctly extracts message from request and passes to service

**Preconditions**:
- ChatService mock configured

**Test Steps**:
1. Arrange: Mock chatService.chat()
2. Act: POST to /api/chat with message "What is the capital of France?"
3. Assert: chatService.chat() called with exact message

**Expected Result**: Service invoked with correct parameter

**Assertions**:
```java
when(chatService.chat(anyString())).thenReturn("Paris");

mockMvc.perform(post("/api/chat")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"message\":\"What is the capital of France?\"}"))
    .andExpect(status().isOk());

verify(chatService).chat("What is the capital of France?");
verify(chatService, never()).chatWithSystem(anyString(), anyString());
```

---

### Test Group 2: POST /api/chat/with-system Endpoint

#### Test 2.1: shouldReturnChatResponse_WhenSystemAndUserMessagesProvided

**Purpose**: Verify POST /api/chat/with-system returns 200 OK with both messages

**Preconditions**:
- ChatService mock configured

**Test Steps**:
1. Arrange: Mock chatService.chatWithSystem() to return "Pirate response"
2. Act: POST with systemMessage and message
3. Assert: HTTP 200, correct JSON response

**Expected Result**: 200 OK with ChatResponse JSON

**Assertions**:
```java
when(chatService.chatWithSystem(anyString(), anyString()))
    .thenReturn("Ahoy matey!");

mockMvc.perform(post("/api/chat/with-system")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"systemMessage\":\"You are a pirate\",\"message\":\"Hello\"}"))
    .andExpect(status().isOk())
    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
    .andExpect(jsonPath("$.response").value("Ahoy matey!"));
```

---

#### Test 2.2: shouldUseDefaultSystemMessage_WhenSystemMessageIsNull

**Purpose**: Verify controller uses default system message when systemMessage is null

**Preconditions**:
- ChatService mock configured

**Test Steps**:
1. Arrange: Mock chatService.chatWithSystem()
2. Act: POST with only message (systemMessage null)
3. Assert: Service called with default "You are a helpful assistant."

**Expected Result**: Default system message used

**Assertions**:
```java
when(chatService.chatWithSystem(anyString(), anyString()))
    .thenReturn("Response");

mockMvc.perform(post("/api/chat/with-system")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"message\":\"Hello\"}"))
    .andExpect(status().isOk());

verify(chatService).chatWithSystem(
    eq("You are a helpful assistant."),
    eq("Hello")
);
```

---

#### Test 2.3: shouldUseProvidedSystemMessage_WhenSystemMessageNotNull

**Purpose**: Verify controller uses provided system message when not null

**Preconditions**:
- ChatService mock configured

**Test Steps**:
1. Arrange: Mock chatService.chatWithSystem()
2. Act: POST with custom systemMessage
3. Assert: Service called with provided system message

**Expected Result**: Custom system message used

**Assertions**:
```java
when(chatService.chatWithSystem(anyString(), anyString()))
    .thenReturn("Custom response");

mockMvc.perform(post("/api/chat/with-system")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"systemMessage\":\"Custom prompt\",\"message\":\"Test\"}"))
    .andExpect(status().isOk());

verify(chatService).chatWithSystem(
    eq("Custom prompt"),
    eq("Test")
);
```

---

### Test Group 3: GET /api/chat/health Endpoint

#### Test 3.1: shouldReturnHealthMessage_WhenGetApiChatHealth

**Purpose**: Verify health check endpoint returns correct message

**Preconditions**:
- No mocking needed (simple endpoint)

**Test Steps**:
1. Act: GET to /api/chat/health
2. Assert: HTTP 200, Content-Type text/plain, body = "AI Chat Service is running!"

**Expected Result**: 200 OK with health message

**Assertions**:
```java
mockMvc.perform(get("/api/chat/health"))
    .andExpect(status().isOk())
    .andExpect(content().string("AI Chat Service is running!"));

// Verify service not called for health check
verifyNoInteractions(chatService);
```

---

### Test Group 4: Error Handling and Validation

#### Test 4.1: shouldReturn400_WhenRequestBodyIsInvalidJSON

**Purpose**: Verify malformed JSON returns 400 Bad Request

**Preconditions**:
- No mocking needed

**Test Steps**:
1. Act: POST with malformed JSON
2. Assert: HTTP 400 Bad Request

**Expected Result**: 400 Bad Request

**Assertions**:
```java
mockMvc.perform(post("/api/chat")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{invalid json}"))
    .andExpect(status().isBadRequest());

verifyNoInteractions(chatService);
```

---

#### Test 4.2: shouldReturn400_WhenMessageFieldIsMissing

**Purpose**: Verify missing required field returns 400 Bad Request

**Preconditions**:
- No mocking needed

**Test Steps**:
1. Act: POST with empty JSON object or missing message field
2. Assert: HTTP 400 Bad Request

**Expected Result**: 400 Bad Request (Jackson validation)

**Assertions**:
```java
mockMvc.perform(post("/api/chat")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{}"))
    .andExpect(status().isBadRequest());

verifyNoInteractions(chatService);
```

---

#### Test 4.3: shouldReturn400_WhenContentTypeNotJSON

**Purpose**: Verify incorrect Content-Type returns 400 Bad Request

**Preconditions**:
- No mocking needed

**Test Steps**:
1. Act: POST with Content-Type: text/plain
2. Assert: HTTP 400 or 415 Unsupported Media Type

**Expected Result**: 400/415 error

**Assertions**:
```java
mockMvc.perform(post("/api/chat")
        .contentType(MediaType.TEXT_PLAIN)
        .content("Hello"))
    .andExpect(status().is4xxClientError());

verifyNoInteractions(chatService);
```

---

#### Test 4.4: shouldReturn500_WhenChatServiceThrowsException

**Purpose**: Verify service exceptions return 500 Internal Server Error

**Preconditions**:
- ChatService mock configured to throw exception

**Test Steps**:
1. Arrange: Mock chatService.chat() to throw RuntimeException
2. Act: POST valid request
3. Assert: HTTP 500 Internal Server Error

**Expected Result**: 500 error with exception propagated

**Assertions**:
```java
when(chatService.chat(anyString()))
    .thenThrow(new RuntimeException("AI model unavailable"));

mockMvc.perform(post("/api/chat")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"message\":\"Hello\"}"))
    .andExpect(status().isInternalServerError());

verify(chatService).chat("Hello");
```

---

### Test Group 5: Edge Cases

#### Test 5.1: shouldHandleLongMessage_WhenMessageExceeds1000Characters

**Purpose**: Verify controller handles long messages without errors

**Preconditions**:
- ChatService mock configured

**Test Steps**:
1. Arrange: Create JSON with 1000+ character message
2. Act: POST request
3. Assert: HTTP 200, service called with full message

**Expected Result**: Long message processed successfully

**Assertions**:
```java
String longMessage = "a".repeat(1000);
when(chatService.chat(anyString())).thenReturn("Response");

mockMvc.perform(post("/api/chat")
        .contentType(MediaType.APPLICATION_JSON)
        .content(String.format("{\"message\":\"%s\"}", longMessage)))
    .andExpect(status().isOk());

verify(chatService).chat(longMessage);
```

---

#### Test 5.2: shouldHandleSpecialCharacters_WhenMessageContainsUnicode

**Purpose**: Verify unicode and special characters handled correctly

**Preconditions**:
- ChatService mock configured

**Test Steps**:
1. Arrange: Create JSON with unicode message: "Hello 世界 🌍"
2. Act: POST request
3. Assert: HTTP 200, service called with preserved characters

**Expected Result**: Special characters preserved

**Assertions**:
```java
String unicodeMessage = "Hello 世界 🌍";
when(chatService.chat(anyString())).thenReturn("Response");

mockMvc.perform(post("/api/chat")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"message\":\"" + unicodeMessage + "\"}"))
    .andExpect(status().isOk());

verify(chatService).chat(unicodeMessage);
```

---

#### Test 5.3: shouldHandleEmptyMessage_WhenMessageIsEmptyString

**Purpose**: Document behavior with empty string message

**Preconditions**:
- ChatService mock configured

**Test Steps**:
1. Arrange: Mock service
2. Act: POST with message = ""
3. Assert: HTTP 200 (currently no validation)

**Expected Result**: Empty message passes through (no validation yet)

**Assertions**:
```java
when(chatService.chat(anyString())).thenReturn("Response");

mockMvc.perform(post("/api/chat")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"message\":\"\"}"))
    .andExpect(status().isOk());

verify(chatService).chat("");
```

**Note**: Documents current behavior. Future enhancement may add validation.

---

## Test Execution Requirements

### Test Independence
- Each test must be runnable in isolation
- MockMvc resets between tests automatically
- ChatService mock reset by @MockBean between tests
- Tests can execute in any order

### Performance Requirements
- All tests combined: <2 seconds execution time
- Individual test: <200ms per test (includes Spring context loading for @WebMvcTest)
- MockMvc faster than full @SpringBootTest

### Coverage Requirements
- **Line coverage**: 100% (all 3 endpoints covered)
- **Branch coverage**: 100% (systemMessage null check)
- **Method coverage**: 100% (all public endpoints tested)

## Test Data Constants

```java
private static final String VALID_CHAT_REQUEST = """
    {
      "message": "Hello, AI!"
    }
    """;

private static final String VALID_CHAT_WITH_SYSTEM_REQUEST = """
    {
      "systemMessage": "You are a helpful assistant.",
      "message": "Hello"
    }
    """;

private static final String CHAT_REQUEST_NO_SYSTEM = """
    {
      "message": "Hello"
    }
    """;

private static final String INVALID_JSON = "{invalid json}";

private static final String EMPTY_JSON = "{}";

private static final String MOCK_AI_RESPONSE = "This is a mock AI response";
```

## JSON Path Assertions Reference

```java
// Verify response field exists and has value
.andExpect(jsonPath("$.response").exists())
.andExpect(jsonPath("$.response").isString())
.andExpect(jsonPath("$.response").value("expected value"))

// Verify response is not empty
.andExpect(jsonPath("$.response").isNotEmpty())

// Verify response field is missing (for error cases)
.andExpect(jsonPath("$.response").doesNotExist())
```

## Summary

**Total Test Methods**: 13 tests
- POST /api/chat: 2 tests
- POST /api/chat/with-system: 3 tests
- GET /api/chat/health: 1 test
- Error handling and validation: 4 tests
- Edge cases: 3 tests

**Expected Coverage**: 100% line coverage, 100% branch coverage

**Execution Time**: <2 seconds total, <200ms per test

**Key Validations**:
- ✅ All endpoints return correct HTTP status codes
- ✅ JSON serialization/deserialization works correctly
- ✅ Service layer called with correct parameters
- ✅ Default system message applied when null
- ✅ Custom system message used when provided
- ✅ Invalid JSON returns 400
- ✅ Missing fields return 400
- ✅ Service exceptions return 500
- ✅ Health check endpoint works independently
- ✅ Long messages and special characters handled correctly