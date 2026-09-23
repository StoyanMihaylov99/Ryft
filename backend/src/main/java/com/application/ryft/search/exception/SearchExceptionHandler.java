package com.application.ryft.search.exception;

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
@RestControllerAdvice(basePackages = "com.application.ryft.search")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SearchExceptionHandler {

    @ExceptionHandler(ProjectNotFoundException.class)
    public ResponseEntity<ApiError> handleProjectNotFound(ProjectNotFoundException ex) {
        return status(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(NotAProjectMemberException.class)
    public ResponseEntity<ApiError> handleNotAMember(NotAProjectMemberException ex) {
        return status(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(SavedFilterNotFoundException.class)
    public ResponseEntity<ApiError> handleSavedFilterNotFound(SavedFilterNotFoundException ex) {
        return status(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(NotSavedFilterOwnerException.class)
    public ResponseEntity<ApiError> handleNotSavedFilterOwner(NotSavedFilterOwnerException ex) {
        return status(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(SavedFilterNameAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleSavedFilterNameExists(SavedFilterNameAlreadyExistsException ex) {
        return status(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(InsufficientProjectRoleException.class)
    public ResponseEntity<ApiError> handleInsufficientProjectRole(InsufficientProjectRoleException ex) {
        return status(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    private ResponseEntity<ApiError> status(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(ApiError.of(status.value(), status.getReasonPhrase(), message));
    }
}
