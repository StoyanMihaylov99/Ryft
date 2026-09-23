package com.application.ryft.search.service;

import com.application.ryft.issues.dto.IssueResponse;
import com.application.ryft.search.dto.IssueSearchRequest;
import java.util.List;
import java.util.UUID;

public interface ProjectSearchService {

    /**
     * Structured, AND-combining issue search for a project — see {@code IssueService#search} for the
     * full filter semantics {@code request} is mapped onto. Any project member, including Viewer, may
     * call it — same as every other list endpoint.
     */
    List<IssueResponse> search(UUID callerId, String projectKey, IssueSearchRequest request);
}
