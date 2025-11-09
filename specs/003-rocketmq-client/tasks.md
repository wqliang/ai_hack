---

description: "Task list for RocketMQ Async Client implementation"
---

# Tasks: RocketMQ Async Client

**Input**: Design documents from `/specs/001-rocketmq-client/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: The examples below include test tasks. Tests are OPTIONAL - only include them if explicitly requested in the feature specification.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Single project**: `src/`, `tests/` at repository root
- **Web app**: `backend/src/`, `frontend/src/`
- **Mobile**: `api/src/`, `ios/src/` or `android/src/`
- Paths shown below assume single project - adjust based on plan.md structure

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [X] T001 Create RocketMQ client package structure per research.md recommendations
- [X] T002 Initialize Java 21 project with Spring Boot 3.3.5 and RocketMQ 5.3.3 dependencies
- [X] T003 [P] configure Gradle build script with multi-release JAR support for Java 17/21 compatibility
- [X] T004 [P] Setup code formatting and linting tools for Java
- [X] T005 [P] Configure TestContainers for integration testing

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T006 Setup RocketMQ connection infrastructure with TLS support in src/main/java/ai/hack/rocketmq/core/
- [X] T007 [P] Implement core exception hierarchy in src/main/java/ai/hack/rocketmq/exception/
- [X] T008 [P] Create configuration management with builder pattern in src/main/java/ai/hack/rocketmq/config/
- [X] T009 [P] Setup persistence layer with RocksDB and H2 in src/main/java/ai/hack/rocketmq/persistence/
- [X] T010 [P] Implement security context for TLS and authentication in src/main/java/ai/hack/rocketmq/security/
- [X] T011 [P] Setup metrics collection infrastructure in src/main/java/ai/hack/rocketmq/monitoring/
- [X] T012 [P] Create base message entity and enums in src/main/java/ai/hack/rocketmq/model/

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Async Message Publishing (Priority: P1) 🎯 MVP

**Goal**: Enable applications to embed a RocketMQ client library that publishes messages asynchronously to RocketMQ topics with TLS encryption and authentication without blocking operations.

**Independent Test**: Can be fully tested by publishing a message to a test topic and verifying it was delivered without blocking the calling thread.

### Tests for User Story 1 (OPTIONAL - only if tests requested) ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [X] T013 [P] [US1] Contract test for async message publishing in src/test/java/ai/hack/rocketmq/UserStory1AsyncPublishTest.java
- [X] T014 [P] [US1] Integration test for message delivery verification in src/test/java/ai/hack/rocketmq/UserStory1AsyncPublishTest.java

### Implementation for User Story 1

- [X] T015 [P] [US1] Create MessagePublisher class in src/main/java/ai/hack/rocketmq/core/MessagePublisher.java
- [X] T016 [US1] Implement DefaultRocketMQAsyncClient core client in src/main/java/ai/hack/rocketmq/DefaultRocketMQAsyncClient.java (depends on T015, T006, T008)
- [X] T017 [US1] Implement supporting classes MessageCallback, MessageProcessingResult, SendResult, BatchSendResult, ClientStatus, ClientState
- [X] T018 [US1] Add message size validation and error handling in src/main/java/ai/hack/rocketmq/core/MessagePublisher.java
- [X] T019 [US1] Implement retry logic for failed send operations in src/main/java/ai/hack/rocketmq/core/MessagePublisher.java
- [X] T020 [US1] Add message compression support in src/main/java/ai/hack/rocketmq/core/MessagePublisher.java
- [X] T021 [US1] Integrate TLS authentication in message publishing flow in src/main/java/ai/hack/rocketmq/core/MessagePublisher.java
- [X] T022 [US1] Add FIFO ordering guarantees per topic in src/main/java/ai/hack/rocketmq/core/MessagePublisher.java
- [X] T023 [US1] Add monitoring and metrics for publish operations in src/main/java/ai/hack/rocketmq/monitoring/MetricsCollector.java
- [X] T024 [US1] Add logging for async publish operations in src/main/java/ai/hack/rocketmq/core/MessagePublisher.java

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently

---

## Phase 4: User Story 2 - Async Message Consumption with Callback (Priority: P1)

**Goal**: Enable applications to embed a RocketMQ client library that consumes messages asynchronously and processes them with callback methods so that incoming messages trigger appropriate business logic and can be replied to dynamically.

**Independent Test**: Can be fully tested by setting up a consumer that receives messages and replies using the callback topic address.

### Tests for User Story 2 (OPTIONAL - only if tests requested) ⚠️

- [X] T025 [P] [US2] Contract test for message consumption with callbacks in tests/UserStory2AsyncConsumeTest.java
- [X] T026 [P] [US2] Integration test for message processing and reply flow in tests/UserStory2AsyncConsumeTest.java

### Implementation for User Story 2

- [X] T027 [P] [US2] Create MessageCallback functional interface in src/main/java/ai/hack/rocketmq/callback/MessageCallback.java
- [X] T028 [P] [US2] Create MessageProcessingResult class in src/main/java/ai/hack/rocketmq/callback/MessageProcessingResult.java
- [X] T029 [US2] Implement MessageConsumer class in src/main/java/ai/hack/rocketmq/core/MessageConsumer.java (depends on T027, T028)
- [X] T030 [US2] Add subscribe method implementation in src/main/java/ai/hack/rocketmq/core/MessageConsumer.java
- [X] T031 [US2] Implement callback message processing in src/main/java/ai/hack/rocketmq/core/MessageConsumer.java
- [X] T032 [US2] Add support for reply-to callback topics in src/main/java/ai/hack/rocketmq/core/MessageConsumer.java
- [X] T033 [US2] Implement error handling for message processing failures in src/main/java/ai/hack/rocketmq/core/MessageConsumer.java
- [X] T034 [US2] Add ordered message processing support in src/main/java/ai/hack/rocketmq/core/MessageConsumer.java
- [X] T035 [US2] Integrate consumption callbacks with DefaultRocketMQAsyncClient in src/main/java/ai/hack/rocketmq/DefaultRocketMQAsyncClient.java
- [X] T036 [US2] Add monitoring for message consumption metrics in src/main/java/ai/hack/rocketmq/monitoring/MetricsCollector.java

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently

---

## Phase 5: User Story 3 - Request-Response Pattern with Timeout (Priority: P2)

**Goal**: Enable applications to send messages and wait for responses with configurable timeouts so that synchronous operations can benefit from asynchronous communication while maintaining responsiveness requirements.

**Independent Test**: Can be fully tested by sending a message through an async client and receiving a response before timeout occurs.

### Tests for User Story 3 (OPTIONAL - only if tests requested) ⚠️

- [X] T037 [P] [US3] Contract test for request-response with timeout in tests/UserStory3RequestResponseTest.java
- [X] T038 [P] [US3] Integration test for correlation and timeout handling in tests/UserStory3RequestResponseTest.java

### Implementation for User Story 3

- [X] T039 [P] [US3] Create CallbackManager class in src/main/java/ai/hack/rocketmq/core/CallbackManager.java
- [X] T040 [US3] Implement correlation ID mapping with ConcurrentHashMap in src/main/java/ai/hack/rocketmq/core/CallbackManager.java
- [X] T041 [US3] Add timeout handling with ScheduledExecutorService in src/main/java/ai/hack/rocketmq/core/CallbackManager.java
- [X] T042 [US3] Implement sendAndReceiveAsync method with timeout in src/main/java/ai/hack/rocketmq/DefaultRocketMQAsyncClient.java
- [X] T043 [US3] Add request-response message correlation logic in src/main/java/ai/hack/rocketmq/core/CallbackManager.java
- [X] T044 [US3] Implement concurrent request handling without race conditions in src/main/java/ai/hack/rocketmq/core/CallbackManager.java
- [X] T045 [US3] Add timeout exception handling for unresponsive requests in src/main/java/ai/hack/rocketmq/core/CallbackManager.java
- [X] T046 [US3] Integrate CallbackManager with message consumer for response handling in src/main/java/ai/hack/rocketmq/core/MessageConsumer.java

**Checkpoint**: User Story 1, 2, AND 3 should now be independently functional

---

## Phase 6: User Story 4 - Concurrent Message Handling (Priority: P2)

**Goal**: Enable the RocketMQ client to handle high-volume concurrent message publishing and consuming so that systems can scale under load.

**Independent Test**: Can be fully tested by generating concurrent message streams and verifying system stability and message delivery accuracy.

### Tests for User Story 4 (OPTIONAL - only if tests requested) ⚠️

- [X] T047 [P] [US4] Performance test for 1000+ concurrent operations in src/test/java/ai/hack/rocketmq/UserStory4ConcurrencyTest.java
- [X] T048 [P] [US4] Chaos test for network resilience under load in src/test/java/ai/hack/rocketmq/UserStory4ConcurrencyTest.java

### Implementation for User Story 4

- [X] T049 [P] [US4] Create ConnectionPool class in src/main/java/ai/hack/rocketmq/core/ConnectionPool.java
- [X] T050 [P] [US4] Implement thread-safe connection management with AtomicInteger in src/main/java/ai/hack/rocketmq/core/ConnectionPool.java
- [X] T051 [US4] Add health checking for connection pool stability in src/main/java/ai/hack/rocketmq/core/ConnectionPool.java
- [X] T052 [US4] Optimize MessagePublisher for concurrent publishing with virtual threads in src/main/java/ai/hack/rocketmq/core/MessagePublisher.java
- [X] T053 [US4] Optimize MessageConsumer for concurrent consumption handling in src/main/java/ai/hack/rocketmq/core/MessageConsumer.java
- [X] T054 [US4] Add connection pool configuration to ClientConfiguration in src/main/java/ai/hack/rocketmq/config/ClientConfiguration.java
- [X] T055 [US4] Implement circuit breaker pattern for overload protection in src/main/java/ai/hack/rocketmq/core/ConnectionPool.java
- [X] T056 [US4] Add backpressure mechanism for high-load scenarios in src/main/java/ai/hack/rocketmq/core/MessagePublisher.java

**Checkpoint**: All user stories should now be independently functional with high concurrency support

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [X] T057 [P] Create Spring Boot starter for easy integration in src/main/java/ai/hack/rocketmq/spring/
- [X] T058 [P] Add comprehensive Javadoc documentation for all public APIs
- [X] T059 Create quickstart.md examples and validation tests in tests/quickstart/
- [X] T060 Code cleanup and performance optimization based on JMH benchmarking
- [X] T061 Security hardening for TLS 1.3 and credential management in src/main/java/ai/hack/rocketmq/security/
- [X] T062 Run quickstart.md validation in test environment
- [X] T063 Memory usage optimization to meet <100MB footprint requirement
- [X] T064 Final performance validation against success criteria (SC-001 through SC-006)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
  - User stories can then proceed in parallel (if staffed)
  - Or sequentially in priority order (P1 → P1 → P2 → P2)
- **Polish (Final Phase)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) - No dependencies on other stories
- **User Story 2 (P1)**: Can start after Foundational (Phase 2) - May integrate with US1 but should be independently testable
- **User Story 3 (P2)**: Depends on US2 completion for callback processing - Should build on US2 infrastructure
- **User Story 4 (P2)**: Can start after Foundational (Phase 2) - Enhances all previous stories with concurrency

### Within Each User Story

- Tests (if included) MUST be written and FAIL before implementation
- Models before services
- Services before endpoints
- Core implementation before integration
- Story complete before moving to next priority

### Parallel Opportunities

- All Setup tasks marked [P] can run in parallel
- All Foundational tasks marked [P] can run in parallel (within Phase 2)
- Once Foundational phase completes, US1 and US2 can start in parallel
- All tests for a user story marked [P] can run in parallel
- Models within a story marked [P] can run in parallel
- US4 (concurrency) can be worked on in parallel with US3 once foundation is ready

---

## Parallel Example: User Story 1

```bash
# Launch all tests for User Story 1 together (if tests requested):
Task: "Contract test for async message publishing in tests/contract/test_async_publish.java"
Task: "Integration test for message delivery verification in tests/integration/test_publish_flow.java"

# Launch core components together:
Task: "Create MessagePublisher class in src/main/java/ai/hack/rocketmq/core/MessagePublisher.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL - blocks all stories)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: Test async message publishing independently
5. Deploy/demo if ready

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready
2. Add User Story 1 → Test independently → Deploy/Demo (MVP!)
3. Add User Story 2 → Test independently → Deploy/Demo
4. Add User Story 3 → Test independently → Deploy/Demo
5. Add User Story 4 → Test independently → Deploy/Demo
6. Each story adds value without breaking previous stories

### Parallel Team Strategy

With multiple developers:

1. Team completes Setup + Foundational together
2. Once Foundational is done:
   - Developer A: User Story 1 (async publishing)
   - Developer B: User Story 2 (async consumption)
   - Developer C: User Story 3 (request-response)
3. US4 (concurrency) can be integrated once foundation stories are complete
4. Stories complete and integrate independently

---

## Success Criteria Validation

The implementation must meet allsuccess criteria from spec.md:
- **SC-001**: <50ms publish latency (95%ile) - Validated by performance tests
- **SC-002**: 1000+ concurrent operations - Validated by US4 load tests
- **SC-003**: 99.9% delivery success - Validated by integration tests
- **SC-004**: <5s timeout completion - Validated by US3 timeout tests
- **SC-005**: <30s recovery time - Validated by resilience tests
- **SC-006**: <10% memory increase - Validated by memory profiling

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Verify tests fail before implementing
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- Avoid: vague tasks, same file conflicts, cross-story dependencies that break independence
- Memory budget: <100MB total usage must be maintained throughout implementation
- Java 21 virtual threads should be utilized for US4 high-concurrency scenarios