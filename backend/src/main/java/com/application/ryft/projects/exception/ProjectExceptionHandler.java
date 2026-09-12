package com.application.ryft.projects.exception;

import com.application.ryft.common.exception.ApiError;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Same ordering rationale as identity's exception handlers (e.g. WorkspaceExceptionHandler): must
 * outrank the unscoped {@link com.application.ryft.common.exception.GlobalExceptionHandler} catch-all.
 */
@RestControllerAdvice(basePackages = "com.application.ryft.projects")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProjectExceptionHandler {

    @ExceptionHandler(ProjectNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ProjectNotFoundException ex) {
        return status(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(WorkspaceNotReadyException.class)
    public ResponseEntity<ApiError> handleWorkspaceNotReady(WorkspaceNotReadyException ex) {
        return status(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ProjectKeyAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleKeyExists(ProjectKeyAlreadyExistsException ex) {
        return status(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(NotAProjectMemberException.class)
    public ResponseEntity<ApiError> handleNotAMember(NotAProjectMemberException ex) {
        return status(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(InsufficientProjectRoleException.class)
    public ResponseEntity<ApiError> handleInsufficientRole(InsufficientProjectRoleException ex) {
        return status(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(AddMemberTargetNotFoundException.class)
    public ResponseEntity<ApiError> handleAddTargetNotFound(AddMemberTargetNotFoundException ex) {
        return status(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(AlreadyProjectMemberException.class)
    public ResponseEntity<ApiError> handleAlreadyMember(AlreadyProjectMemberException ex) {
        return status(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(ProjectMemberNotFoundException.class)
    public ResponseEntity<ApiError> handleMemberNotFound(ProjectMemberNotFoundException ex) {
        return status(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(CannotAssignOwnerRoleException.class)
    public ResponseEntity<ApiError> handleCannotAssignOwner(CannotAssignOwnerRoleException ex) {
        return status(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(CannotModifySelfRoleException.class)
    public ResponseEntity<ApiError> handleCannotModifySelf(CannotModifySelfRoleException ex) {
        return status(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(CannotRemoveSelfException.class)
    public ResponseEntity<ApiError> handleCannotRemoveSelf(CannotRemoveSelfException ex) {
        return status(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    private ResponseEntity<ApiError> status(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(ApiError.of(status.value(), status.getReasonPhrase(), message));
    }
}
