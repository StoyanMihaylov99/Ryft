package com.application.ryft.identity.exception;

import com.application.ryft.common.exception.ApiError;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Same ordering rationale as {@link AuthExceptionHandler}: must outrank the unscoped
 * {@link com.application.ryft.common.exception.GlobalExceptionHandler} catch-all. Kept as its own
 * class (rather than adding methods to AuthExceptionHandler) purely for readability — the two handle
 * disjoint exception types, so their relative order doesn't matter.
 */
@RestControllerAdvice(basePackages = "com.application.ryft.identity")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WorkspaceExceptionHandler {

    @ExceptionHandler(NotAWorkspaceMemberException.class)
    public ResponseEntity<ApiError> handleNotAMember(NotAWorkspaceMemberException ex) {
        return status(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(WorkspaceNotSetUpException.class)
    public ResponseEntity<ApiError> handleNotSetUp(WorkspaceNotSetUpException ex) {
        return status(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(WorkspaceAlreadySetUpException.class)
    public ResponseEntity<ApiError> handleAlreadySetUp(WorkspaceAlreadySetUpException ex) {
        return status(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(InsufficientWorkspaceRoleException.class)
    public ResponseEntity<ApiError> handleInsufficientRole(InsufficientWorkspaceRoleException ex) {
        return status(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(InviteTargetNotFoundException.class)
    public ResponseEntity<ApiError> handleInviteTargetNotFound(InviteTargetNotFoundException ex) {
        return status(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(WorkspaceMemberNotFoundException.class)
    public ResponseEntity<ApiError> handleMemberNotFound(WorkspaceMemberNotFoundException ex) {
        return status(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(AlreadyWorkspaceMemberException.class)
    public ResponseEntity<ApiError> handleAlreadyMember(AlreadyWorkspaceMemberException ex) {
        return status(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(CannotAssignOwnerRoleException.class)
    public ResponseEntity<ApiError> handleCannotAssignOwner(CannotAssignOwnerRoleException ex) {
        return status(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(CannotModifySelfRoleException.class)
    public ResponseEntity<ApiError> handleCannotModifySelf(CannotModifySelfRoleException ex) {
        return status(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    private ResponseEntity<ApiError> status(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(ApiError.of(status.value(), status.getReasonPhrase(), message));
    }
}
