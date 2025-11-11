package ai.hack.rocketmq.client;

import ai.hack.rocketmq.client.exception.SessionException;
import ai.hack.rocketmq.client.model.StreamingResponseHandler;
import ai.hack.rocketmq.client.model.StreamingSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active streaming sessions for RPC client.
 * <p>
 * This class provides thread-safe session management including registration,
 * retrieval, activity tracking, and cleanup of streaming sessions. Sessions
 * are tracked by session ID and support concurrent access.
 * <p>
 * Sessions can be in two states:
 * <ul>
 *   <li><strong>Active</strong>: Accepting new messages</li>
 *   <li><strong>Inactive</strong>: No longer accepting messages (ended or timed out)</li>
 * </ul>
 *
 * @author Claude Code
 * @since 1.0.0
 */
public class SessionManager {

    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);

    /**
     * Map of session ID to streaming session.
     * ConcurrentHashMap provides thread-safe access for concurrent session management.
     */
    private final ConcurrentHashMap<String, StreamingSession> sessions;

    /**
     * Map of session ID to streaming response handler for bidirectional streaming.
     * Only populated for bidirectional streaming sessions.
     */
    private final ConcurrentHashMap<String, StreamingResponseHandler> responseHandlers;

    /**
     * Constructs a new SessionManager.
     */
    public SessionManager() {
        this.sessions = new ConcurrentHashMap<>();
        this.responseHandlers = new ConcurrentHashMap<>();
    }

    /**
     * Registers a new streaming session.
     * <p>
     * The session is marked as active and ready to accept messages.
     *
     * @param session the streaming session to register
     * @throws SessionException if a session with the same ID already exists
     */
    public void registerSession(StreamingSession session) throws SessionException {
        StreamingSession existing = sessions.putIfAbsent(session.sessionId(), session);
        if (existing != null) {
            throw new SessionException("Session already exists: " + session.sessionId());
        }
        logger.debug("Registered streaming session: sessionId={}", session.sessionId());
    }

    /**
     * Retrieves a streaming session by ID.
     *
     * @param sessionId the session ID to retrieve
     * @return the StreamingSession
     * @throws SessionException if session does not exist
     */
    public StreamingSession getSession(String sessionId) throws SessionException {
        StreamingSession session = sessions.get(sessionId);
        if (session == null) {
            throw new SessionException("Session not found: " + sessionId);
        }
        return session;
    }

    /**
     * Retrieves an active streaming session by ID.
     * <p>
     * This method ensures the session exists and is still active.
     *
     * @param sessionId the session ID to retrieve
     * @return the active StreamingSession
     * @throws SessionException if session does not exist or is not active
     */
    public StreamingSession getActiveSession(String sessionId) throws SessionException {
        StreamingSession session = getSession(sessionId);
        if (!session.active()) {
            throw new SessionException("Session is not active: " + sessionId);
        }
        return session;
    }

    /**
     * Updates a streaming session with new activity.
     * <p>
     * This method increments the message count and updates the last activity timestamp.
     *
     * @param sessionId the session ID to update
     * @return the updated StreamingSession
     * @throws SessionException if session does not exist or is not active
     */
    public StreamingSession recordActivity(String sessionId) throws SessionException {
        StreamingSession session = getActiveSession(sessionId);
        StreamingSession updated = session.withActivity();
        sessions.put(sessionId, updated);
        logger.debug("Recorded activity for session: sessionId={}, messageCount={}",
            sessionId, updated.messageCount());
        return updated;
    }

    /**
     * Deactivates a streaming session.
     * <p>
     * The session remains in the registry but no longer accepts new messages.
     *
     * @param sessionId the session ID to deactivate
     * @return the deactivated StreamingSession
     * @throws SessionException if session does not exist
     */
    public StreamingSession deactivateSession(String sessionId) throws SessionException {
        StreamingSession session = getSession(sessionId);
        StreamingSession deactivated = session.deactivate();
        sessions.put(sessionId, deactivated);
        logger.debug("Deactivated streaming session: sessionId={}, totalMessages={}",
            sessionId, deactivated.messageCount());
        return deactivated;
    }

    /**
     * Removes a streaming session from the registry.
     *
     * @param sessionId the session ID to remove
     * @return true if session was removed, false if not found
     */
    public boolean removeSession(String sessionId) {
        StreamingSession removed = sessions.remove(sessionId);
        if (removed != null) {
            logger.debug("Removed streaming session: sessionId={}", sessionId);
            return true;
        }
        return false;
    }

    /**
     * Checks if a session exists and is active.
     *
     * @param sessionId the session ID to check
     * @return true if session exists and is active, false otherwise
     */
    public boolean isSessionActive(String sessionId) {
        StreamingSession session = sessions.get(sessionId);
        return session != null && session.active();
    }

    /**
     * Gets the count of all sessions (active and inactive).
     *
     * @return total session count
     */
    public int getSessionCount() {
        return sessions.size();
    }

    /**
     * Gets the count of active sessions.
     *
     * @return active session count
     */
    public int getActiveSessionCount() {
        return (int) sessions.values().stream()
            .filter(StreamingSession::active)
            .count();
    }

    /**
     * Removes all idle sessions that have exceeded the timeout.
     * <p>
     * This method should be called periodically to clean up abandoned sessions.
     *
     * @param timeoutMillis idle timeout in milliseconds
     * @return count of sessions removed
     */
    public int removeIdleSessions(long timeoutMillis) {
        int removedCount = 0;
        for (StreamingSession session : sessions.values()) {
            if (session.isIdleTimeout(timeoutMillis)) {
                if (sessions.remove(session.sessionId(), session)) {
                    removedCount++;
                    logger.info("Removed idle session: sessionId={}, idleTime={}ms",
                        session.sessionId(),
                        java.time.Instant.now().toEpochMilli() - session.lastActivityAt().toEpochMilli());
                }
            }
        }
        if (removedCount > 0) {
            logger.info("Removed {} idle sessions (timeout={}ms)", removedCount, timeoutMillis);
        }
        return removedCount;
    }

    /**
     * Removes all sessions from the registry.
     * <p>
     * Used during shutdown to clean up all resources.
     * Also removes all registered response handlers.
     *
     * @return count of sessions removed
     */
    public int removeAllSessions() {
        int count = sessions.size();
        sessions.clear();

        // Also clear all response handlers
        int handlerCount = responseHandlers.size();
        responseHandlers.clear();

        if (count > 0) {
            logger.info("Removed all {} sessions and {} response handlers", count, handlerCount);
        }
        return count;
    }

    /**
     * Gets all active sessions.
     * <p>
     * Returns a snapshot of active sessions at the time of call.
     * The collection is not backed by the internal map.
     *
     * @return collection of active streaming sessions
     */
    public Collection<StreamingSession> getActiveSessions() {
        return sessions.values().stream()
            .filter(StreamingSession::active)
            .toList();
    }

    /**
     * Gets all sessions (active and inactive).
     * <p>
     * Returns a snapshot of all sessions at the time of call.
     * The collection is not backed by the internal map.
     *
     * @return collection of all streaming sessions
     */
    public Collection<StreamingSession> getAllSessions() {
        return sessions.values().stream().toList();
    }

    /**
     * Registers a response handler for bidirectional streaming.
     * <p>
     * This handler will be invoked for each incremental response received
     * during the streaming session.
     *
     * @param sessionId the session ID
     * @param handler the response handler
     * @throws SessionException if session does not exist
     */
    public void registerResponseHandler(String sessionId, StreamingResponseHandler handler)
        throws SessionException {
        // Verify session exists
        getSession(sessionId);

        responseHandlers.put(sessionId, handler);
        logger.debug("Registered response handler for session: sessionId={}", sessionId);
    }

    /**
     * Gets the response handler for a session.
     *
     * @param sessionId the session ID
     * @return the response handler, or null if not registered
     */
    public StreamingResponseHandler getResponseHandler(String sessionId) {
        return responseHandlers.get(sessionId);
    }

    /**
     * Removes the response handler for a session.
     *
     * @param sessionId the session ID
     * @return true if handler was removed, false if not found
     */
    public boolean removeResponseHandler(String sessionId) {
        return responseHandlers.remove(sessionId) != null;
    }

    /**
     * Checks if a session has a registered response handler.
     *
     * @param sessionId the session ID
     * @return true if handler is registered, false otherwise
     */
    public boolean hasResponseHandler(String sessionId) {
        return responseHandlers.containsKey(sessionId);
    }
}
