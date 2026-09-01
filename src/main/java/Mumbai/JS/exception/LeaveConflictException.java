package Mumbai.JS.exception;

/**
 * Thrown when the request is well-formed but clashes with the current state -
 * overlapping dates, or acting on a request that is no longer PENDING. Maps to 409.
 */
public class LeaveConflictException extends RuntimeException {

    public LeaveConflictException(String message) {
        super(message);
    }
}
