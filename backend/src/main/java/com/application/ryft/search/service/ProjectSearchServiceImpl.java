package com.application.ryft.search.service;

import com.application.ryft.issues.dto.IssueResponse;
import com.application.ryft.issues.dto.IssueSearchCriteria;
import com.application.ryft.issues.service.IssueService;
import com.application.ryft.search.dto.IssueSearchRequest;
import com.application.ryft.search.exception.NotAProjectMemberException;
import com.application.ryft.search.exception.ProjectNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Thin pass-through onto {@code issues.service.IssueService#search} — this module owns no persistence of
 * its own yet (saved filters are a later roadmap task), so there's no repository here, only cross-module
 * exception translation. Mirrors {@code sprints.service.BacklogServiceImpl.listBacklog}: {@code issues}'
 * exception types can't be caught by {@code issues.exception.IssueExceptionHandler} here, since that
 * advice is scoped by the *controller's* package (see {@code ProjectNotFoundException}'s javadoc), not
 * this module's.
 */
@Service
public class ProjectSearchServiceImpl implements ProjectSearchService {

    private final IssueService issueService;

    public ProjectSearchServiceImpl(IssueService issueService) {
        this.issueService = issueService;
    }

    @Override
    public List<IssueResponse> search(UUID callerId, String projectKey, IssueSearchRequest request) {
        IssueSearchCriteria criteria = new IssueSearchCriteria(request.assigneeIds(), request.statusIds(),
                request.labelIds(), request.componentIds(), request.types(), request.sprintIds(), request.text());
        try {
            return issueService.search(callerId, projectKey, criteria);
        } catch (com.application.ryft.issues.exception.ProjectNotFoundException e) {
            throw new ProjectNotFoundException(projectKey);
        } catch (com.application.ryft.issues.exception.NotAProjectMemberException e) {
            throw new NotAProjectMemberException();
        }
    }
}
