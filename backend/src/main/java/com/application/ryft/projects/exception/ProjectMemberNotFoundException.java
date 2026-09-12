package com.application.ryft.projects.exception;

public class ProjectMemberNotFoundException extends RuntimeException {

    public ProjectMemberNotFoundException() {
        super("That user is not a member of this project");
    }
}
