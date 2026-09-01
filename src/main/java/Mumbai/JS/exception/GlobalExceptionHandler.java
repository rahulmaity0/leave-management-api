package Mumbai.JS.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import Mumbai.JS.dto.ApiError;

/**
 * Turns exceptions thrown anywhere in the app into a consistent JSON error body
 * with the right HTTP status. Without this, the service layer would have to know
 * about HTTP, or errors would leak stack traces to the client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 404 - the id doesn't exist. */
    @ExceptionHandler(EmployeeNotFoundException.class)
    public ResponseEntity<ApiError> handleEmployeeNotFound(EmployeeNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(LeaveNotFoundException.class)
    public ResponseEntity<ApiError> handleLeaveNotFound(LeaveNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /** 400 - the client asked for something that breaks a business rule. */
    @ExceptionHandler(InvalidLeaveRequestException.class)
    public ResponseEntity<ApiError> handleInvalidLeave(InvalidLeaveRequestException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** 409 - the request is fine, but it clashes with the current state. */
    @ExceptionHandler(LeaveConflictException.class)
    public ResponseEntity<ApiError> handleLeaveConflict(LeaveConflictException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    /** 400 - @Valid failed. Returns which field failed and why, instead of a wall of text. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        ApiError body = ApiError.validation(HttpStatus.BAD_REQUEST.value(),
                "One or more fields are invalid", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    /** 400 - malformed JSON, or a bad enum value like "type": "HOLIDAY". */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, "Malformed request body: check your JSON and enum values");
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(ApiError.of(status.value(), status.getReasonPhrase(), message));
    }
}
