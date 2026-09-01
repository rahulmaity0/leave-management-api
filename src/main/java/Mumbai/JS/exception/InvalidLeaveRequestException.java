package Mumbai.JS.exception;

/**
 * Thrown when the request itself is not valid - end date before start date,
 * not enough leave balance, approving your own request. Maps to 400.
 */
public class InvalidLeaveRequestException extends RuntimeException {

    public InvalidLeaveRequestException(String message) {
        super(message);
    }
}
