package com.application.ryft.issues.service;

import com.application.ryft.issues.dto.CreateIssueRequest;
import com.application.ryft.issues.dto.IssueResponse;
import com.application.ryft.issues.dto.UpdateIssueRequest;
import com.application.ryft.issues.entity.Issue;
import com.application.ryft.issues.entity.IssueKeySequence;
import com.application.ryft.issues.entity.IssuePriority;
import com.application.ryft.issues.entity.IssueStatus;
import com.application.ryft.issues.exception.AssigneeNotAProjectMemberException;
import com.application.ryft.issues.exception.IssueNotFoundException;
import com.application.ryft.issues.exception.NotAProjectMemberException;
import com.application.ryft.issues.exception.ProjectNotFoundException;
import com.application.ryft.issues.repository.IssueKeySequenceRepository;
import com.application.ryft.issues.repository.IssueRepository;
import com.application.ryft.projects.dto.ProjectResponse;
import com.application.ryft.projects.service.ProjectService;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IssueServiceImpl implements IssueService {

    private final IssueRepository issueRepository;
    private final IssueKeySequenceRepository issueKeySequenceRepository;
    private final ProjectService projectService;

    public IssueServiceImpl(IssueRepository issueRepository, IssueKeySequenceRepository issueKeySequenceRepository,
            ProjectService projectService) {
        this.issueRepository = issueRepository;
        this.issueKeySequenceRepository = issueKeySequenceRepository;
        this.projectService = projectService;
    }

    @Override
    @Transactional
    public IssueResponse create(UUID callerId, String projectKey, CreateIssueRequest request) {
        ProjectResponse project = requireProjectMembership(callerId, projectKey);
        if (request.assigneeId() != null) {
            requireAssigneeIsProjectMember(callerId, projectKey, request.assigneeId());
        }

        IssuePriority priority = request.priority() != null ? request.priority() : IssuePriority.MEDIUM;
        String description = request.description() == null ? null : request.description().trim();
        String issueKey = nextIssueKey(project.id(), project.key());

        Issue issue = issueRepository.save(new Issue(project.id(), issueKey, request.type(), request.title().trim(),
                description, priority, request.assigneeId(), callerId));
        return toResponse(issue);
    }

    @Override
    @Transactional(readOnly = true)
    public List<IssueResponse> listForProject(UUID callerId, String projectKey) {
        ProjectResponse project = requireProjectMembership(callerId, projectKey);
        return issueRepository.findAllByProjectIdOrderByCreatedAtAsc(project.id()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public IssueResponse get(UUID callerId, String issueKey) {
        Issue issue = requireIssue(issueKey);
        requireProjectMembership(callerId, projectKeyOf(issue));
        return toResponse(issue);
    }

    @Override
    @Transactional
    public IssueResponse update(UUID callerId, String issueKey, UpdateIssueRequest request) {
        Issue issue = requireIssue(issueKey);
        String projectKey = projectKeyOf(issue);
        requireProjectMembership(callerId, projectKey);

        applyUpdate(issue, callerId, projectKey, request);
        return toResponse(issue);
    }

    private void applyUpdate(Issue issue, UUID callerId, String projectKey, UpdateIssueRequest request) {
        applyTitle(issue, request);
        applyDescription(issue, request);
        applyPriority(issue, request);
        applyAssignee(issue, callerId, projectKey, request);
        applyStatus(issue, request);
    }

    private void applyTitle(Issue issue, UpdateIssueRequest request) {
        if (request.title() != null && !request.title().isBlank()) {
            issue.setTitle(request.title().trim());
        }
    }

    private void applyDescription(Issue issue, UpdateIssueRequest request) {
        if (request.description() != null) {
            issue.setDescription(request.description().trim());
        }
    }

    private void applyPriority(Issue issue, UpdateIssueRequest request) {
        if (request.priority() != null) {
            issue.setPriority(request.priority());
        }
    }

    private void applyAssignee(Issue issue, UUID callerId, String projectKey, UpdateIssueRequest request) {
        if (request.assigneeId() != null) {
            requireAssigneeIsProjectMember(callerId, projectKey, request.assigneeId());
            issue.setAssigneeId(request.assigneeId());
        }
    }

    private void applyStatus(Issue issue, UpdateIssueRequest request) {
        if (request.status() != null) {
            issue.setStatus(request.status());
            issue.setResolvedAt(request.status() == IssueStatus.DONE ? Instant.now() : null);
        }
    }

    @Override
    @Transactional
    public void delete(UUID callerId, String issueKey) {
        Issue issue = requireIssue(issueKey);
        requireProjectMembership(callerId, projectKeyOf(issue));
        issueRepository.delete(issue);
    }

    private String nextIssueKey(UUID projectId, String projectKey) {
        IssueKeySequence sequence = issueKeySequenceRepository.findForUpdate(projectId)
                .orElseGet(() -> new IssueKeySequence(projectId));
        long number = sequence.incrementAndGet();
        issueKeySequenceRepository.save(sequence);
        return projectKey + "-" + number;
    }

    private Issue requireIssue(String issueKey) {
        return issueRepository.findByKey(normalizeKey(issueKey))
                .orElseThrow(() -> new IssueNotFoundException(issueKey));
    }

    /** Safe because a project key (validated at creation) never contains a hyphen — see CreateProjectRequest. */
    private String projectKeyOf(Issue issue) {
        return issue.getKey().substring(0, issue.getKey().lastIndexOf('-'));
    }

    private ProjectResponse requireProjectMembership(UUID callerId, String projectKey) {
        try {
            return projectService.get(callerId, projectKey);
        } catch (com.application.ryft.projects.exception.ProjectNotFoundException e) {
            throw new ProjectNotFoundException(projectKey);
        } catch (com.application.ryft.projects.exception.NotAProjectMemberException e) {
            throw new NotAProjectMemberException();
        }
    }

    private void requireAssigneeIsProjectMember(UUID callerId, String projectKey, UUID assigneeId) {
        boolean isMember = projectService.listMembers(callerId, projectKey).stream()
                .anyMatch(member -> member.userId().equals(assigneeId));
        if (!isMember) {
            throw new AssigneeNotAProjectMemberException();
        }
    }

    private IssueResponse toResponse(Issue issue) {
        return new IssueResponse(issue.getId(), issue.getProjectId(), issue.getKey(), issue.getType(), issue.getTitle(),
                issue.getDescription(), issue.getStatus(), issue.getPriority(), issue.getAssigneeId(),
                issue.getReporterId(), issue.getCreatedAt(), issue.getUpdatedAt(), issue.getResolvedAt());
    }

    private String normalizeKey(String key) {
        return key.trim().toUpperCase(Locale.ROOT);
    }
}
