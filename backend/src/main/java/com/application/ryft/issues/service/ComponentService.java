package com.application.ryft.issues.service;

import com.application.ryft.issues.dto.ComponentResponse;
import com.application.ryft.issues.dto.CreateComponentRequest;
import com.application.ryft.issues.dto.UpdateComponentRequest;
import java.util.List;
import java.util.UUID;

public interface ComponentService {

    /** Owner/Admin only — same gate as issue create. Rejects a duplicate name within the project (409). */
    ComponentResponse create(UUID callerId, String projectKey, CreateComponentRequest request);

    /** Any project member, ordered by name. */
    List<ComponentResponse> listForProject(UUID callerId, String projectKey);

    /** Owner/Admin only. A null field in {@code request} leaves that field unchanged. */
    ComponentResponse update(UUID callerId, String projectKey, UUID componentId, UpdateComponentRequest request);

    /** Owner/Admin only. Also removes every {@code IssueComponent} row referencing this component. */
    void delete(UUID callerId, String projectKey, UUID componentId);
}
