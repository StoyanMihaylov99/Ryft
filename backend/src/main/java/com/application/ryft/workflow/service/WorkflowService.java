package com.application.ryft.workflow.service;

import com.application.ryft.workflow.dto.WorkflowSchemeResponse;
import java.util.UUID;

public interface WorkflowService {

    /**
     * Returns the project's workflow scheme, creating the fixed default (To Do / In Progress / Done)
     * on first request if none exists yet. There is exactly one scheme per project in v1 — Phase 4
     * adds real per-project configurability.
     */
    WorkflowSchemeResponse getSchemeForProject(UUID callerId, String projectKey);
}
