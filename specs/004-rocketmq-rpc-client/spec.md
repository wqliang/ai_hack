# Feature Specification: RocketMQ RPC Client Wrapper

**Feature Branch**: `004-rocketmq-rpc-client`
**Created**: 2025-11-11
**Status**: Draft
**Input**: User description: "当前项目要新增一个Client的封装,其功能是实现消息的发送和接收,用于模拟RPC调用和AI场景下的单向或双向流式交互。首先要有一个发送的客户端,用于将请求消息发送到rocketmq-broker上,如果发送的消息是多条,也就是请求消息是流式的,要注意将同一个会话的消息发到同一个消息队列以达到同会话消息都被同一个接收者收到。每个发送者会有一个全局唯一的ID,这个ID对应一个Topic,这个Topic只有这个发送者订阅,用于接收响应方返回的数据或数据流。发送者发出的消息中会在扩展属性中带上自己的ID。消息接受者从消息队列上接收消息,处理结果会根据请求消息中的ID发送到对应的Topic从而被发送者收到。整个Client封装的接口必须包括同步阻塞式和异步式两个接口,接口满足接口设计的规范和最佳实践。"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Synchronous RPC Call (Priority: P1)

A developer wants to make a synchronous RPC-style call through RocketMQ where they send a request message and wait for the response, similar to a traditional HTTP request-response pattern.

**Why this priority**: This is the most fundamental use case and represents the minimum viable functionality. It provides the foundation for all other interaction patterns.

**Independent Test**: Can be fully tested by sending a single request message and receiving a single response message, verifying the request-response correlation and blocking behavior.

**Acceptance Scenarios**:

1. **Given** a client with a unique sender ID, **When** the client sends a single request message synchronously, **Then** the client blocks until a response is received from the corresponding response topic
2. **Given** a client has sent a synchronous request, **When** the receiver processes the request and sends a response to the sender's topic, **Then** the original sender receives the response and correlates it with the original request
3. **Given** a client sends a synchronous request, **When** no response is received within the timeout period, **Then** the client receives a timeout exception and can handle it appropriately

---

### User Story 2 - Asynchronous RPC Call (Priority: P1)

A developer wants to make an asynchronous RPC-style call where they send a request message and receive the response through a callback or future mechanism without blocking the calling thread.

**Why this priority**: Asynchronous operations are essential for high-performance applications and AI scenarios where processing may take significant time. This is core functionality alongside synchronous calls.

**Independent Test**: Can be fully tested by sending a request message asynchronously, verifying the calling thread is not blocked, and receiving the response through a callback mechanism.

**Acceptance Scenarios**:

1. **Given** a client with a unique sender ID, **When** the client sends a request message asynchronously with a callback handler, **Then** the method returns immediately without blocking
2. **Given** an asynchronous request has been sent, **When** the receiver processes the request and sends a response, **Then** the callback handler is invoked with the response data
3. **Given** multiple asynchronous requests are sent concurrently, **When** responses arrive in any order, **Then** each response is correctly correlated with its original request and delivered to the appropriate callback

---

### User Story 3 - Streaming Request with Single Response (Priority: P2)

A developer wants to send multiple request messages as a stream (e.g., for incremental data or AI prompt tokens) and receive a single aggregated response, ensuring all messages from the same session are processed by the same receiver.

**Why this priority**: This supports AI scenarios where input may be provided incrementally (streaming prompts) but a single final response is expected. It builds on P1 functionality.

**Independent Test**: Can be tested by sending multiple messages with the same session ID, verifying they route to the same message queue, and receiving a single aggregated response.

**Acceptance Scenarios**:

1. **Given** a client initiates a streaming request session, **When** the client sends multiple request messages with the same session ID, **Then** all messages are routed to the same message queue using message ordering keys
2. **Given** multiple request messages are sent in a stream, **When** the receiver processes all messages in order, **Then** the receiver sends a single response to the sender's topic
3. **Given** two different sessions are active concurrently, **When** messages from both sessions are sent, **Then** messages from each session are processed independently by potentially different receivers

---

### User Story 4 - Bidirectional Streaming (Priority: P2)

A developer wants to send a stream of request messages and receive a stream of response messages, enabling real-time bidirectional communication such as AI chat with streaming responses.

**Why this priority**: This represents the most advanced use case, supporting full-duplex streaming for AI interactions. It depends on both streaming request handling and streaming response delivery.

**Independent Test**: Can be tested by sending multiple request messages as a stream and receiving multiple response messages as a stream, verifying the correlation between request and response streams.

**Acceptance Scenarios**:

1. **Given** a client initiates a bidirectional streaming session, **When** the client sends multiple request messages with the same session ID, **Then** the client can receive multiple response messages as they are produced by the receiver
2. **Given** a streaming session is active, **When** response messages arrive incrementally, **Then** the client delivers each response message to the appropriate handler (callback or stream consumer) as soon as it arrives
3. **Given** a bidirectional streaming session, **When** either party completes their message stream, **Then** the session is properly closed and resources are released

---

### Edge Cases

- What happens when a sender's response topic does not exist or cannot be created?
- How does the system handle message correlation when multiple concurrent requests are in flight from the same sender?
- What happens if a receiver sends a response to a non-existent sender topic (sender has disconnected)?
- How does the system handle partial message delivery in streaming scenarios (network interruption mid-stream)?
- What happens when the RocketMQ broker is temporarily unavailable during send or receive operations?
- How does the client handle message ordering guarantees when the same session spans multiple message queue partitions?
- What happens if the sender ID collision occurs (two clients generate the same ID)?
- How does the system clean up abandoned topics from disconnected senders?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST provide a sender client that can send request messages to a RocketMQ broker
- **FR-002**: Each sender client MUST have a globally unique sender ID (UUID format assumed)
- **FR-003**: Each sender client MUST have a dedicated response topic named using its sender ID (e.g., "RESPONSE_{senderId}")
- **FR-004**: Sender client MUST automatically create and subscribe to its response topic upon initialization
- **FR-005**: Request messages MUST include the sender ID in message extended properties (user property: "senderId")
- **FR-006**: Request messages for the same session MUST be routed to the same message queue using session ID as the message ordering key
- **FR-007**: System MUST provide a synchronous send method that blocks until a response is received or timeout occurs
- **FR-008**: System MUST provide an asynchronous send method that returns immediately and delivers responses via callback
- **FR-009**: Receiver client MUST extract sender ID from request message properties
- **FR-010**: Receiver client MUST send response messages to the topic corresponding to the sender ID from the request
- **FR-011**: System MUST correlate response messages with their original requests using a unique correlation ID (generated per request)
- **FR-012**: Request messages MUST include a correlation ID in message properties (user property: "correlationId")
- **FR-013**: Response messages MUST include the correlation ID from the corresponding request
- **FR-014**: System MUST support streaming requests where multiple messages share the same session ID
- **FR-015**: System MUST support streaming responses where multiple response messages are delivered for a single session
- **FR-016**: Synchronous send method MUST have a configurable timeout period (default: 30 seconds)
- **FR-017**: System MUST provide proper connection lifecycle management (connect, disconnect, cleanup)
- **FR-018**: System MUST handle graceful shutdown with proper resource cleanup (close producers, consumers, unsubscribe topics)

### Key Entities

- **Sender Client**: The component that sends request messages and receives response messages, identified by a unique sender ID
- **Receiver Client**: The component that receives request messages from queues and sends response messages to sender-specific topics
- **Request Message**: A message containing the request payload, sender ID, correlation ID, and optional session ID for streaming
- **Response Message**: A message containing the response payload and correlation ID to match with the original request
- **Session**: A logical grouping of related messages in streaming scenarios, identified by a session ID to ensure ordering and single-receiver processing
- **Response Topic**: A RocketMQ topic dedicated to a specific sender for receiving responses, named using the sender's unique ID

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A developer can send a single request and receive a response synchronously in under 100ms (excluding actual processing time)
- **SC-002**: A developer can send 1000 asynchronous requests concurrently and receive all responses correctly correlated without blocking
- **SC-003**: In streaming scenarios, all messages from the same session are guaranteed to be processed by the same receiver (100% ordering guarantee)
- **SC-004**: The client can handle at least 100 concurrent sessions without performance degradation or resource exhaustion
- **SC-005**: Response correlation accuracy is 100% - no response is ever matched to the wrong request
- **SC-006**: Client initialization and cleanup complete within 5 seconds under normal conditions
- **SC-007**: The system handles broker temporary unavailability gracefully with appropriate error messages and retry mechanisms where applicable

## Assumptions

- RocketMQ broker is already deployed and accessible
- Message payload format is flexible (byte array or string) to support various use cases (RPC, AI text, etc.)
- Standard RocketMQ message size limits apply (default 4MB maximum message size)
- Network latency between client and broker is reasonable (< 50ms for typical deployments)
- Sender ID uniqueness is guaranteed by using UUID generation
- Topic naming follows RocketMQ conventions and restrictions
- Correlation ID generation uses UUID to ensure uniqueness across all clients
- Response topic creation and subscription happen during client initialization
- The receiver client implementation is separate from the sender client but uses the same message format conventions
- Session ID for streaming is provided by the caller (application-level responsibility)
- Message ordering within a session uses RocketMQ's message ordering feature (sharding key / message queue selection)
- Timeout handling for synchronous calls uses standard Java timeout mechanisms (blocking with timeout)
- Callback mechanism for asynchronous calls follows standard Java callback patterns (functional interface or CompletableFuture)
