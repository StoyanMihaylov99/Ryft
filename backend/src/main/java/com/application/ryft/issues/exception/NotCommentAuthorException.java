package com.application.ryft.issues.exception;

public class NotCommentAuthorException extends RuntimeException {

    public NotCommentAuthorException() {
        super("You can only edit or delete your own comments");
    }
}
