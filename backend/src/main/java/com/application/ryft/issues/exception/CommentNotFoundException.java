package com.application.ryft.issues.exception;

public class CommentNotFoundException extends RuntimeException {

    public CommentNotFoundException() {
        super("No comment with that id");
    }
}
