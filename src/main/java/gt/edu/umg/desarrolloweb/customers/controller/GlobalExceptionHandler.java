package gt.edu.umg.desarrolloweb.customers.controller;

import gt.edu.umg.desarrolloweb.customers.api.dto.ErrorDto;
import gt.edu.umg.desarrolloweb.customers.service.CustomerNotFoundException;
import gt.edu.umg.desarrolloweb.customers.service.DuplicateMeterCodeException;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * Converts exceptions into the "Error" schema defined in the contract.
 *
 * Security: the client NEVER receives a stack trace. Unexpected errors are logged on the
 * server and the client only gets a generic 500 message.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CustomerNotFoundException.class)
    public ResponseEntity<ErrorDto> handleNotFound(CustomerNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), List.of());
    }

    @ExceptionHandler(DuplicateMeterCodeException.class)
    public ResponseEntity<ErrorDto> handleDuplicate(DuplicateMeterCodeException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), List.of());
    }

    /** @Valid failed on the request body (e.g. fullName too short, bad meterCode pattern). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorDto> handleBodyValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Validation failed", details);
    }

    /** Constraint failed on a path/query parameter (e.g. customerId = 0 violates minimum: 1). */
    @ExceptionHandler({HandlerMethodValidationException.class, ConstraintViolationException.class})
    public ResponseEntity<ErrorDto> handleParameterValidation(Exception ex) {
        return build(HttpStatus.BAD_REQUEST, "Invalid parameter", List.of());
    }

    /** Malformed JSON, or a field with the wrong type. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorDto> handleUnreadable(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, "Malformed request body", List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDto> handleUnexpected(Exception ex) {
        // Spring's own web exceptions (415 wrong Content-Type, 405 wrong method, 404 unknown
        // route...) already carry the right status: keep it instead of turning them into 500.
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.valueOf(errorResponse.getStatusCode().value());
            return build(status, status.getReasonPhrase(), List.of());
        }
        log.error("Unexpected error", ex); // full detail stays in the server log
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", List.of());
    }

    private ResponseEntity<ErrorDto> build(HttpStatus status, String message, List<String> details) {
        ErrorDto body = new ErrorDto(status.value(), message).details(details);
        return ResponseEntity.status(status).body(body);
    }
}
