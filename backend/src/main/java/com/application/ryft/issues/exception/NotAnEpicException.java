package com.application.ryft.issues.exception;

/**
 * Thrown when an operation that only makes sense for an EPIC (currently just progress reporting) is
 * called on a STORY/TASK/BUG/SUBTASK key instead — those issue types have no "children" in the
 * progress sense, so silently returning a 0/0 result would hide a caller bug rather than surface it.
 */
public class NotAnEpicException extends RuntimeException {

    public NotAnEpicException(String key) {
        super("Issue " + key + " is not an Epic and has no progress to report");
    }
}
