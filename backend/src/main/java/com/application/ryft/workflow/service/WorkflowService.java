package com.application.ryft.workflow.service;

import com.application.ryft.workflow.dto.UpdateWorkflowSchemeRequest;
import com.application.ryft.workflow.dto.WorkflowSchemeResponse;
import com.application.ryft.workflow.dto.WorkflowStatusResponse;
import com.application.ryft.workflow.entity.StatusCategory;
import java.util.List;
import java.util.UUID;

public interface WorkflowService {

    /**
     * Returns the project's workflow scheme, creating the fixed default (To Do / Blocked / In Progress /
     * Done, any-to-any transitions) on first request if none exists yet. There is exactly one scheme per
     * project in v1. Schemes/transition graphs created before a later addition (e.g. {@code BLOCKED}, or
     * transitions themselves) existed are backfilled lazily on next read — there's no Flyway/Liquibase in
     * this project, just Hibernate {@code ddl-auto: update}.
     */
    WorkflowSchemeResponse getSchemeForProject(UUID callerId, String projectKey);

    /**
     * Applies a full statuses+transitions diff to the project's scheme — see
     * {@code WorkflowServiceImpl.updateScheme}'s javadoc for the exact create/update/delete rules and the
     * v1 same-request-reference restriction. Owner/Admin only.
     */
    WorkflowSchemeResponse updateScheme(UUID callerId, String projectKey, UpdateWorkflowSchemeRequest request);

    /** {@code fromStatusId == toStatusId} is always legal (no-op moves, e.g. re-saving an unrelated field). */
    boolean isTransitionLegal(UUID callerId, String projectKey, UUID fromStatusId, UUID toStatusId);

    WorkflowStatusResponse getStatus(UUID callerId, String projectKey, UUID statusId);

    /** Every status id in the project's scheme belonging to the given category — "done" is a set, not one fixed id. */
    List<UUID> getStatusIdsInCategory(UUID callerId, String projectKey, StatusCategory category);
}
