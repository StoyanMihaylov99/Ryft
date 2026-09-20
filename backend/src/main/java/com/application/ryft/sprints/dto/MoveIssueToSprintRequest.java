package com.application.ryft.sprints.dto;

import java.util.UUID;

/** The whole body is this one field; {@code sprintId == null} explicitly means "move to the backlog". */
public record MoveIssueToSprintRequest(UUID sprintId) {
}
