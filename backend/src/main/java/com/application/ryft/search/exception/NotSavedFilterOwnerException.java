package com.application.ryft.search.exception;

/** Mirrors {@code issues.exception.NotCommentAuthorException}: owner-only, regardless of project role. */
public class NotSavedFilterOwnerException extends RuntimeException {

    public NotSavedFilterOwnerException() {
        super("You can only delete your own saved filters");
    }
}
