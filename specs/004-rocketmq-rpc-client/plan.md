# Implementation Plan: RocketMQ RPC Client Wrapper

**Branch**: `004-rocketmq-rpc-client` | **Date**: 2025-11-11 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/004-rocketmq-rpc-client/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/commands/plan.md` for the execution workflow.

## Summary

This feature implements a RocketMQ client wrapper that enables RPC-style communication and AI streaming interactions through message queues. The client provides both synchronous (blocking) and asynchronous (non-blocking with callbacks) interfaces for sending requests and receiving responses. Each sender has a unique ID with a dedicated response topic for receiving replies. The system supports streaming requests (multiple messages with session-based routing) and streaming responses (bidirectional communication) for AI chat scenarios. Messages are correlated using correlation IDs to ensure accurate request-response matching even with concurrent operations.

## Technical Context

**Language/Version**: Java 21 (with Java 17 compatibility via multi-release JAR)
**Primary Dependencies**: Apache RocketMQ 5.3.3 client library, Spring Boot 3.3.5, RocksDB 8.10.0, H2 Database 2.2.224
**Storage**: Hybrid approach - RocksDB for message persistence + H2 for metadata/correlation tracking (in-memory or file-based)
**Testing**: JUnit 5, Mockito, Spring Boot Test, TestContainers for integration tests
**Target Platform**: JVM-based applications (server-side, can run on Linux/Windows/macOS)
**Project Type**: Single project - Library/framework component integrated into existing Spring Boot application
**Performance Goals**: <100ms request-response latency (excluding processing), 1000+ concurrent async requests, 100+ concurrent streaming sessions
**Constraints**: Message ordering within sessions, 100% correlation accuracy, <5s initialization/cleanup time, 4MB max message size (RocketMQ default)
**Scale/Scope**: Support for high-throughput RPC and AI streaming scenarios with concurrent sessions, proper resource management for long-running applications

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### I. Code Documentation (NON-NEGOTIABLE)
- ✅ **PASS**: All client wrapper classes (sender, receiver, correlation manager) will have JavaDoc with @author, @since, and class descriptions
- ✅ **PASS**: All public methods (send sync/async, receive, lifecycle methods) will have JavaDoc with @param, @return, @throws
- ✅ **PASS**: Complex algorithms (correlation matching, session routing, timeout handling) will have inline comments
- ✅ **PASS**: Configuration constants (timeout defaults, topic naming patterns) will be documented

### II. Java Development Standards
- ✅ **PASS**: Package structure follows ai.hack.rocketmq.client.* convention
- ✅ **PASS**: Naming: camelCase for methods/variables, PascalCase for classes (RpcClient, MessageCorrelator)
- ✅ **PASS**: Leverage Java 21 features: records for message DTOs, sealed classes for message types, CompletableFuture for async
- ✅ **PASS**: Apply SOLID: Single Responsibility (separate sender/receiver), Interface Segregation (sync/async interfaces), Dependency Injection
- ✅ **PASS**: Use generics for type-safe message payload handling

### III. Spring Boot Best Practices
- ✅ **PASS**: Use constructor-based dependency injection for RocketMQ producer/consumer dependencies
- ✅ **PASS**: Externalize configuration (broker URLs, timeouts, topic prefixes) via application.yml and @ConfigurationProperties
- ✅ **PASS**: Use @Service annotation for client wrapper services
- ✅ **PASS**: Implement proper exception handling with custom exceptions (RpcTimeoutException, CorrelationException)
- ✅ **PASS**: Graceful lifecycle management using @PreDestroy for cleanup

### IV. Testing Discipline
- ✅ **PASS**: Unit tests for correlation logic, timeout handling, message routing using JUnit 5 + Mockito
- ✅ **PASS**: Integration tests for actual RocketMQ send/receive using TestContainers (embedded RocketMQ broker)
- ✅ **PASS**: Test coverage for edge cases: timeouts, concurrent requests, session ordering, error scenarios
- ✅ **PASS**: Test-driven development encouraged: write tests for sync/async contracts before implementation

### V. Git Flow and Version Control
- ✅ **PASS**: Feature branch 004-rocketmq-rpc-client follows naming convention
- ✅ **PASS**: Will use conventional commits (feat: add RPC client, test: add correlation tests)
- ✅ **PASS**: Merge to develop with --no-ff after code review

**Initial Assessment**: All constitution principles can be satisfied. No violations identified.

## Project Structure

### Documentation (this feature)

```text
specs/[###-feature]/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
src/main/java/ai/hack/
├── rocketmq/
│   └── client/
│       ├── RpcClient.java                    # Main client interface
│       ├── RpcClientImpl.java                # Implementation with sync/async support
│       ├── MessageSender.java                # Wraps RocketMQ Producer for sending
│       ├── MessageReceiver.java              # Wraps RocketMQ Consumer for receiving
│       ├── CorrelationManager.java           # Tracks request-response correlations
│       ├── SessionManager.java               # Manages streaming sessions
│       ├── config/
│       │   ├── RpcClientConfig.java          # Configuration properties
│       │   └── RpcClientAutoConfiguration.java # Spring Boot auto-configuration
│       ├── exception/
│       │   ├── RpcTimeoutException.java
│       │   ├── CorrelationException.java
│       │   └── SessionException.java
│       └── model/
│           ├── RpcRequest.java               # Request message (record)
│           ├── RpcResponse.java              # Response message (record)
│           ├── StreamingSession.java         # Session metadata (record)
│           └── MessageMetadata.java          # Correlation ID, sender ID, etc. (record)

tests/
├── java/ai/hack/rocketmq/client/
│   ├── unit/
│   │   ├── CorrelationManagerTest.java
│   │   ├── SessionManagerTest.java
│   │   └── MessageSenderTest.java
│   └── integration/
│       ├── RpcClientSyncTest.java
│       ├── RpcClientAsyncTest.java
│       ├── RpcClientStreamingTest.java
│       └── EmbeddedRocketMQConfig.java       # TestContainers configuration
└── resources/
    └── application-test.yml                   # Test configuration
```

**Structure Decision**: Single project structure selected. The RPC client is a library component that integrates into the existing Spring Boot application under `ai.hack.rocketmq.client` package. This follows the existing pattern where RocketMQ components are organized under the `rocketmq` sub-package. The structure separates concerns with distinct packages for configuration, exceptions, and domain models.

## Constitution Re-Check (Post-Design)

*Re-evaluation after Phase 1 design completion*

### I. Code Documentation (NON-NEGOTIABLE)
- ✅ **PASS**: All entities defined as Java records with clear field documentation
- ✅ **PASS**: API contract specifies JavaDoc requirements for all public methods
- ✅ **PASS**: Research document explains complex algorithms (correlation tracking, session routing)
- ✅ **PASS**: Configuration properties documented in data model

**Verdict**: PASS - Design supports comprehensive documentation requirements

### II. Java Development Standards
- ✅ **PASS**: Package structure ai.hack.rocketmq.client.* follows reverse domain notation
- ✅ **PASS**: Classes use PascalCase (RpcClient, MessageSender), methods use camelCase
- ✅ **PASS**: Leverages Java 21 records (RpcRequest, RpcResponse, StreamingSession, MessageMetadata)
- ✅ **PASS**: CompletableFuture for async operations (modern Java concurrency)
- ✅ **PASS**: SOLID principles: RpcClient (interface), RpcClientImpl (implementation), separate managers for concerns

**Verdict**: PASS - Design adheres to Java 21 best practices

### III. Spring Boot Best Practices
- ✅ **PASS**: Configuration via @ConfigurationProperties (RpcClientConfig)
- ✅ **PASS**: Services use @Service annotation and constructor injection
- ✅ **PASS**: Lifecycle management with @PreDestroy hook
- ✅ **PASS**: Custom exception hierarchy (RpcException, RpcTimeoutException, etc.)
- ✅ **PASS**: Externalized configuration in application.yml

**Verdict**: PASS - Design follows Spring Boot conventions

### IV. Testing Discipline
- ✅ **PASS**: Unit test structure defined (CorrelationManagerTest, SessionManagerTest, etc.)
- ✅ **PASS**: Integration tests using TestContainers for embedded RocketMQ broker
- ✅ **PASS**: Test coverage for edge cases documented in research.md
- ✅ **PASS**: API contracts specify testable preconditions/postconditions

**Verdict**: PASS - Comprehensive testing strategy defined

### V. Git Flow and Version Control
- ✅ **PASS**: Feature branch 004-rocketmq-rpc-client created and checked out
- ✅ **PASS**: All design artifacts committed to feature branch
- ✅ **PASS**: Ready for conventional commits during implementation

**Verdict**: PASS - Git Flow followed correctly

**Final Assessment**: All constitution principles remain satisfied after detailed design. No violations. Ready to proceed to implementation (/speckit.tasks).

