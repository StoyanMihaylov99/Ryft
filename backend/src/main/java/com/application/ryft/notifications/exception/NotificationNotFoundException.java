package com.application.ryft.notifications.exception;

public class NotificationNotFoundException extends RuntimeException {

    public NotificationNotFoundException() {
        super("No notification with that id for this user");
    }
}
