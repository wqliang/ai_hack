# Tasks: RocketMQ RPC Client Wrapper

**Input**: Design documents from `specs/004-rocketmq-rpc-client/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: Tests are included based on constitution requirement IV (Testing Discipline) - unit tests for services, integration tests for RPC functionality.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3, US4)
- Include exact file paths in descriptions

## Path Conventions

- **Single project**: `src/main/java/ai/hack/`, `src/test/java/ai/hack/` at repository root
- Paths assume single Java project structure as defined in plan.md

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [X] T001 Create package structure `src/main/java/ai/hack/rocketmq/client/` with subpackages: config, exception, model
- [X] T002 [P] Create test package structure `src/test/java/ai/hack/rocketmq/client/` with subpackages: unit, integration
- [X] T003 [P] Create `src/test/resources/application-test.yml` with test RocketMQ configuration
- [X] T004 [P] Verify RocketMQ 5.3.3 client dependency in build.gradle (already present)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T005 [P] Create `RpcException` base exception class in `src/main/java/ai/hack/rocketmq/client/exception/RpcException.java`
- [X] T006 [P] Create `RpcTimeoutException` extending RpcException in `src/main/java/ai/hack/rocketmq/client/exception/RpcTimeoutException.java`
- [X] T007 [P] Create `CorrelationException` extending RpcException in `src/main/java/ai/hack/rocketmq/client/exception/CorrelationException.java`
- [X] T008 [P] Create `SessionException` extending RpcException in `src/main/java/ai/hack/rocketmq/client/exception/SessionException.java`
- [X] T009 [P] Create `RpcRequest` record in `src/main/java/ai/hack/rocketmq/client/model/RpcRequest.java` with validation
- [X] T010 [P] Create `RpcResponse` record in `src/main/java/ai/hack/rocketmq/client/model/RpcResponse.java` with success/error factory methods
- [X] T011 [P] Create `MessageMetadata` record in `src/main/java/ai/hack/rocketmq/client/model/MessageMetadata.java` with RocketMQ message conversion methods
- [X] T012 [P] Create `RpcClientConfig` class in `src/main/java/ai/hack/rocketmq/client/config/RpcClientConfig.java` with @ConfigurationProperties
- [X] T013 Create `RpcClient` interface in `src/main/java/ai/hack/rocketmq/client/RpcClient.java` with method signatures for sync/async/streaming
- [X] T014 Create `RpcReceiver` interface in `src/main/java/ai/hack/rocketmq/client/RpcReceiver.java` for processing requests
- [X] T015 Create `CorrelationManager` class in `src/main/java/ai/hack/rocketmq/client/CorrelationManager.java` with ConcurrentHashMap for tracking pending requests

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Synchronous RPC Call (Priority: P1) 🎯 MVP

**Goal**: Enable synchronous RPC-style communication where a sender sends a request and blocks until response is received or timeout occurs

**Independent Test**: Send a single request message synchronously, verify blocking behavior, receive correlated response, handle timeout scenarios

### Tests for User Story 1

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [ ] T016 [P] [US1] Create unit test `CorrelationManagerTest.java` in `src/test/java/ai/hack/rocketmq/client/unit/` testing correlation tracking and timeout
- [ ] T017 [P] [US1] Create integration test `RpcClientSyncTest.java` in `src/test/java/ai/hack/rocketmq/client/integration/` with TestContainers for end-to-end sync RPC

### Implementation for User Story 1

- [ ] T018 [P] [US1] Create `MessageSender` class in `src/main/java/ai/hack/rocketmq/client/MessageSender.java` wrapping RocketMQ Producer with send() method
- [ ] T019 [P] [US1] Create `MessageReceiver` class in `src/main/java/ai/hack/rocketmq/client/MessageReceiver.java` wrapping RocketMQ Consumer with message listener
- [ ] T020 [US1] Implement `RpcClientImpl` class in `src/main/java/ai/hack/rocketmq/client/RpcClientImpl.java` with constructor injection for Producer/Consumer
- [ ] T021 [US1] Implement `sendSync()` method in RpcClientImpl: generate correlation ID, create RpcRequest, send via MessageSender, block on CompletableFuture
- [ ] T022 [US1] Implement response topic subscription in RpcClientImpl: create topic with RESPONSE_{senderId} pattern, subscribe via MessageReceiver
- [ ] T023 [US1] Implement correlation matching in RpcClientImpl: extract correlationId from response, complete corresponding CompletableFuture
- [ ] T024 [US1] Implement timeout handling in RpcClientImpl: use ScheduledExecutorService to schedule timeout tasks, complete future with RpcTimeoutException
- [ ] T025 [US1] Implement `start()` lifecycle method in RpcClientImpl: start Producer and Consumer, initialize ScheduledExecutorService
- [ ] T026 [US1] Implement `close()` lifecycle method with @PreDestroy in RpcClientImpl: shutdown resources, cancel pending requests, cleanup executor
- [ ] T027 [US1] Add JavaDoc comments to all classes and methods following constitution requirement I (Code Documentation)
- [ ] T028 [US1] Add inline comments for complex logic (correlation matching, timeout scheduling) following constitution requirement I

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently. Run `./gradlew test --tests "*RpcClientSyncTest"` to verify.

---

## Phase 4: User Story 2 - Asynchronous RPC Call (Priority: P1)

**Goal**: Enable non-blocking asynchronous RPC calls using CompletableFuture for high-throughput scenarios

**Independent Test**: Send requests asynchronously, verify non-blocking behavior, receive responses via CompletableFuture callbacks, handle concurrent requests

### Tests for User Story 2

- [ ] T029 [P] [US2] Create integration test `RpcClientAsyncTest.java` in `src/test/java/ai/hack/rocketmq/client/integration/` testing async calls and concurrent requests
- [ ] T030 [P] [US2] Create unit test `MessageSenderTest.java` in `src/test/java/ai/hack/rocketmq/client/unit/` testing async send with callbacks

### Implementation for User Story 2

- [ ] T031 [US2] Implement `sendAsync()` method in RpcClientImpl: create CompletableFuture, send via Producer.sendAsync() with callback
- [ ] T032 [US2] Implement async timeout handling: schedule timeout task that completes future exceptionally if no response
- [ ] T033 [US2] Implement concurrent request handling: verify ConcurrentHashMap in CorrelationManager supports multiple in-flight requests
- [ ] T034 [US2] Add max concurrent requests validation in RpcClientImpl: check against RpcClientConfig.maxConcurrentRequests
- [ ] T035 [US2] Add JavaDoc for async methods documenting CompletableFuture behavior and exception handling
- [ ] T036 [US2] Add integration between sendSync() and sendAsync(): implement sync by calling async and blocking with future.get(timeout)

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently. Run `./gradlew test --tests "*RpcClient*Test"` to verify both.

---

## Phase 5: User Story 3 - Streaming Request with Single Response (Priority: P2)

**Goal**: Enable sending multiple request messages in a stream (with same session ID) to be processed by same receiver, receiving single aggregated response

**Independent Test**: Start streaming session, send multiple messages with same sessionId, verify routing to same queue, receive single aggregated response

### Foundational Components for User Story 3

- [ ] T037 [P] [US3] Create `StreamingSession` record in `src/main/java/ai/hack/rocketmq/client/model/StreamingSession.java` with session metadata
- [ ] T038 [US3] Create `SessionManager` class in `src/main/java/ai/hack/rocketmq/client/SessionManager.java` for managing active streaming sessions

### Tests for User Story 3

- [ ] T039 [P] [US3] Create unit test `SessionManagerTest.java` in `src/test/java/ai/hack/rocketmq/client/unit/` testing session lifecycle and validation
- [ ] T040 [P] [US3] Create integration test `RpcClientStreamingTest.java` in `src/test/java/ai/hack/rocketmq/client/integration/` testing message ordering within sessions

### Implementation for User Story 3

- [ ] T041 [US3] Implement `sendStreamingStart()` method in RpcClientImpl: generate sessionId (UUID), create StreamingSession, register in SessionManager
- [ ] T042 [US3] Implement `sendStreamingMessage()` method in RpcClientImpl: validate sessionId exists, create RpcRequest with sessionId, send with MessageQueueSelector
- [ ] T043 [US3] Implement MessageQueueSelector in MessageSender: use sessionId.hashCode() % queueCount to ensure same queue selection
- [ ] T044 [US3] Implement `sendStreamingEnd()` method in RpcClientImpl: send end marker message, wait for final response with timeout, deactivate session
- [ ] T045 [US3] Implement session validation: throw SessionException if sessionId unknown or inactive
- [ ] T046 [US3] Implement session activity tracking: update StreamingSession.lastActivityAt on each message send
- [ ] T047 [US3] Add max concurrent sessions validation: check against RpcClientConfig.maxConcurrentSessions
- [ ] T048 [US3] Add JavaDoc for streaming methods documenting session semantics and ordering guarantees

**Checkpoint**: All user stories (US1, US2, US3) should now be independently functional. Test streaming with multiple messages.

---

## Phase 6: User Story 4 - Bidirectional Streaming (Priority: P2)

**Goal**: Enable full-duplex streaming where both request and response messages flow incrementally in real-time (e.g., AI chat)

**Independent Test**: Start bidirectional session, send request stream, receive response stream incrementally, verify correlation and resource cleanup

### Tests for User Story 4

- [ ] T049 [P] [US4] Extend `RpcClientStreamingTest.java` with bidirectional streaming tests: send multiple requests, receive multiple responses incrementally
- [ ] T050 [P] [US4] Create unit test for streaming response handler: verify callbacks invoked as responses arrive

### Implementation for User Story 4

- [ ] T051 [US4] Extend MessageReceiver to support streaming response delivery: invoke callback for each incremental response in a session
- [ ] T052 [US4] Implement streaming response correlation in CorrelationManager: track sessionId → List<CompletableFuture> mapping for multiple responses
- [ ] T053 [US4] Update sendStreamingMessage() to support response callbacks: accept optional consumer for incremental responses
- [ ] T054 [US4] Implement session completion signaling: detect end-of-stream marker from receiver, complete all pending futures
- [ ] T055 [US4] Implement proper session cleanup on completion: remove from SessionManager, complete/cancel all futures, release resources
- [ ] T056 [US4] Add bidirectional stream timeout handling: timeout entire session if idle for configured period
- [ ] T057 [US4] Add JavaDoc for bidirectional streaming methods documenting callback semantics and resource management

**Checkpoint**: All user stories should now be independently functional including full bidirectional streaming capability.

---

## Phase 7: Configuration and Auto-Configuration

**Purpose**: Spring Boot integration and configuration management

- [ ] T058 [P] Create `RpcClientAutoConfiguration` class in `src/main/java/ai/hack/rocketmq/client/config/RpcClientAutoConfiguration.java` with @Configuration
- [ ] T059 [P] Implement @Bean methods in RpcClientAutoConfiguration: create RpcClientImpl bean with constructor injection of RocketMQ Producer/Consumer
- [ ] T060 [P] Create `src/main/resources/META-INF/spring.factories` for auto-configuration registration
- [ ] T061 [P] Add default configuration properties in `src/main/resources/application.yml` for rocketmq.rpc.client.*
- [ ] T062 [P] Add JSR-303 validation annotations to RpcClientConfig: @NotBlank for brokerUrl, @Min/@Max for timeouts
- [ ] T063 [P] Add configuration metadata file `src/main/resources/META-INF/spring-configuration-metadata.json` for IDE auto-completion
- [ ] T064 Update quickstart.md with final configuration examples and actual usage patterns

---

## Phase 8: Receiver Implementation Support

**Purpose**: Enable developers to implement custom request processing logic

- [ ] T065 [P] Create example receiver `EchoReceiver` in `src/main/java/ai/hack/rocketmq/client/example/EchoReceiver.java` demonstrating RpcReceiver interface
- [ ] T066 [P] Implement processRequest() in EchoReceiver: simple echo of request payload
- [ ] T067 [P] Implement processStreamingRequest() in EchoReceiver: concatenate all messages in session
- [ ] T068 [P] Add JavaDoc to RpcReceiver interface explaining implementation contract and thread safety requirements
- [ ] T069 Create receiver registration mechanism: allow RpcClientImpl to accept RpcReceiver implementation via constructor or setter
- [ ] T070 Implement receiver invocation in MessageReceiver: extract request, call RpcReceiver.processRequest(), send response to sender's topic

---

## Phase 9: Error Handling and Edge Cases

**Purpose**: Robust error handling for production scenarios

- [ ] T071 [P] Implement broker unavailability handling in MessageSender: fail fast with clear RpcException on send failures
- [ ] T072 [P] Implement response topic creation failure handling: retry with exponential backoff, throw RpcException if persistent failure
- [ ] T073 [P] Implement abandoned request cleanup: periodic task to remove timed-out correlation entries from ConcurrentHashMap
- [ ] T074 [P] Implement session idle timeout: SessionManager removes inactive sessions exceeding configured idle period
- [ ] T075 [P] Add error handling for correlation ID mismatch: log warning and discard unknown responses
- [ ] T076 [P] Implement graceful degradation on resource exhaustion: reject new requests when max concurrent limit reached
- [ ] T077 Add comprehensive error logging with SLF4J: log all exceptions with context (correlationId, sessionId, etc.)

---

## Phase 10: Performance Optimization

**Purpose**: Ensure performance goals are met (<100ms latency, 1000+ concurrent requests)

- [ ] T078 [P] Implement connection pooling configuration for RocketMQ Producer: tune producer group and instance count
- [ ] T079 [P] Configure consumer thread pool size in MessageReceiver: match expected throughput from RpcClientConfig
- [ ] T080 [P] Implement bounded ConcurrentHashMap size in CorrelationManager: use eviction policy to prevent memory leaks
- [ ] T081 [P] Tune ScheduledExecutorService thread pool: dedicated executor for timeout tasks separate from message processing
- [ ] T082 [P] Add performance metrics collection: track request latencies, timeout rates, concurrent request counts
- [ ] T083 Run performance tests: verify <100ms latency goal (excluding processing time), 1000+ concurrent async requests

---

## Phase 11: Integration Testing with TestContainers

**Purpose**: Comprehensive end-to-end testing with embedded RocketMQ broker

- [ ] T084 Create `EmbeddedRocketMQConfig` class in `src/test/java/ai/hack/rocketmq/client/integration/EmbeddedRocketMQConfig.java` using TestContainers
- [ ] T085 Implement RocketMQ NameServer container setup in EmbeddedRocketMQConfig: configure with minimal resources
- [ ] T086 Implement RocketMQ Broker container setup in EmbeddedRocketMQConfig: link to NameServer, configure test topics
- [ ] T087 [P] Create integration test for sync RPC happy path: send request, receive response, verify correlation
- [ ] T088 [P] Create integration test for async RPC happy path: multiple concurrent requests, verify all responses received
- [ ] T089 [P] Create integration test for timeout scenario: verify RpcTimeoutException thrown after configured timeout
- [ ] T090 [P] Create integration test for streaming: send 10 messages in session, verify ordering and single aggregated response
- [ ] T091 [P] Create integration test for bidirectional streaming: send/receive incremental messages, verify correlation
- [ ] T092 [P] Create integration test for error scenarios: invalid sessionId, exceeded concurrency limits, broker unavailability
- [ ] T093 Create integration test suite runner: execute all integration tests with @SpringBootTest and embedded broker
- [ ] T094 Verify test coverage meets 70%+ threshold using JaCoCo: run `./gradlew jacocoTestReport`

---

## Phase 12: Documentation and Polish

**Purpose**: Final documentation, code cleanup, and production readiness

- [ ] T095 [P] Complete JavaDoc for all public classes: RpcClient, RpcClientImpl, RpcReceiver, all models, all exceptions
- [ ] T096 [P] Complete JavaDoc for all public methods: include @param, @return, @throws, and usage examples
- [ ] T097 [P] Add package-info.java files in each package with package-level documentation
- [ ] T098 [P] Review and enhance inline comments for complex algorithms: correlation matching, session routing, timeout handling
- [ ] T099 [P] Create quickstart examples in quickstart.md: sync call, async call, streaming, bidirectional
- [ ] T100 [P] Update README.md or CLAUDE.md with RPC client usage examples and configuration guide
- [ ] T101 Run code quality checks: verify no warnings from IntelliJ IDEA inspections, no checkstyle violations
- [ ] T102 Run security scan: verify no security vulnerabilities in dependencies using `./gradlew dependencyCheckAnalyze`
- [ ] T103 Final build verification: `./gradlew clean build` must pass with all tests green
- [ ] T104 Run quickstart validation: follow quickstart.md step-by-step, verify all examples work

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Story 1 (Phase 3)**: Depends on Foundational phase completion - BLOCKS US2 (async builds on sync)
- **User Story 2 (Phase 4)**: Depends on US1 completion (sync implementation) - Independent from US3/US4
- **User Story 3 (Phase 5)**: Depends on Foundational phase completion - Can start after Foundational or in parallel with US1/US2
- **User Story 4 (Phase 6)**: Depends on US3 completion (builds on streaming foundation)
- **Configuration (Phase 7)**: Can start after Foundational, parallelize with user stories
- **Receiver Support (Phase 8)**: Can start after US1 completion
- **Error Handling (Phase 9)**: Can start after US1 completion, parallelize with other phases
- **Performance (Phase 10)**: Depends on US1+US2 completion
- **Integration Tests (Phase 11)**: Depends on respective user story implementation
- **Documentation (Phase 12)**: Depends on all user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) - No dependencies on other stories ✅ MVP READY
- **User Story 2 (P1)**: Builds on User Story 1 implementation - Reuses sync infrastructure for async
- **User Story 3 (P2)**: Can start after Foundational (Phase 2) - Independent from US1/US2 but typically follows
- **User Story 4 (P2)**: Depends on User Story 3 - Extends streaming with bidirectional capability

### Within Each User Story

- Tests (if included) MUST be written and FAIL before implementation
- Models/Records before services
- Core managers (CorrelationManager, SessionManager) before client implementation
- Client interface before implementation class
- Implementation methods in order: lifecycle (start/close) → sync → async → streaming
- JavaDoc and inline comments after implementation code works

### Parallel Opportunities

**Setup Phase (Phase 1)**:
- All tasks T001-T004 can run in parallel

**Foundational Phase (Phase 2)**:
- Exception classes (T005-T008) can run in parallel
- Model records (T009-T011) can run in parallel
- T012 (Config) can run in parallel with exceptions/models
- T013-T015 must run sequentially (interface dependencies)

**User Story 1 Tests (Phase 3)**:
- T016 and T017 can run in parallel

**User Story 1 Implementation (Phase 3)**:
- T018 (MessageSender) and T019 (MessageReceiver) can run in parallel
- All JavaDoc tasks (T027, T028) can run in parallel after code completion

**User Story 2 Tests (Phase 4)**:
- T029 and T030 can run in parallel

**User Story 3 Foundational (Phase 5)**:
- T037 (StreamingSession record) and T038 (SessionManager) can have T037 run first

**User Story 3 Tests (Phase 5)**:
- T039 and T040 can run in parallel

**Configuration Phase (Phase 7)**:
- All tasks T058-T064 can run in parallel (different files)

**Receiver Support (Phase 8)**:
- T065-T067 can run in parallel, T068-T070 sequential

**Error Handling (Phase 9)**:
- All tasks T071-T077 can run in parallel (different error scenarios)

**Performance (Phase 10)**:
- All tasks T078-T082 can run in parallel

**Integration Tests (Phase 11)**:
- T085-T086 must run sequentially (container setup dependency)
- T087-T092 can run in parallel (different test scenarios)

**Documentation (Phase 12)**:
- T095-T102 can run in parallel, T103-T104 must run sequentially at end

---

## Parallel Example: User Story 1 Implementation

```bash
# Launch MessageSender and MessageReceiver in parallel:
Task T018: "Create MessageSender class in src/main/java/ai/hack/rocketmq/client/MessageSender.java"
Task T019: "Create MessageReceiver class in src/main/java/ai/hack/rocketmq/client/MessageReceiver.java"

# After both complete, implement RpcClientImpl:
Task T020: "Implement RpcClientImpl class in src/main/java/ai/hack/rocketmq/client/RpcClientImpl.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (4 tasks)
2. Complete Phase 2: Foundational (11 tasks) - CRITICAL BLOCKING PHASE
3. Complete Phase 3: User Story 1 (13 tasks)
4. **STOP and VALIDATE**: Run `./gradlew test --tests "*RpcClientSyncTest"` - verify synchronous RPC works end-to-end
5. Deploy/demo if ready - **You now have a working MVP!**

**MVP Scope**: Synchronous RPC calls with correlation, timeout handling, and lifecycle management. This represents the minimum viable product for RPC-style communication.

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready (15 tasks)
2. Add User Story 1 → Test independently → Deploy/Demo **(MVP! ~28 tasks total)**
3. Add User Story 2 → Test independently → Deploy/Demo (async RPC capability)
4. Add User Story 3 → Test independently → Deploy/Demo (streaming requests)
5. Add User Story 4 → Test independently → Deploy/Demo (bidirectional streaming)
6. Add Configuration + Receiver Support + Error Handling → Productionize
7. Add Performance + Integration Tests → Validate at scale
8. Add Documentation → Complete and ready for adoption

Each increment adds value without breaking previous stories.

### Parallel Team Strategy

With multiple developers:

1. **Team completes Setup + Foundational together** (critical path, ~15 tasks)
2. Once Foundational is done, split work:
   - **Developer A**: User Story 1 (sync RPC) - 13 tasks
   - **Developer B**: Configuration (Phase 7) - 7 tasks
   - **Developer C**: Error Handling (Phase 9) - 7 tasks
3. After US1 complete:
   - **Developer A**: User Story 2 (async RPC) - 8 tasks
   - **Developer B**: Receiver Support (Phase 8) - 6 tasks
   - **Developer D**: Integration Tests setup (Phase 11, T084-T086)
4. After US2 complete:
   - **Developer A**: User Story 3 (streaming) - 11 tasks
   - **Developers B+C**: Performance optimization (Phase 10) - 6 tasks
5. After US3 complete:
   - **Developer A**: User Story 4 (bidirectional) - 7 tasks
   - **Developer D**: Integration tests (Phase 11, T087-T094) - 11 tasks
6. Final polish: All developers on Documentation (Phase 12) - 10 tasks

---

## Notes

- [P] tasks = different files, no dependencies, safe to parallelize
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Tests written first (fail) before implementation (TDD encouraged by constitution)
- Commit after each task or logical group of tasks
- Stop at any checkpoint to validate story independently
- Run `./gradlew build` frequently to catch errors early
- Follow constitution: JavaDoc all classes/methods, inline comments for complex logic
- Avoid: vague tasks, same file conflicts, cross-story dependencies that break independence

---

## Task Count Summary

- **Phase 1 (Setup)**: 4 tasks
- **Phase 2 (Foundational)**: 11 tasks
- **Phase 3 (US1 - Sync RPC)**: 13 tasks
- **Phase 4 (US2 - Async RPC)**: 8 tasks
- **Phase 5 (US3 - Streaming Request)**: 11 tasks
- **Phase 6 (US4 - Bidirectional Streaming)**: 7 tasks
- **Phase 7 (Configuration)**: 7 tasks
- **Phase 8 (Receiver Support)**: 6 tasks
- **Phase 9 (Error Handling)**: 7 tasks
- **Phase 10 (Performance)**: 6 tasks
- **Phase 11 (Integration Tests)**: 11 tasks
- **Phase 12 (Documentation)**: 10 tasks

**Total**: 104 tasks

**MVP (US1 only)**: 28 tasks (Setup + Foundational + US1)
**P1 Complete (US1+US2)**: 36 tasks
**All User Stories (US1-US4)**: 54 tasks
**Production Ready (All phases)**: 104 tasks
