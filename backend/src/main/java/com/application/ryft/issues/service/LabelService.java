package com.application.ryft.issues.service;

import com.application.ryft.issues.dto.CreateLabelRequest;
import com.application.ryft.issues.dto.LabelResponse;
import com.application.ryft.issues.dto.UpdateLabelRequest;
import java.util.List;
import java.util.UUID;

public interface LabelService {

    /** Owner/Admin only — same gate as issue create. Rejects a duplicate name within the project (409). */
    LabelResponse create(UUID callerId, String projectKey, CreateLabelRequest request);

    /** Any project member, ordered by name. */
    List<LabelResponse> listForProject(UUID callerId, String projectKey);

    /** Owner/Admin only. A null field in {@code request} leaves that field unchanged. */
    LabelResponse update(UUID callerId, String projectKey, UUID labelId, UpdateLabelRequest request);

    /** Owner/Admin only. Also removes every {@code IssueLabel} row referencing this label. */
    void delete(UUID callerId, String projectKey, UUID labelId);
}
