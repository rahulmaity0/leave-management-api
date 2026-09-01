package Mumbai.JS.exception;

/** Thrown when a leave request id does not exist. Maps to 404. */
public class LeaveNotFoundException extends RuntimeException {

    public LeaveNotFoundException(String message) {
        super(message);
    }
}
