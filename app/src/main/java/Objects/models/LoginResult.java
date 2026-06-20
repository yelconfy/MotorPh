package Objects.models;

/**
 * Carries the outcome of a login attempt back to the UI layer.
 *
 * The User is non-null for SUCCESS and MUST_CHANGE_PASSWORD so the
 * form can redirect or update the password immediately.
 */
public class LoginResult {

  public enum Status {
    SUCCESS,
    INVALID_CREDENTIALS,
    ACCOUNT_LOCKED,
    MUST_CHANGE_PASSWORD,
  }

  private final Status status;
  private final User user;

  private LoginResult(Status status, User user) {
    this.status = status;
    this.user = user;
  }

  public static LoginResult Success(User u) {
    return new LoginResult(Status.SUCCESS, u);
  }

  public static LoginResult Invalid() {
    return new LoginResult(Status.INVALID_CREDENTIALS, null);
  }

  public static LoginResult Locked() {
    return new LoginResult(Status.ACCOUNT_LOCKED, null);
  }

  public static LoginResult MustChange(User u) {
    return new LoginResult(Status.MUST_CHANGE_PASSWORD, u);
  }

  public Status GetStatus() {
    return status;
  }

  public User GetUser() {
    return user;
  }
}
