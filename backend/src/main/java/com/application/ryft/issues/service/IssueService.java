package com.application.ryft.issues.service;

import com.application.ryft.issues.dto.ChangeIssueStatusRequest;
import com.application.ryft.issues.dto.CreateIssueRequest;
import com.application.ryft.issues.dto.IssueResponse;
import com.application.ryft.issues.dto.UpdateIssueRequest;
import java.util.List;
import java.util.UUID;

public interface IssueService {

    IssueResponse create(UUID callerId, String projectKey, CreateIssueRequest request);

    List<IssueResponse> listForProject(UUID callerId, String projectKey);

    IssueResponse get(UUID callerId, String issueKey);

    IssueResponse update(UUID callerId, String issueKey, UpdateIssueRequest request);

    IssueResponse changeStatus(UUID callerId, String issueKey, ChangeIssueStatusRequest request);

    void delete(UUID callerId, String issueKey);
}
