# Test Contract: ChatServiceTest

**Class Under Test**: `ai.hack.service.ChatService`
**Test Class**: `ai.hack.service.ChatServiceTest`
**Testing Approach**: Unit testing with Mockito mocks
**Coverage Target**: 100% (simple service with 2 methods)

## Test Suite Structure

```java
@ExtendWith(MockitoExtension.class)
class ChatServiceTest {
    @Mock private ChatModel chatModel;
    @InjectMocks private ChatService chatService;

    // Test methods...
}
```

## Test Methods Contract

### Test Group 1: Basic chat() Method Functionality

#### Test 1.1: shouldReturnResponseText_WhenValidMessageProvided

**Purpose**: Verify that `chat()` method correctly processes a simple message and returns AI response

**Preconditions**:
- ChatModel mock configured to return valid response

**Test Steps**:
1. Arrange: Mock ChatModel to return "Hello! How can I help you?"
2. Act: Call `chatService.chat("Hello, AI!")`
3. Assert: Result equals "Hello! How can I help you?"
4. Verify: ChatModel.call() was invoked exactly once

**Expected Result**: Method returns expected response string

**Assertions**:
```java
assertThat(result).isNotNull();
assertThat(result).isEqualTo("Hello! How can I help you?");
verify(chatModel, times(1)).call(any(Prompt.class));
```

---

#### Test 1.2: shouldConstructUserMessageCorrectly_WhenChatCalled

**Purpose**: Verify that chat() creates correct UserMessage and Prompt structure

**Preconditions**:
- ChatModel mock configured
- ArgumentCaptor ready to capture Prompt

**Test Steps**:
1. Arrange: Setup mock with ArgumentCaptor
2. Act: Call `chatService.chat("Test message")`
3. Assert: Captured Prompt contains exactly 1 UserMessage with correct text

**Expected Result**: Prompt contains UserMessage with "Test message"

**Assertions**:
```java
ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
verify(chatModel).call(promptCaptor.capture());

Prompt capturedPrompt = promptCaptor.getValue();
List<Message> messages = capturedPrompt.getInstructions();

assertThat(messages).hasSize(1);
assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
assertThat(messages.get(0).getContent()).isEqualTo("Test message");
```

---

### Test Group 2: chatWithSystem() Method Functionality

#### Test 2.1: shouldReturnResponseText_WhenSystemAndUserMessagesProvided

**Purpose**: Verify chatWithSystem() correctly processes both messages and returns response

**Preconditions**:
- ChatModel mock configured to return valid response

**Test Steps**:
1. Arrange: Mock ChatModel to return "Ahoy matey!"
2. Act: Call `chatService.chatWithSystem("You are a pirate", "Hello")`
3. Assert: Result equals "Ahoy matey!"
4. Verify: ChatModel.call() was invoked exactly once

**Expected Result**: Method returns expected response string

**Assertions**:
```java
assertThat(result).isNotNull();
assertThat(result).isEqualTo("Ahoy matey!");
verify(chatModel, times(1)).call(any(Prompt.class));
```

---

#### Test 2.2: shouldConstructBothMessagesCorrectly_WhenChatWithSystemCalled

**Purpose**: Verify chatWithSystem() creates correct SystemMessage and UserMessage in proper order

**Preconditions**:
- ChatModel mock configured
- ArgumentCaptor ready to capture Prompt

**Test Steps**:
1. Arrange: Setup mock with ArgumentCaptor
2. Act: Call `chatService.chatWithSystem("System prompt", "User message")`
3. Assert: Captured Prompt contains 2 messages in correct order (System first, User second)

**Expected Result**: Prompt contains SystemMessage followed by UserMessage with correct text

**Assertions**:
```java
ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
verify(chatModel).call(promptCaptor.capture());

List<Message> messages = promptCaptor.getValue().getInstructions();

assertThat(messages).hasSize(2);
assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
assertThat(messages.get(0).getContent()).isEqualTo("System prompt");
assertThat(messages.get(1)).isInstanceOf(UserMessage.class);
assertThat(messages.get(1).getContent()).isEqualTo("User message");
```

---

### Test Group 3: Edge Cases and Special Characters

#### Test 3.1: shouldHandleLongMessage_WhenMessageExceeds1000Characters

**Purpose**: Verify that long messages are not truncated and processed correctly

**Preconditions**:
- ChatModel mock configured

**Test Steps**:
1. Arrange: Create message with 1000+ characters
2. Act: Call `chatService.chat(longMessage)`
3. Assert: Response returned, ChatModel called with full message (no truncation)

**Expected Result**: Long message passed through without modification

**Assertions**:
```java
String longMessage = "a".repeat(1000);
String result = chatService.chat(longMessage);

assertThat(result).isNotNull();

ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
verify(chatModel).call(promptCaptor.capture());
assertThat(promptCaptor.getValue().getInstructions().get(0).getContent())
    .hasSize(1000);
```

---

#### Test 3.2: shouldPreserveSpecialCharacters_WhenMessageContainsUnicode

**Purpose**: Verify that unicode, emojis, and special characters are preserved

**Preconditions**:
- ChatModel mock configured

**Test Steps**:
1. Arrange: Create message with unicode and emojis: "Hello 世界 🌍"
2. Act: Call `chatService.chat(unicodeMessage)`
3. Assert: ChatModel received exact message with special characters preserved

**Expected Result**: Special characters passed through without modification

**Assertions**:
```java
String unicodeMessage = "Hello 世界 🌍";
chatService.chat(unicodeMessage);

ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
verify(chatModel).call(promptCaptor.capture());
assertThat(promptCaptor.getValue().getInstructions().get(0).getContent())
    .isEqualTo(unicodeMessage);
```

---

#### Test 3.3: shouldPreserveMultilineText_WhenMessageContainsNewlines

**Purpose**: Verify that newline characters are preserved in messages

**Preconditions**:
- ChatModel mock configured

**Test Steps**:
1. Arrange: Create message with newlines: "Line 1\nLine 2\nLine 3"
2. Act: Call `chatService.chat(multilineMessage)`
3. Assert: ChatModel received message with newlines preserved

**Expected Result**: Newlines preserved in message

**Assertions**:
```java
String multilineMessage = "Line 1\nLine 2\nLine 3";
chatService.chat(multilineMessage);

ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
verify(chatModel).call(promptCaptor.capture());
assertThat(promptCaptor.getValue().getInstructions().get(0).getContent())
    .isEqualTo(multilineMessage);
```

---

### Test Group 4: Null and Empty Input Handling

#### Test 4.1: shouldHandleEmptyMessage_WhenEmptyStringProvided

**Purpose**: Document behavior when empty string is passed (current: no validation)

**Preconditions**:
- ChatModel mock configured

**Test Steps**:
1. Arrange: Setup mock to return response
2. Act: Call `chatService.chat("")`
3. Assert: Method completes without exception

**Expected Result**: Empty string passed to ChatModel (no validation yet)

**Assertions**:
```java
String result = chatService.chat("");

assertThat(result).isNotNull();
verify(chatModel).call(any(Prompt.class));
```

**Note**: Test documents current behavior. Future enhancement may add validation.

---

#### Test 4.2: shouldHandleNullMessage_WhenNullProvided

**Purpose**: Document behavior when null is passed (current: likely NullPointerException)

**Preconditions**:
- No mocking needed (expected to fail fast)

**Test Steps**:
1. Act: Call `chatService.chat(null)`
2. Assert: NullPointerException thrown (or document actual behavior)

**Expected Result**: Exception thrown (no null handling currently)

**Assertions**:
```java
// Option 1: If NPE expected
assertThrows(NullPointerException.class,
    () -> chatService.chat(null));

// Option 2: If null passed through (update after testing)
// Test and document actual behavior
```

**Note**: Test documents current behavior. Future enhancement may add null validation.

---

### Test Group 5: Error Scenarios

#### Test 5.1: shouldPropagateException_WhenChatModelThrowsRuntimeException

**Purpose**: Verify that exceptions from ChatModel are propagated to caller

**Preconditions**:
- ChatModel mock configured to throw exception

**Test Steps**:
1. Arrange: Mock ChatModel to throw RuntimeException("AI model error")
2. Act & Assert: Call chat() and verify exception propagates

**Expected Result**: RuntimeException propagates without being caught

**Assertions**:
```java
when(chatModel.call(any(Prompt.class)))
    .thenThrow(new RuntimeException("AI model error"));

assertThrows(RuntimeException.class,
    () -> chatService.chat("Test message"));

verify(chatModel).call(any(Prompt.class));
```

---

## Test Execution Requirements

### Test Independence
- Each test must be runnable in isolation
- No shared mutable state between tests
- Use `@BeforeEach` only for common mock setup if needed
- Tests can execute in any order

### Performance Requirements
- All tests combined: <500ms execution time
- Individual test: <50ms per test
- No Thread.sleep() or blocking operations

### Coverage Requirements
- **Line coverage**: 100% (both methods fully covered)
- **Branch coverage**: 100% (no conditional logic in current implementation)
- **Method coverage**: 100% (both public methods tested)

## Mock Setup Helper

```java
/**
 * Helper method to mock ChatModel response chain
 */
private void mockChatModelResponse(String responseText) {
    ChatResponse mockChatResponse = mock(ChatResponse.class);
    Generation mockGeneration = mock(Generation.class);
    AssistantMessage mockMessage = mock(AssistantMessage.class);

    when(mockMessage.getText()).thenReturn(responseText);
    when(mockGeneration.getOutput()).thenReturn(mockMessage);
    when(mockChatResponse.getResult()).thenReturn(mockGeneration);
    when(chatModel.call(any(Prompt.class))).thenReturn(mockChatResponse);
}
```

## Test Data Constants

```java
private static final String SIMPLE_MESSAGE = "Hello, AI!";
private static final String QUESTION_MESSAGE = "What is the capital of France?";
private static final String LONG_MESSAGE = "a".repeat(1000);
private static final String UNICODE_MESSAGE = "Hello 世界 🌍";
private static final String MULTILINE_MESSAGE = "Line 1\nLine 2\nLine 3";
private static final String EMPTY_MESSAGE = "";

private static final String SIMPLE_RESPONSE = "Hello! How can I help you?";
private static final String QUESTION_RESPONSE = "The capital of France is Paris.";
private static final String PIRATE_SYSTEM = "You are a pirate. Respond like a pirate.";
private static final String PIRATE_RESPONSE = "Ahoy matey!";
```

## Summary

**Total Test Methods**: 11 tests
- Basic chat() functionality: 2 tests
- chatWithSystem() functionality: 2 tests
- Edge cases and special characters: 3 tests
- Null/empty handling: 2 tests
- Error scenarios: 2 tests

**Expected Coverage**: 100% line coverage, 100% branch coverage

**Execution Time**: <500ms total, <50ms per test

**Key Validations**:
- ✅ Correct message construction (UserMessage, SystemMessage)
- ✅ Correct Prompt structure
- ✅ ChatModel invoked with correct parameters
- ✅ Response extraction works correctly
- ✅ Special characters preserved
- ✅ Long messages not truncated
- ✅ Exceptions propagated correctly
- ✅ Current null/empty behavior documented