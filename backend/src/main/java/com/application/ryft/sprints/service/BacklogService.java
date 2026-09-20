package com.application.ryft.sprints.service;

import com.application.ryft.issues.dto.IssueResponse;
import java.util.List;
import java.util.UUID;

public interface BacklogService {

    List<IssueResponse> listBacklog(UUID callerId, String projectKey);

    /** {@code sprintId == null} moves the issue back to the backlog. Owner/Admin only. */
    IssueResponse moveIssue(UUID callerId, String issueKey, UUID sprintId);
}
