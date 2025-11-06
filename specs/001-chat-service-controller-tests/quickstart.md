# Quickstart Guide: Running and Understanding the Tests

**Feature**: Comprehensive Test Coverage for Chat API
**Date**: 2025-11-06
**Target Audience**: Developers working on this feature

## Overview

This guide explains how to run the ChatServiceTest and ChatControllerTest, interpret results, and understand code coverage reports.

## Prerequisites

- Java 21 installed
- Project cloned locally
- Gradle wrapper available (`./gradlew`)

## Running Tests

### Run All Tests

```bash
# From project root
./gradlew test

# Output will show:
# - Number of tests executed
# - Number passed/failed/skipped
# - Execution time
# - Location of test report
```

**Expected Output**:
```
ChatServiceTest > shouldReturnResponseText_WhenValidMessageProvided() PASSED
ChatServiceTest > shouldConstructUserMessageCorrectly_WhenChatCalled() PASSED
...
ChatControllerTest > shouldReturnChatResponse_WhenValidMessageProvided() PASSED
...

BUILD SUCCESSFUL in 4s
```

### Run Specific Test Class

```bash
# Run only ChatServiceTest
./gradlew test --tests "ai.hack.service.ChatServiceTest"

# Run only ChatControllerTest
./gradlew test --tests "ai.hack.controller.ChatControllerTest"
```

### Run Specific Test Method

```bash
# Run single test method
./gradlew test --tests "ai.hack.service.ChatServiceTest.shouldReturnResponseText_WhenValidMessageProvided"
```

### Run Tests with Coverage

```bash
# Run tests and generate coverage report
./gradlew test jacocoTestReport

# Coverage report location:
# build/reports/jacoco/test/html/index.html
```

**Open in browser**:
```bash
# macOS
open build/reports/jacoco/test/html/index.html

# Linux
xdg-open build/reports/jacoco/test/html/index.html

# Windows
start build/reports/jacoco/test/html/index.html
```

### Continuous Testing (Watch Mode)

```bash
# Run tests automatically on file changes (requires gradle-watch plugin or IDE)
# Recommended: Use IDE's built-in continuous testing

# IntelliJ IDEA: Right-click test class → Run with Coverage → Enable auto-test
# VS Code with Java: Use Test Explorer with auto-run enabled
```

## Understanding Test Results

### Test Report Location

After running tests, detailed HTML report available at:
```
build/reports/tests/test/index.html
```

**Report contains**:
- Test summary (passed, failed, skipped)
- Execution time per test
- Test failure details with stack traces
- Standard output/error from tests

### Reading Test Output

**Successful test**:
```
ChatServiceTest > shouldReturnResponseText_WhenValidMessageProvided() PASSED (42ms)
```

**Failed test**:
```
ChatServiceTest > shouldReturnResponseText_WhenValidMessageProvided() FAILED

org.opentest4j.AssertionFailedError:
Expected: "Expected response"
Actual: "Actual response"
    at ChatServiceTest.java:45
```

### Test Naming Convention

Test method names follow this pattern:
```
should<ExpectedBehavior>_When<Condition>

Examples:
- shouldReturnResponseText_WhenValidMessageProvided
- shouldReturn400_WhenMessageFieldIsMissing
- shouldUseDefaultSystemMessage_WhenSystemMessageIsNull
```

**Why this helps**: Test name clearly describes what is being tested and expected outcome

## Understanding Code Coverage

### Coverage Metrics Explained

**Line Coverage**: Percentage of code lines executed during tests
- **Target**: 80% minimum
- **Interpretation**: 85% = 85 out of 100 lines executed

**Branch Coverage**: Percentage of decision paths (if/else, switch) tested
- **Target**: 70% minimum
- **Interpretation**: 75% = 3 out of 4 branches tested

**Method Coverage**: Percentage of methods called during tests
- **Target**: 100% for public methods
- **Interpretation**: All public methods should have at least one test

### Reading Coverage Report

**HTML Report Structure**:
```
index.html
├── Overview (project-level summary)
├── Package: ai.hack.service (package-level)
│   └── ChatService.java (class-level details)
└── Package: ai.hack.controller
    └── ChatController.java
```

**Color Coding**:
- 🟢 **Green**: Line fully covered by tests
- 🟡 **Yellow**: Line partially covered (some branches not tested)
- 🔴 **Red**: Line not covered by tests

**Example Coverage View**:
```java
public String chat(String message) {           // 🟢 Covered
    UserMessage userMessage = new UserMessage(message);  // 🟢 Covered
    Prompt prompt = new Prompt(List.of(userMessage));    // 🟢 Covered
    return chatModel.call(prompt)                        // 🟢 Covered
        .getResult().getOutput().getText();              // 🟢 Covered
}
```

### Expected Coverage Results

After implementing all tests:

**ChatService.java**:
- Line Coverage: 100%
- Branch Coverage: 100% (no conditional logic)
- Method Coverage: 100% (both methods tested)

**ChatController.java**:
- Line Coverage: 100%
- Branch Coverage: 100% (systemMessage null check tested)
- Method Coverage: 100% (all 3 endpoints tested)

### Coverage Gotchas

**Lines that may show as not covered**:
1. **Constructors**: May not be counted if injection-only
2. **Generated code**: Lombok @Data methods (excluded from coverage)
3. **Exception paths**: Rare error conditions (document if intentionally not tested)

**What to do if coverage < 80%**:
1. Open coverage report in browser
2. Find red/yellow highlighted lines
3. Add test cases to cover those lines
4. Re-run `./gradlew test jacocoTestReport`
5. Verify coverage improved

## Test Execution Flow

### ChatServiceTest Execution

```
1. JUnit loads test class
2. Mockito initializes mocks (@Mock, @InjectMocks)
3. For each test:
   a. Mock behavior configured (when/thenReturn)
   b. Test method executes
   c. Assertions verify results
   d. Mockito verifications check interactions
   e. Mocks reset automatically
4. Test results collected
```

**Execution time**: ~500ms for all ChatServiceTest tests

### ChatControllerTest Execution

```
1. @WebMvcTest loads minimal Spring context (controllers + web layer only)
2. Spring Test initializes MockMvc
3. ChatService replaced with @MockBean
4. For each test:
   a. Mock behavior configured
   b. MockMvc performs HTTP request
   c. Spring processes request (deserialization, controller, serialization)
   d. Assertions verify HTTP response
   e. Mockito verifications check service calls
5. Test results collected
```

**Execution time**: ~2 seconds for all ChatControllerTest tests
- **Note**: @WebMvcTest much faster than @SpringBootTest (2s vs 10s+)

## Debugging Failed Tests

### Step 1: Read the Failure Message

```bash
./gradlew test --rerun-tasks

# Look for "FAILED" in output
# Read assertion error message
```

**Example failure**:
```
ChatServiceTest > shouldReturnResponseText_WhenValidMessageProvided() FAILED
    Expected: "Hello! How can I help?"
    Actual:   "Hello! How can I help you?"
                                    ^^^^ Difference here
```

### Step 2: Check Mock Configuration

**Common issues**:
- Mock not configured: `when()` statement missing
- Wrong argument matcher: `eq("exact")` vs `anyString()`
- Mock not reset: Previous test polluted state

**Fix**:
```java
// Before: May fail if exact string doesn't match
when(chatService.chat("Hello")).thenReturn("Response");

// After: More flexible matching
when(chatService.chat(anyString())).thenReturn("Response");
```

### Step 3: Enable Debug Logging

Add to `src/test/resources/application-test.yml`:
```yaml
logging:
  level:
    ai.hack: DEBUG
    org.springframework.test.web.servlet: DEBUG
```

Re-run tests:
```bash
./gradlew test --info
```

### Step 4: Use IDE Debugger

**IntelliJ IDEA**:
1. Right-click test method
2. Select "Debug 'test method name'"
3. Set breakpoints in test or production code
4. Step through execution

**VS Code with Java Extension**:
1. Click in gutter to set breakpoint
2. Click "Debug Test" above test method
3. Use debug controls to step through

### Step 5: Check Test Isolation

**Problem**: Test passes when run alone, fails when run with others

**Diagnosis**:
```bash
# Run test alone
./gradlew test --tests "ChatServiceTest.testMethod1"  # PASSES

# Run all tests
./gradlew test  # FAILS
```

**Likely cause**: Shared mutable state or test order dependency

**Fix**: Ensure each test:
- Sets up its own mocks in `@BeforeEach` or test method
- Doesn't modify static fields
- Doesn't depend on execution order

## Common Issues and Solutions

### Issue 1: Tests Compile But Don't Run

**Symptom**: `No tests found`

**Solution**:
```bash
# Ensure test classes end with "Test"
# Ensure test methods have @Test annotation
# Check package structure matches: src/test/java/ai/hack/...

# Clean and rebuild
./gradlew clean test
```

### Issue 2: MockMvc Returns 404

**Symptom**: `MockMvc.perform()` returns 404 Not Found

**Solution**:
```java
// Ensure @WebMvcTest specifies correct controller
@WebMvcTest(ChatController.class)  // Must specify controller

// Ensure request path matches exactly
perform(post("/api/chat"))  // Not "/chat" or "/api/chat/"
```

### Issue 3: JSON Deserialization Fails

**Symptom**: 400 Bad Request, cannot deserialize JSON

**Solution**:
```java
// Ensure Content-Type header set
.contentType(MediaType.APPLICATION_JSON)

// Ensure JSON properly escaped
.content("{\"message\":\"Test\"}")  // Escape quotes

// Or use Jackson ObjectMapper
ObjectMapper mapper = new ObjectMapper();
String json = mapper.writeValueAsString(new ChatRequest("Test", null));
.content(json)
```

### Issue 4: Coverage Report Not Generated

**Symptom**: `build/reports/jacoco` directory doesn't exist

**Solution**:
```bash
# Ensure JaCoCo plugin configured in build.gradle
# Run with explicit jacocoTestReport task
./gradlew test jacocoTestReport

# Check build.gradle has:
plugins {
    id 'jacoco'
}
```

## Performance Optimization

### Keeping Tests Fast

**Target**: <5 seconds for entire test suite

**Optimizations**:
1. **Use @WebMvcTest**: Loads only web layer (~2s vs ~10s for @SpringBootTest)
2. **Minimize @BeforeEach**: Only setup truly shared state
3. **Avoid Thread.sleep()**: Use Mockito timeouts if needed
4. **Parallel execution** (optional): Configure in build.gradle

```gradle
test {
    maxParallelForks = Runtime.runtime.availableProcessors().intdiv(2) ?: 1
}
```

### Measuring Test Performance

```bash
# Show test execution times
./gradlew test --info | grep "Test"

# Or check HTML report → Classes → Individual tests show execution time
```

## Integration with CI/CD

### GitHub Actions Example

```yaml
name: Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '21'
      - name: Run tests with coverage
        run: ./gradlew test jacocoTestReport
      - name: Upload coverage report
        uses: actions/upload-artifact@v3
        with:
          name: coverage-report
          path: build/reports/jacoco/test/html
```

### Enforcing Coverage Thresholds

Add to `build.gradle`:
```gradle
jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = 0.80  // Fail build if coverage < 80%
            }
        }
    }
}

check.dependsOn jacocoTestCoverageVerification
```

**Result**: `./gradlew check` fails if coverage below 80%

## Next Steps

After running tests successfully:

1. **Review coverage report**: Identify any uncovered lines
2. **Add missing tests**: If coverage < 80%
3. **Refactor tests**: Improve readability and maintainability
4. **Run in CI/CD**: Integrate into build pipeline
5. **Monitor performance**: Ensure tests stay fast (<5s)

## Quick Reference

```bash
# Essential commands
./gradlew test                          # Run all tests
./gradlew test jacocoTestReport         # Run tests + coverage
./gradlew test --tests "ClassName"      # Run specific test class
./gradlew clean test                    # Clean rebuild + test

# View reports
open build/reports/tests/test/index.html        # Test results
open build/reports/jacoco/test/html/index.html  # Coverage report

# Troubleshooting
./gradlew test --info                   # Verbose output
./gradlew test --debug                  # Debug output
./gradlew clean test                    # Clean rebuild
./gradlew test --rerun-tasks            # Force rerun
```

## Support

**Questions or issues**:
1. Check this quickstart guide
2. Review test contracts: `specs/001-chat-service-controller-tests/contracts/`
3. Check research document: `specs/001-chat-service-controller-tests/research.md`
4. Review test code for examples
5. Ask team for help

**Expected test results**:
- ✅ 24 tests passing (11 ChatServiceTest + 13 ChatControllerTest)
- ✅ 100% line coverage for ChatService
- ✅ 100% line coverage for ChatController
- ✅ Execution time < 5 seconds
- ✅ No flaky tests (consistent pass/fail)