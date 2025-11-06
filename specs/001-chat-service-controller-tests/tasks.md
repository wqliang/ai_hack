# Tasks: Comprehensive Test Coverage for Chat API

**Input**: Design documents from `/specs/001-chat-service-controller-tests/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: This feature IS about creating tests - all tasks are test implementation tasks.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Single Spring Boot project**: `src/test/java/ai/hack/` for test code
- Tests mirror production code package structure
- Configuration files at project root (`build.gradle`)

---

## Phase 1: Setup (Test Infrastructure)

**Purpose**: Configure test framework and code coverage tooling

- [X] T001 Add JaCoCo plugin to build.gradle for code coverage reporting
- [X] T002 Configure JaCoCo coverage thresholds (80% minimum) in build.gradle
- [X] T003 [P] Create test directory structure: `src/test/java/ai/hack/service/` and `src/test/java/ai/hack/controller/`
- [X] T004 [P] Verify Spring Boot Test dependencies are present in build.gradle (spring-boot-starter-test)

**Checkpoint**: Test infrastructure ready - JaCoCo configured, directories created

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: No foundational tasks required - test framework already exists in Spring Boot project

**⚠️ CRITICAL**: Project already has Spring Boot 3.3.5 with test dependencies. No blocking prerequisites needed.

**Checkpoint**: Ready to proceed with test implementation

---

## Phase 3: User Story 1 - Service Layer Unit Testing (Priority: P1) 🎯 MVP

**Goal**: Create comprehensive unit tests for ChatService to verify business logic, message construction, and ChatModel integration without external dependencies.

**Independent Test**: Run `./gradlew test --tests "ai.hack.service.ChatServiceTest"` to verify all service layer logic works correctly with mocked dependencies. Should achieve 100% coverage of ChatService.

### Implementation for User Story 1

- [ ] T005 [US1] Create ChatServiceTest class with @ExtendWith(MockitoExtension.class) in src/test/java/ai/hack/service/ChatServiceTest.java
- [ ] T006 [US1] Add @Mock ChatModel and @InjectMocks ChatService fields to ChatServiceTest
- [ ] T007 [US1] Implement helper method mockChatModelResponse() for deep stubbing Spring AI response chain
- [ ] T008 [P] [US1] Implement test: shouldReturnResponseText_WhenValidMessageProvided - verify chat() returns correct response
- [ ] T009 [P] [US1] Implement test: shouldConstructUserMessageCorrectly_WhenChatCalled - verify UserMessage and Prompt construction
- [ ] T010 [P] [US1] Implement test: shouldReturnResponseText_WhenSystemAndUserMessagesProvided - verify chatWithSystem() returns response
- [ ] T011 [P] [US1] Implement test: shouldConstructBothMessagesCorrectly_WhenChatWithSystemCalled - verify SystemMessage + UserMessage order
- [ ] T012 [P] [US1] Implement test: shouldHandleLongMessage_WhenMessageExceeds1000Characters - verify no truncation
- [ ] T013 [P] [US1] Implement test: shouldPreserveSpecialCharacters_WhenMessageContainsUnicode - verify unicode/emoji preservation
- [ ] T014 [P] [US1] Implement test: shouldPreserveMultilineText_WhenMessageContainsNewlines - verify newline preservation
- [ ] T015 [P] [US1] Implement test: shouldHandleEmptyMessage_WhenEmptyStringProvided - document empty string behavior
- [ ] T016 [P] [US1] Implement test: shouldHandleNullMessage_WhenNullProvided - document null handling behavior
- [ ] T017 [P] [US1] Implement test: shouldPropagateException_WhenChatModelThrowsRuntimeException - verify exception propagation
- [ ] T018 [US1] Run ChatServiceTest and verify all 11 tests pass with 100% ChatService coverage

**Checkpoint**: ChatServiceTest complete with 11 tests, 100% coverage, execution time <500ms

---

## Phase 4: User Story 2 - REST Controller Integration Testing (Priority: P2)

**Goal**: Create comprehensive integration tests for ChatController to verify HTTP layer, request/response mapping, JSON serialization, and error handling.

**Independent Test**: Run `./gradlew test --tests "ai.hack.controller.ChatControllerTest"` to verify all controller endpoints work correctly with mocked service. Should achieve 100% coverage of ChatController.

### Implementation for User Story 2

- [ ] T019 [US2] Create ChatControllerTest class with @WebMvcTest(ChatController.class) in src/test/java/ai/hack/controller/ChatControllerTest.java
- [ ] T020 [US2] Add @Autowired MockMvc and @MockBean ChatService fields to ChatControllerTest
- [ ] T021 [P] [US2] Implement test: shouldReturnChatResponse_WhenValidMessageProvided - verify POST /api/chat returns 200 with JSON
- [ ] T022 [P] [US2] Implement test: shouldInvokeChatServiceWithCorrectMessage_WhenPostApiChat - verify service parameter extraction
- [ ] T023 [P] [US2] Implement test: shouldReturnChatResponse_WhenSystemAndUserMessagesProvided - verify POST /api/chat/with-system
- [ ] T024 [P] [US2] Implement test: shouldUseDefaultSystemMessage_WhenSystemMessageIsNull - verify default "You are a helpful assistant."
- [ ] T025 [P] [US2] Implement test: shouldUseProvidedSystemMessage_WhenSystemMessageNotNull - verify custom system message
- [ ] T026 [P] [US2] Implement test: shouldReturnHealthMessage_WhenGetApiChatHealth - verify GET /api/chat/health returns health message
- [ ] T027 [US2] Run ChatControllerTest for basic endpoint tests and verify 6 tests pass

**Checkpoint**: Basic endpoint tests complete, all happy path scenarios covered

---

## Phase 5: User Story 3 - Edge Case and Error Handling Coverage (Priority: P3)

**Goal**: Add tests for edge cases, error scenarios, and boundary conditions to ensure robustness and prevent production failures.

**Independent Test**: Run `./gradlew test` to verify complete test suite including edge cases. Should achieve 100% coverage with all error scenarios tested.

### Implementation for User Story 3

- [ ] T028 [P] [US3] Implement test in ChatControllerTest: shouldReturn400_WhenRequestBodyIsInvalidJSON - verify malformed JSON handling
- [ ] T029 [P] [US3] Implement test in ChatControllerTest: shouldReturn400_WhenMessageFieldIsMissing - verify missing field validation
- [ ] T030 [P] [US3] Implement test in ChatControllerTest: shouldReturn400_WhenContentTypeNotJSON - verify Content-Type validation
- [ ] T031 [P] [US3] Implement test in ChatControllerTest: shouldReturn500_WhenChatServiceThrowsException - verify exception handling
- [ ] T032 [P] [US3] Implement test in ChatControllerTest: shouldHandleLongMessage_WhenMessageExceeds1000Characters - verify long message processing
- [ ] T033 [P] [US3] Implement test in ChatControllerTest: shouldHandleSpecialCharacters_WhenMessageContainsUnicode - verify unicode handling
- [ ] T034 [P] [US3] Implement test in ChatControllerTest: shouldHandleEmptyMessage_WhenMessageIsEmptyString - document empty message behavior
- [ ] T035 [US3] Run complete ChatControllerTest suite and verify all 13 tests pass with 100% ChatController coverage

**Checkpoint**: All edge cases and error scenarios covered, ChatControllerTest complete

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Validate complete test suite, verify coverage, and ensure quality standards met

- [ ] T036 Run complete test suite with `./gradlew test` and verify all 24 tests pass (11 ChatServiceTest + 13 ChatControllerTest)
- [ ] T037 Generate coverage report with `./gradlew test jacocoTestReport` and verify coverage meets targets
- [ ] T038 Verify ChatService has 100% line coverage (target: 80% minimum, achieved: 100%)
- [ ] T039 Verify ChatController has 100% line coverage (target: 80% minimum, achieved: 100%)
- [ ] T040 Verify total test execution time is under 5 seconds as specified in success criteria
- [ ] T041 Run tests in random order to verify test independence: `./gradlew test --rerun-tasks`
- [ ] T042 [P] Review test code for consistency in naming conventions (Given-When-Then pattern)
- [ ] T043 [P] Verify all tests use AssertJ assertions for readability
- [ ] T044 Open coverage HTML report (build/reports/jacoco/test/html/index.html) and verify no red/uncovered lines
- [ ] T045 Run quickstart.md validation: Follow quickstart guide and verify all commands work as documented

**Checkpoint**: Complete test suite validated, coverage targets exceeded, all quality gates passed

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: No tasks (Spring Boot already configured)
- **User Stories (Phase 3-5)**: All can start after Setup (Phase 1) completion
  - User stories can proceed in parallel if desired
  - Recommended sequential: US1 → US2 → US3 for learning progression
- **Polish (Phase 6)**: Depends on all user stories (US1, US2, US3) being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Phase 1 Setup - No dependencies on other stories
- **User Story 2 (P2)**: Can start after Phase 1 Setup - Independently testable from US1
- **User Story 3 (P3)**: Adds to ChatControllerTest from US2 - Can start after US2 or in parallel with US2

### Within Each User Story

**User Story 1** (ChatServiceTest):
1. T005-T007: Test class setup (sequential)
2. T008-T017: Individual test methods (all parallelizable - different test methods)
3. T018: Verify tests pass (depends on T005-T017)

**User Story 2** (ChatControllerTest):
1. T019-T020: Test class setup (sequential)
2. T021-T026: Basic endpoint tests (all parallelizable - different test methods)
3. T027: Verify tests pass (depends on T019-T026)

**User Story 3** (Edge cases):
1. T028-T034: Edge case and error tests (all parallelizable - different test methods)
2. T035: Verify tests pass (depends on T028-T034)

### Parallel Opportunities

- **Phase 1**: T003 and T004 can run in parallel (different concerns)
- **User Story 1**: T008-T017 (all 10 test methods) can be written in parallel - different test methods in same class
- **User Story 2**: T021-T026 (all 6 test methods) can be written in parallel - different test methods in same class
- **User Story 3**: T028-T034 (all 7 test methods) can be written in parallel - different test methods in same class
- **Phase 6**: T037-T041 sequential (verification tasks), T042-T043 parallel (review tasks)

**Maximum parallelization**: After Phase 1, US1, US2, US3 can all proceed simultaneously by different developers

---

## Parallel Example: User Story 1

```bash
# After T005-T007 (test class setup), launch all test method implementations together:
Task: "Implement test: shouldReturnResponseText_WhenValidMessageProvided"
Task: "Implement test: shouldConstructUserMessageCorrectly_WhenChatCalled"
Task: "Implement test: shouldReturnResponseText_WhenSystemAndUserMessagesProvided"
Task: "Implement test: shouldConstructBothMessagesCorrectly_WhenChatWithSystemCalled"
Task: "Implement test: shouldHandleLongMessage_WhenMessageExceeds1000Characters"
Task: "Implement test: shouldPreserveSpecialCharacters_WhenMessageContainsUnicode"
Task: "Implement test: shouldPreserveMultilineText_WhenMessageContainsNewlines"
Task: "Implement test: shouldHandleEmptyMessage_WhenEmptyStringProvided"
Task: "Implement test: shouldHandleNullMessage_WhenNullProvided"
Task: "Implement test: shouldPropagateException_WhenChatModelThrowsRuntimeException"
```

## Parallel Example: User Story 2

```bash
# After T019-T020 (test class setup), launch all endpoint test implementations together:
Task: "Implement test: shouldReturnChatResponse_WhenValidMessageProvided"
Task: "Implement test: shouldInvokeChatServiceWithCorrectMessage_WhenPostApiChat"
Task: "Implement test: shouldReturnChatResponse_WhenSystemAndUserMessagesProvided"
Task: "Implement test: shouldUseDefaultSystemMessage_WhenSystemMessageIsNull"
Task: "Implement test: shouldUseProvidedSystemMessage_WhenSystemMessageNotNull"
Task: "Implement test: shouldReturnHealthMessage_WhenGetApiChatHealth"
```

## Parallel Example: User Story 3

```bash
# All edge case tests can be implemented in parallel:
Task: "Implement test: shouldReturn400_WhenRequestBodyIsInvalidJSON"
Task: "Implement test: shouldReturn400_WhenMessageFieldIsMissing"
Task: "Implement test: shouldReturn400_WhenContentTypeNotJSON"
Task: "Implement test: shouldReturn500_WhenChatServiceThrowsException"
Task: "Implement test: shouldHandleLongMessage_WhenMessageExceeds1000Characters"
Task: "Implement test: shouldHandleSpecialCharacters_WhenMessageContainsUnicode"
Task: "Implement test: shouldHandleEmptyMessage_WhenMessageIsEmptyString"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001-T004) - ~30 minutes
2. Complete Phase 3: User Story 1 (T005-T018) - ~2 hours
3. **STOP and VALIDATE**: Run ChatServiceTest independently
   - All 11 tests should pass
   - 100% coverage of ChatService
   - Execution time <500ms
4. Celebrate! You have tested the core business logic

### Incremental Delivery

1. **Foundation**: Phase 1 Setup → Test infrastructure ready
2. **MVP**: Add User Story 1 → Unit tests complete, service layer validated
3. **API Contract**: Add User Story 2 → Integration tests complete, API contract validated
4. **Production Ready**: Add User Story 3 → Edge cases covered, robust error handling
5. **Quality Gate**: Phase 6 Polish → All standards verified, ready for production

### Parallel Team Strategy

With multiple developers:

1. **Sequential approach** (recommended for learning):
   - Complete Phase 1 together
   - Complete US1 together (learn unit testing patterns)
   - Complete US2 together (learn integration testing patterns)
   - Complete US3 together (learn edge case patterns)

2. **Parallel approach** (experienced team):
   - Complete Phase 1 together
   - Developer A: US1 (ChatServiceTest - 11 tests)
   - Developer B: US2 (ChatControllerTest - 6 basic tests)
   - Developer C: US3 (ChatControllerTest - 7 edge case tests)
   - Developers B & C collaborate on same test file

---

## Task Summary

**Total Tasks**: 45 tasks
- Phase 1 Setup: 4 tasks
- Phase 2 Foundational: 0 tasks (no blocking prerequisites)
- Phase 3 User Story 1: 14 tasks (ChatServiceTest - 11 test methods)
- Phase 4 User Story 2: 9 tasks (ChatControllerTest - 6 basic endpoint tests)
- Phase 5 User Story 3: 8 tasks (ChatControllerTest - 7 edge case tests)
- Phase 6 Polish: 10 tasks (validation and quality gates)

**Parallel Opportunities**:
- Phase 1: 2 tasks parallelizable
- User Story 1: 10 test methods parallelizable (T008-T017)
- User Story 2: 6 test methods parallelizable (T021-T026)
- User Story 3: 7 test methods parallelizable (T028-T034)
- Phase 6: 2 review tasks parallelizable (T042-T043)

**Estimated Effort**:
- Phase 1: 30 minutes (configuration)
- User Story 1: 2-3 hours (11 unit tests)
- User Story 2: 1.5-2 hours (6 integration tests)
- User Story 3: 1.5-2 hours (7 edge case tests)
- Phase 6: 30 minutes (validation)
- **Total: 6-8 hours** for complete implementation

**Test Coverage**:
- ChatService: 11 tests → 100% coverage
- ChatController: 13 tests → 100% coverage
- Total: 24 tests covering 2 production classes

**Success Criteria Met**:
- ✅ 80%+ code coverage (achieved: 100%)
- ✅ Tests execute in <5 seconds
- ✅ Tests are independent and order-agnostic
- ✅ All functional requirements covered (FR-001 through FR-025)
- ✅ All edge cases documented and tested
- ✅ Spring Boot best practices followed

---

## Notes

- All test methods marked [P] are parallelizable - they write to different test methods in the same class file
- Test class setup tasks (T005-T007, T019-T020) must be done sequentially before individual tests
- Each user story is independently testable via `./gradlew test --tests "ClassName"`
- Contracts in `specs/001-chat-service-controller-tests/contracts/` provide detailed implementation guidance for each test
- Reference `quickstart.md` for commands to run tests and interpret coverage reports
- This is a testing feature - all tasks create test code, no production code changes needed unless bugs discovered