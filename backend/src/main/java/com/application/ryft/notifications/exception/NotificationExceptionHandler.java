package com.application.ryft.notifications.exception;

import com.application.ryft.common.exception.ApiError;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Same ordering rationale as the other modules' handlers: must outrank the unscoped
 * {@link com.application.ryft.common.exception.GlobalExceptionHandler} catch-all.
 */
@RestControllerAdvice(basePackages = "com.application.ryft.notifications")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class NotificationExceptionHandler {

    @ExceptionHandler(NotificationNotFoundException.class)
    public ResponseEntity<ApiError> handleNotificationNotFound(NotificationNotFoundException ex) {
        return status(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    private ResponseEntity<ApiError> status(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(ApiError.of(status.value(), status.getReasonPhrase(), message));
    }
}
