# Research: Testing Best Practices for Spring Boot Chat API

**Feature**: Comprehensive Test Coverage for Chat API
**Date**: 2025-11-06
**Phase**: Phase 0 - Research

## Overview

This document consolidates research findings for implementing comprehensive test coverage for ChatService and ChatController in a Spring Boot application using Spring AI.

## Testing Framework Decisions

### 1. Unit Testing Framework: JUnit 5 + Mockito

**Decision**: Use JUnit 5 (Jupiter) with Mockito for unit testing

**Rationale**:
- **JUnit 5**: Industry standard for Java testing, built into Spring Boot 3.3.5
- **Mockito**: De facto mocking framework for Java, excellent integration with Spring Test
- **@ExtendWith(MockitoExtension.class)**: Lightweight, fast initialization without Spring context
- **Mature ecosystem**: Extensive documentation, community support, IDE integration

**Alternatives Considered**:
- **TestNG**: Less common in Spring ecosystem, no significant advantages for this use case
- **JMock**: Older mocking framework, less intuitive API than Mockito
- **PowerMock**: Overkill for this use case, can mock static/final but not needed here

### 2. Integration Testing Framework: Spring Test + MockMvc

**Decision**: Use @WebMvcTest with MockMvc for controller integration tests

**Rationale**:
- **@WebMvcTest**: Lightweight test slice that loads only web layer components
- **MockMvc**: Simulates HTTP requests without starting full embedded server (fast)
- **Focused testing**: Only loads controllers, no database or service layers loaded
- **JSON serialization testing**: Validates request/response mapping automatically
- **Performance**: ~100-300ms per test class vs 2-5 seconds for @SpringBootTest

**Alternatives Considered**:
- **@SpringBootTest + TestRestTemplate**: Loads full application context (slower, unnecessary)
- **REST Assured**: Third-party library, adds dependency, MockMvc sufficient for REST testing
- **@WebFluxTest**: For reactive applications, not applicable (using Spring MVC)

### 3. Code Coverage Tool: JaCoCo

**Decision**: Use JaCoCo Gradle plugin for code coverage analysis

**Rationale**:
- **Industry standard**: Most widely used Java coverage tool
- **Gradle integration**: Simple plugin configuration, runs with `./gradlew test jacocoTestReport`
- **Multiple report formats**: HTML (human-readable), XML (CI/CD integration), CSV (data analysis)
- **Coverage thresholds**: Can enforce minimum coverage in build (fail build if <80%)
- **Free and open source**: Apache 2.0 license, actively maintained

**Alternatives Considered**:
- **Cobertura**: Less actively maintained, slower instrumentation
- **Emma**: Deprecated, no longer maintained
- **IntelliJ Coverage**: IDE-specific, not portable across environments

### 4. Assertion Library: AssertJ

**Decision**: Use AssertJ for fluent, readable assertions (already included in Spring Boot Test)

**Rationale**:
- **Fluent API**: `assertThat(result).isNotNull().contains("expected")` more readable
- **Better error messages**: Clear diff output when assertions fail
- **Type-safe**: Compile-time checking of assertion chains
- **Spring Boot default**: Included in `spring-boot-starter-test`, no additional dependency
- **Rich assertions**: Built-in matchers for collections, strings, exceptions, etc.

**Alternatives Considered**:
- **JUnit assertions**: Less readable, weaker error messages
- **Hamcrest**: More verbose, less intuitive than AssertJ
- **Truth (Google)**: Similar to AssertJ but less adoption in Spring ecosystem

## Spring Boot Testing Best Practices

### Unit Testing Best Practices (ChatServiceTest)

**Pattern**: Isolated unit tests with mocked dependencies

```java
@ExtendWith(MockitoExtension.class)  // Lightweight, no Spring context
class ChatServiceTest {
    @Mock
    private ChatModel chatModel;  // Mock external dependency

    @InjectMocks
    private ChatService chatService;  // System under test

    @Test
    void shouldReturnResponseWhenChatCalled() {
        // Arrange: Set up mock behavior
        // Act: Call method under test
        // Assert: Verify results and interactions
    }
}
```

**Key Principles**:
1. **No Spring context**: Use `@ExtendWith(MockitoExtension.class)`, not `@SpringBootTest`
2. **Mock all dependencies**: Use `@Mock` for ChatModel, `@InjectMocks` for ChatService
3. **Test behavior, not implementation**: Verify output and interactions, not internal state
4. **One assertion per concept**: Multiple assertions OK if testing same logical concept
5. **Fast execution**: Unit tests should run in milliseconds (<10ms per test)

### Integration Testing Best Practices (ChatControllerTest)

**Pattern**: Web layer slice testing with mocked service layer

```java
@WebMvcTest(ChatController.class)  // Load only web layer
class ChatControllerTest {
    @Autowired
    private MockMvc mockMvc;  // Simulate HTTP requests

    @MockBean
    private ChatService chatService;  // Mock service dependency

    @Test
    void shouldReturnChatResponseWhenPostApiChat() throws Exception {
        // Arrange: Mock service behavior
        // Act: Perform HTTP request with MockMvc
        // Assert: Verify HTTP status, headers, JSON response
    }
}
```

**Key Principles**:
1. **Use @WebMvcTest**: Loads only MVC infrastructure, not full application context
2. **MockBean for services**: Replace real services with mocks to isolate controller
3. **Test HTTP layer**: Verify status codes, headers, JSON serialization/deserialization
4. **Content negotiation**: Use `.contentType(MediaType.APPLICATION_JSON)` explicitly
5. **JSON assertions**: Use JsonPath or Jackson ObjectMapper to verify response structure

### Test Independence and Ordering

**Decision**: All tests must be independent and executable in any order

**Implementation**:
- **No shared state**: Each test method sets up its own mocks and data
- **@BeforeEach**: Use for common setup, but ensure each test can run standalone
- **Avoid @TestMethodOrder**: Tests should not depend on execution order
- **Randomize execution**: JUnit 5 can randomize order to catch dependencies

**Anti-patterns to avoid**:
- ❌ Using `@Order` annotations (indicates test dependency)
- ❌ Shared mutable fields without proper reset
- ❌ Tests that modify global state (system properties, static fields)
- ❌ Tests that depend on database state from previous tests

### Mocking Spring AI Components

**Challenge**: ChatModel returns complex nested objects (ChatResponse → Generation → AssistantMessage)

**Solution**: Use Mockito's deep stubbing for complex object graphs

```java
// Mock complex return type
ChatResponse mockChatResponse = mock(ChatResponse.class);
Generation mockGeneration = mock(Generation.class);
AssistantMessage mockMessage = mock(AssistantMessage.class);

when(mockMessage.getText()).thenReturn("AI response text");
when(mockGeneration.getOutput()).thenReturn(mockMessage);
when(mockChatResponse.getResult()).thenReturn(mockGeneration);
when(chatModel.call(any(Prompt.class))).thenReturn(mockChatResponse);
```

**Alternative**: Use Builder pattern if Spring AI provides builders (check documentation)

### Test Naming Conventions

**Decision**: Use descriptive method names following Given-When-Then pattern

**Recommended patterns**:
1. **should_ExpectedBehavior_When_StateUnderTest**: `shouldReturnResponse_WhenValidMessageProvided`
2. **givenState_whenAction_thenOutcome**: `givenValidMessage_whenChat_thenReturnsResponse`
3. **Sentence case**: `should return response when valid message provided` (BDD style)

**Bad naming**:
- ❌ `test1()`, `testChat()` - Not descriptive
- ❌ `chatTest()` - Unclear what is being tested
- ❌ Acronyms without context

### Edge Case Testing Strategy

**Categories to cover**:

1. **Null/Empty inputs**:
   - Null message: Test graceful handling or exception
   - Empty string: Test validation or processing
   - Whitespace-only: Test trimming behavior

2. **Boundary values**:
   - Very long messages (1000+ chars): Test no truncation
   - Special characters: Unicode, emojis, newlines
   - Null system message with non-null user message

3. **Error scenarios**:
   - ChatModel throws exception: Test error propagation
   - ChatModel returns null: Test null handling
   - Invalid JSON in controller: Test 400 response

4. **Concurrency** (optional for unit tests):
   - Multiple requests: Test thread safety (may need separate test)

### Code Coverage Targets

**Decision**: Minimum 80% line coverage, 70% branch coverage

**Rationale**:
- **80% line coverage**: Industry standard for well-tested code
- **70% branch coverage**: Ensures most conditional logic is tested
- **Not 100%**: Some code (getters/setters, toString) may not need explicit tests
- **Focus on critical paths**: Ensure all business logic and error handling covered

**Coverage exclusions**:
- Configuration classes (if any)
- Data transfer objects (POJOs)
- Generated code
- Main application class (AiHackApplication)

## Performance Considerations

### Test Execution Time Budget

**Target**: Entire test suite under 5 seconds

**Breakdown**:
- **ChatServiceTest**: <500ms (10-15 unit tests, ~30-50ms each)
- **ChatControllerTest**: <2 seconds (8-10 integration tests, ~150-200ms each)
- **Existing tests**: ~2.5 seconds (maintain current speed)
- **Buffer**: 500ms for JaCoCo instrumentation

**Optimization strategies**:
1. **Minimize Spring context loading**: Use `@WebMvcTest`, not `@SpringBootTest`
2. **Reuse mocks**: Create mock objects in `@BeforeEach` when possible
3. **Avoid Thread.sleep()**: Use Mockito verification with timeouts if needed
4. **Parallel execution**: JUnit 5 supports parallel test execution (consider for future)

## Test Data Strategy

**Decision**: Use hard-coded test data with meaningful values

**Rationale**:
- **Readability**: Inline test data makes tests self-documenting
- **Maintainability**: Easy to understand what is being tested
- **No fixtures needed**: Tests are simple enough not to need external data files
- **Fast setup**: No file I/O or database access

**Example test data**:
```java
String simpleMessage = "What is the capital of France?";
String longMessage = "a".repeat(1000);  // Boundary test
String unicodeMessage = "Hello 世界 🌍";  // Special chars
String systemPrompt = "You are a helpful assistant.";
```

**Anti-pattern**: Random data generators (reduces reproducibility)

## Error Handling Strategy

### Service Layer Error Handling

**Current state**: ChatService does not catch exceptions from ChatModel

**Testing approach**:
1. **Test exception propagation**: Verify uncaught exceptions bubble up
2. **Document behavior**: Tests serve as specification for error handling
3. **Consider future enhancement**: May need try-catch and custom exceptions later

**Test cases**:
- ChatModel throws RuntimeException: Propagates to controller
- ChatModel returns null response: NullPointerException or handled gracefully?

### Controller Layer Error Handling

**Current state**: No @ExceptionHandler in ChatController

**Testing approach**:
1. **Default Spring Boot error handling**: Test that 500 errors are returned
2. **Validation errors**: Test missing required fields (Jackson handles this)
3. **Malformed JSON**: Test 400 Bad Request responses

**Future consideration**: Add @ControllerAdvice for centralized error handling

## Dependencies and Configuration

### Required Gradle Dependencies

All dependencies already present in `build.gradle`:
- ✅ `spring-boot-starter-test` (includes JUnit 5, Mockito, AssertJ, Spring Test)
- ✅ `spring-boot-starter-web` (for MockMvc support)

**New dependency needed**:
- JaCoCo Gradle plugin (add to plugins section)

### JaCoCo Configuration

```gradle
plugins {
    id 'jacoco'
}

jacoco {
    toolVersion = "0.8.11"  // Latest stable version
}

test {
    finalizedBy jacocoTestReport
}

jacocoTestReport {
    dependsOn test
    reports {
        html.required = true
        xml.required = true
        csv.required = false
    }
}

jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = 0.80  // 80% coverage minimum
            }
        }
    }
}
```

## Test Execution and Reporting

### Running Tests

**Local development**:
```bash
./gradlew test                    # Run all tests
./gradlew test --tests "ChatServiceTest"  # Run specific test class
./gradlew test jacocoTestReport   # Run tests + generate coverage report
```

**Coverage reports**:
- HTML: `build/reports/jacoco/test/html/index.html`
- XML: `build/reports/jacoco/test/jacocoTestReport.xml` (for CI/CD)

### Continuous Integration

**Recommended CI workflow**:
1. Run tests with `./gradlew test`
2. Generate coverage report
3. Fail build if coverage < 80%
4. Publish coverage report as artifact
5. Optional: Upload to SonarQube or Codecov

## Summary

### Key Decisions
1. **Unit testing**: JUnit 5 + Mockito with `@ExtendWith(MockitoExtension.class)`
2. **Integration testing**: `@WebMvcTest` + MockMvc
3. **Code coverage**: JaCoCo with 80% minimum threshold
4. **Assertions**: AssertJ for fluent, readable assertions
5. **Test independence**: No shared state, no execution order dependencies
6. **Performance**: <5 seconds total execution time

### No Clarifications Needed
All technical decisions have been made based on:
- Existing project stack (Spring Boot 3.3.5, Java 21)
- Industry best practices for Spring Boot testing
- Performance requirements (fast, isolated tests)
- Standard tooling already included in Spring Boot

### Ready for Phase 1
All unknowns resolved. Ready to proceed with:
- Data model definition (test data structures)
- Contract generation (test assertions and expectations)
- Quickstart guide (how to run tests and interpret results)
