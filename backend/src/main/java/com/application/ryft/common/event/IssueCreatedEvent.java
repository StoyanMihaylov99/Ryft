package com.application.ryft.common.event;

import java.util.UUID;

/**
 * Published after {@code IssueServiceImpl.create} commits. Plain record, zero dependency on the
 * {@code issues}/{@code activity}/{@code notifications} modules — every class in this package stays a
 * dependency-free leaf so it can be published by any module and consumed by any other without creating
 * a module-boundary cycle.
 */
public record IssueCreatedEvent(
        UUID issueId,
        String issueKey,
        UUID projectId,
        String projectKey,
        UUID actorId,
        UUID assigneeId,
        UUID reporterId,
        String title,
        String issueType
) {
}
