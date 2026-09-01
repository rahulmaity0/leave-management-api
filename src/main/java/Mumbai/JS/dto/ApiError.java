package Mumbai.JS.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * One consistent error body for every failure the API returns.
 * fieldErrors is only populated for validation failures, and is omitted otherwise.
 */
public record ApiError(
        int status,
        String error,
        String message,
        Map<String, String> fieldErrors,
        LocalDateTime timestamp) {

    public static ApiError of(int status, String error, String message) {
        return new ApiError(status, error, message, null, LocalDateTime.now());
    }

    public static ApiError validation(int status, String message, Map<String, String> fieldErrors) {
        return new ApiError(status, "Validation Failed", message, fieldErrors, LocalDateTime.now());
    }
}
