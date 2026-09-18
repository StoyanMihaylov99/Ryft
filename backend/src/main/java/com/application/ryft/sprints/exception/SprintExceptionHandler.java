package com.application.ryft.sprints.exception;

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
@RestControllerAdvice(basePackages = "com.application.ryft.sprints")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SprintExceptionHandler {

    @ExceptionHandler(SprintNotFoundException.class)
    public ResponseEntity<ApiError> handleSprintNotFound(SprintNotFoundException ex) {
        return status(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(NoActiveSprintException.class)
    public ResponseEntity<ApiError> handleNoActiveSprint(NoActiveSprintException ex) {
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

    @ExceptionHandler(InsufficientProjectRoleException.class)
    public ResponseEntity<ApiError> handleInsufficientProjectRole(InsufficientProjectRoleException ex) {
        return status(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(InvalidSprintDateRangeException.class)
    public ResponseEntity<ApiError> handleInvalidDateRange(InvalidSprintDateRangeException ex) {
        return status(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(SprintCompletedException.class)
    public ResponseEntity<ApiError> handleSprintCompleted(SprintCompletedException ex) {
        return status(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(IllegalSprintStateTransitionException.class)
    public ResponseEntity<ApiError> handleIllegalSprintStateTransition(IllegalSprintStateTransitionException ex) {
        return status(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(SprintDatesRequiredException.class)
    public ResponseEntity<ApiError> handleSprintDatesRequired(SprintDatesRequiredException ex) {
        return status(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(SprintNotStartedException.class)
    public ResponseEntity<ApiError> handleSprintNotStarted(SprintNotStartedException ex) {
        return status(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(ActiveSprintAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleActiveSprintAlreadyExists(ActiveSprintAlreadyExistsException ex) {
        return status(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(IssueNotFoundException.class)
    public ResponseEntity<ApiError> handleIssueNotFound(IssueNotFoundException ex) {
        return status(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(IssueSprintProjectMismatchException.class)
    public ResponseEntity<ApiError> handleIssueSprintProjectMismatch(IssueSprintProjectMismatchException ex) {
        return status(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(CannotMoveIssueIntoCompletedSprintException.class)
    public ResponseEntity<ApiError> handleCannotMoveIntoCompletedSprint(CannotMoveIssueIntoCompletedSprintException ex) {
        return status(HttpStatus.CONFLICT, ex.getMessage());
    }

    private ResponseEntity<ApiError> status(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(ApiError.of(status.value(), status.getReasonPhrase(), message));
    }
}
