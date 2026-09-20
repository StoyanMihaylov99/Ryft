package com.application.ryft.issues.exception;

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
@RestControllerAdvice(basePackages = "com.application.ryft.issues")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class IssueExceptionHandler {

    @ExceptionHandler(IssueNotFoundException.class)
    public ResponseEntity<ApiError> handleIssueNotFound(IssueNotFoundException ex) {
        return status(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ProjectNotFoundException.class)
    public ResponseEntity<ApiError> handleProjectNotFound(ProjectNotFoundException ex) {
        return status(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(NotAProjectMemberException.class)
    public ResponseEntity<ApiError> handleNotAMember(NotAProjectMemberException ex) {
        return status(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(AssigneeNotAProjectMemberException.class)
    public ResponseEntity<ApiError> handleAssigneeNotAMember(AssigneeNotAProjectMemberException ex) {
        return status(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(InvalidParentLinkException.class)
    public ResponseEntity<ApiError> handleInvalidParentLink(InvalidParentLinkException ex) {
        return status(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(NotAnEpicException.class)
    public ResponseEntity<ApiError> handleNotAnEpic(NotAnEpicException ex) {
        return status(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(InsufficientProjectRoleException.class)
    public ResponseEntity<ApiError> handleInsufficientProjectRole(InsufficientProjectRoleException ex) {
        return status(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(IllegalStatusTransitionException.class)
    public ResponseEntity<ApiError> handleIllegalStatusTransition(IllegalStatusTransitionException ex) {
        return status(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(CommentNotFoundException.class)
    public ResponseEntity<ApiError> handleCommentNotFound(CommentNotFoundException ex) {
        return status(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(NotCommentAuthorException.class)
    public ResponseEntity<ApiError> handleNotCommentAuthor(NotCommentAuthorException ex) {
        return status(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(LabelNotFoundException.class)
    public ResponseEntity<ApiError> handleLabelNotFound(LabelNotFoundException ex) {
        return status(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ComponentNotFoundException.class)
    public ResponseEntity<ApiError> handleComponentNotFound(ComponentNotFoundException ex) {
        return status(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(LabelNameAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleLabelNameExists(LabelNameAlreadyExistsException ex) {
        return status(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(ComponentNameAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleComponentNameExists(ComponentNameAlreadyExistsException ex) {
        return status(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(InvalidLabelReferenceException.class)
    public ResponseEntity<ApiError> handleInvalidLabelReference(InvalidLabelReferenceException ex) {
        return status(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(InvalidComponentReferenceException.class)
    public ResponseEntity<ApiError> handleInvalidComponentReference(InvalidComponentReferenceException ex) {
        return status(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    private ResponseEntity<ApiError> status(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(ApiError.of(status.value(), status.getReasonPhrase(), message));
    }
}
