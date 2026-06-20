package Objects.models.IAM;

/**
 * Immutable handoff value returned by the login flow after a session row is
 * created in User_Session (03 - Security and Audit Tables).
 *
 * Carries the generated SessionID (used by the heartbeat to refresh / liveness-
 * check the row) and the raw session token (whose SHA-256 hash is what was
 * actually persisted). The raw token never leaves the client process.
 *
 * Lives in the Model layer so the ILoginProcess port can return it without the
 * UI or Process layer depending on DataAccess types.
 */
public final class SessionContext {

  private final long sessionId;
  private final String token;

  public SessionContext(long sessionId, String token) {
    this.sessionId = sessionId;
    this.token = token;
  }

  public long GetSessionId() {
    return sessionId;
  }

  public String GetToken() {
    return token;
  }
}