package api.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import service.GroceryQuantityLimitException;

import java.util.Map;

/** Stable, safe errors for known request failures; internal exception details stay on the server. */
@RestControllerAdvice(basePackages = "api.web")
public class ApiErrorAdvice {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> rejected(ResponseStatusException error) {
        return response(error.getStatus(), error.getStatus().name(),
                error.getReason() == null ? error.getStatus().getReasonPhrase() : error.getReason());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> unreadable() {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                "Request body must contain valid JSON with the expected field types");
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, String>> invalidParameter() {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "A required request parameter is missing or invalid");
    }

    @ExceptionHandler(GroceryQuantityLimitException.class)
    public ResponseEntity<Map<String, String>> quantityLimit() {
        return response(HttpStatus.CONFLICT, "QUANTITY_LIMIT_EXCEEDED", "Grocery quantity cannot exceed 2147483647");
    }

    private ResponseEntity<Map<String, String>> response(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("code", code, "message", message));
    }
}
