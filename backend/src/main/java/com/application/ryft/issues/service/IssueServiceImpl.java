package com.application.ryft.issues.service;

import com.application.ryft.issues.dto.ChangeIssueStatusRequest;
import com.application.ryft.issues.dto.CreateIssueRequest;
import com.application.ryft.issues.dto.IssueResponse;
import com.application.ryft.issues.dto.UpdateIssueRequest;
import com.application.ryft.issues.entity.Issue;
import com.application.ryft.issues.entity.IssueKeySequence;
import com.application.ryft.issues.entity.IssuePriority;
import com.application.ryft.issues.entity.IssueStatus;
import com.application.ryft.issues.exception.AssigneeNotAProjectMemberException;
import com.application.ryft.issues.exception.InsufficientProjectRoleException;
import com.application.ryft.issues.exception.IssueNotFoundException;
import com.application.ryft.issues.repository.CommentRepository;
import com.application.ryft.issues.repository.IssueKeySequenceRepository;
import com.application.ryft.issues.repository.IssueRepository;
import com.application.ryft.projects.dto.ProjectResponse;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IssueServiceImpl implements IssueService {

    private static final double RANK_STEP = 1000.0;

    private final IssueRepository issueRepository;
    private final IssueKeySequenceRepository issueKeySequenceRepository;
    private final IssueProjectAccess projectAccess;
    private final CommentRepository commentRepository;

    public IssueServiceImpl(IssueRepository issueRepository, IssueKeySequenceRepository issueKeySequenceRepository,
            IssueProjectAccess projectAccess, CommentRepository commentRepository) {
        this.issueRepository = issueRepository;
        this.issueKeySequenceRepository = issueKeySequenceRepository;
        this.projectAccess = projectAccess;
        this.commentRepository = commentRepository;
    }

    @Override
    @Transactional
    public IssueResponse create(UUID callerId, String projectKey, CreateIssueRequest request) {
        ProjectResponse project = projectAccess.requireMembership(callerId, projectKey);
        requireOwnerOrAdmin(callerId, projectKey);
        if (request.assigneeId() != null) {
            requireAssigneeIsProjectMember(callerId, projectKey, request.assigneeId());
        }

        IssuePriority priority = request.priority() != null ? request.priority() : IssuePriority.MEDIUM;
        String description = request.description() == null ? null : request.description().trim();
        String issueKey = nextIssueKey(project.id(), project.key());
        double rank = nextBacklogRank(project.id());

        Issue issue = new Issue(project.id(), issueKey, request.type(), request.title().trim(), description,
                priority, request.assigneeId(), callerId, rank);
        issue.setStoryPoints(request.storyPoints());
        return IssueResponse.from(issueRepository.save(issue));
    }

    @Override
    @Transactional(readOnly = true)
    public List<IssueResponse> listForProject(UUID callerId, String projectKey) {
        ProjectResponse project = projectAccess.requireMembership(callerId, projectKey);
        return issueRepository.findAllByProjectIdOrderByCreatedAtAsc(project.id()).stream()
                .map(IssueResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<IssueResponse> listForProject(UUID callerId, String projectKey, UUID sprintId) {
        ProjectResponse project = projectAccess.requireMembership(callerId, projectKey);
        return issueRepository.findAllByProjectIdAndSprintIdOrderByCreatedAtAsc(project.id(), sprintId).stream()
                .map(IssueResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<IssueResponse> listBacklogForProject(UUID callerId, String projectKey) {
        ProjectResponse project = projectAccess.requireMembership(callerId, projectKey);
        return issueRepository.findAllByProjectIdAndSprintIdIsNullOrderByBacklogRankAsc(project.id()).stream()
                .map(IssueResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<IssueResponse> listForSprint(UUID callerId, String projectKey, UUID sprintId) {
        ProjectResponse project = projectAccess.requireMembership(callerId, projectKey);
        return issueRepository.findAllByProjectIdAndSprintIdOrderByCreatedAtAsc(project.id(), sprintId).stream()
                .map(IssueResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public IssueResponse get(UUID callerId, String issueKey) {
        Issue issue = requireIssue(issueKey);
        projectAccess.requireMembership(callerId, projectKeyOf(issue));
        return IssueResponse.from(issue);
    }

    @Override
    @Transactional
    public IssueResponse update(UUID callerId, String issueKey, UpdateIssueRequest request) {
        Issue issue = requireIssue(issueKey);
        String projectKey = projectKeyOf(issue);
        projectAccess.requireMembership(callerId, projectKey);
        requireOwnerOrAdmin(callerId, projectKey);

        applyUpdate(issue, callerId, projectKey, request);
        return IssueResponse.from(issue);
    }

    @Override
    @Transactional
    public IssueResponse changeStatus(UUID callerId, String issueKey, ChangeIssueStatusRequest request) {
        Issue issue = requireIssue(issueKey);
        projectAccess.requireMembership(callerId, projectKeyOf(issue));
        setStatus(issue, request.status());
        return IssueResponse.from(issue);
    }

    private void applyUpdate(Issue issue, UUID callerId, String projectKey, UpdateIssueRequest request) {
        applyTitle(issue, request);
        applyDescription(issue, request);
        applyPriority(issue, request);
        applyAssignee(issue, callerId, projectKey, request);
        applyStoryPoints(issue, request);
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

    private void applyStoryPoints(Issue issue, UpdateIssueRequest request) {
        if (request.storyPoints() != null) {
            issue.setStoryPoints(request.storyPoints());
        }
    }

    private void setStatus(Issue issue, IssueStatus status) {
        issue.setStatus(status);
        issue.setResolvedAt(status == IssueStatus.DONE ? Instant.now() : null);
    }

    @Override
    @Transactional
    public void delete(UUID callerId, String issueKey) {
        Issue issue = requireIssue(issueKey);
        String projectKey = projectKeyOf(issue);
        projectAccess.requireMembership(callerId, projectKey);
        requireOwnerOrAdmin(callerId, projectKey);
        commentRepository.deleteAllByIssueId(issue.getId());
        issueRepository.delete(issue);
    }

    @Override
    @Transactional
    public IssueResponse moveToSprint(UUID callerId, String issueKey, UUID sprintId) {
        Issue issue = requireIssue(issueKey);
        String projectKey = projectKeyOf(issue);
        projectAccess.requireMembership(callerId, projectKey);
        requireOwnerOrAdmin(callerId, projectKey);

        issue.setSprintId(sprintId);
        return IssueResponse.from(issue);
    }

    @Override
    @Transactional
    public IssueResponse reorderBacklog(UUID callerId, String issueKey, String beforeIssueKey, String afterIssueKey) {
        Issue issue = requireIssue(issueKey);
        String projectKey = projectKeyOf(issue);
        projectAccess.requireMembership(callerId, projectKey);
        requireOwnerOrAdmin(callerId, projectKey);

        Optional<Issue> beforeIssue = findNeighbor(beforeIssueKey);
        Optional<Issue> afterIssue = findNeighbor(afterIssueKey);
        issue.setBacklogRank(newBacklogRank(issue, beforeIssue, afterIssue));
        return IssueResponse.from(issue);
    }

    @Override
    @Transactional
    public void moveUnfinishedIssuesToBacklog(UUID callerId, String projectKey, UUID sprintId) {
        ProjectResponse project = projectAccess.requireMembership(callerId, projectKey);
        issueRepository.findAllByProjectIdAndSprintId(project.id(), sprintId).stream()
                .filter(issue -> issue.getStatus() != IssueStatus.DONE)
                .forEach(issue -> issue.setSprintId(null));
    }

    /**
     * Midpoint-insertion ranking: splits the difference between the two neighbors an issue is dropped
     * between, so re-ranking one issue never touches any other row. Falling off either end of the list
     * steps by a full {@link #RANK_STEP} past the remaining neighbor instead of halving toward it,
     * leaving room for further inserts on that side.
     */
    private double newBacklogRank(Issue issue, Optional<Issue> beforeIssue, Optional<Issue> afterIssue) {
        if (beforeIssue.isPresent() && afterIssue.isPresent()) {
            return (beforeIssue.get().getBacklogRank() + afterIssue.get().getBacklogRank()) / 2.0;
        }
        if (beforeIssue.isPresent()) {
            return beforeIssue.get().getBacklogRank() + RANK_STEP;
        }
        if (afterIssue.isPresent()) {
            return afterIssue.get().getBacklogRank() - RANK_STEP;
        }
        return issue.getBacklogRank();
    }

    private Optional<Issue> findNeighbor(String neighborKey) {
        return neighborKey == null ? Optional.empty() : issueRepository.findByKey(normalizeKey(neighborKey));
    }

    private double nextBacklogRank(UUID projectId) {
        return issueRepository.findFirstByProjectIdOrderByBacklogRankDesc(projectId)
                .map(issue -> issue.getBacklogRank() + RANK_STEP)
                .orElse(RANK_STEP);
    }

    private void requireOwnerOrAdmin(UUID callerId, String projectKey) {
        if (!projectAccess.isOwnerOrAdmin(callerId, projectKey)) {
            throw new InsufficientProjectRoleException();
        }
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

    private void requireAssigneeIsProjectMember(UUID callerId, String projectKey, UUID assigneeId) {
        if (!projectAccess.isMember(callerId, projectKey, assigneeId)) {
            throw new AssigneeNotAProjectMemberException();
        }
    }

    private String normalizeKey(String key) {
        return key.trim().toUpperCase(Locale.ROOT);
    }
}
