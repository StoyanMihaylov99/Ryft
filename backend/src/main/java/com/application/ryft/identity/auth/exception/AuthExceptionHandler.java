package com.application.ryft.identity.auth.exception;

import com.application.ryft.common.exception.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Explicitly ordered ahead of the unscoped {@link com.application.ryft.common.exception.GlobalExceptionHandler}:
 * Spring picks the first {@code @RestControllerAdvice} bean (in bean-registration/order) that has ANY
 * matching {@code @ExceptionHandler}, not the most specific match across all advice beans — without
 * this, GlobalExceptionHandler's {@code Exception.class} catch-all would swallow these before this
 * more specific advice ever got a chance.
 */
@RestControllerAdvice(basePackages = "com.application.ryft.identity")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AuthExceptionHandler.class);

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ApiError> handleEmailAlreadyRegistered(EmailAlreadyRegisteredException ex) {
        return status(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials(InvalidCredentialsException ex) {
        return status(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ApiError> handleInvalidRefreshToken(InvalidRefreshTokenException ex) {
        return status(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(RefreshTokenReuseDetectedException.class)
    public ResponseEntity<ApiError> handleReuseDetected(RefreshTokenReuseDetectedException ex) {
        log.warn("Refresh token reuse detected — rotation family revoked");
        return status(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    private ResponseEntity<ApiError> status(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(ApiError.of(status.value(), status.getReasonPhrase(), message));
    }
}
