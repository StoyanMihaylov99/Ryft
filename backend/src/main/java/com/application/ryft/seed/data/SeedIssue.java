package com.application.ryft.seed.data;

import com.application.ryft.issues.entity.IssuePriority;
import com.application.ryft.issues.entity.IssueType;
import com.application.ryft.workflow.entity.StatusCategory;
import java.util.List;

/**
 * One issue in a {@link SeedProject}'s declarative issue list, processed in list order by
 * {@code DemoProjectInstaller} — an issue referenced via {@code childOf}/{@code inSprint} must already
 * appear earlier in the same project's list (epics before their linked stories/tasks/bugs, those before
 * their own subtasks), the same "declare it in dependency order" rule the whole data set follows instead
 * of doing a topological sort at seed time.
 *
 * <p>A fluent builder, not a positional record: several fields are adjacent same-typed strings
 * ({@code assigneeEmail}/{@code reporterEmail}, {@code parentTitle}/{@code sprintName}) that a
 * positional constructor call lets a reader (or author) silently transpose without the compiler ever
 * catching it — the whole point of {@link DemoDataSet} being readable is defeated if a swapped pair of
 * emails only surfaces as a confusing runtime 403/404 during seeding. Named methods make each value
 * self-describing at the call site instead, e.g. {@code SeedIssue.story("...").assignedTo(BOB_EMAIL)
 * .reportedBy(ALICE_EMAIL)}.
 *
 * <p>{@code childOf} is dual-purpose, same as {@code Issue.parentIssueId}: for a STORY/TASK/BUG it's an
 * optional epic link; for a SUBTASK it's the required parent STORY/TASK/BUG. {@code reportedBy} defaults
 * to the project's Owner (the demo user) when unset — every other value must be a non-Viewer member of
 * the project, since {@code IssueService#create} rejects a Viewer caller. {@code movedTo} is unset to
 * leave a freshly-created issue at its scheme's default initial status (lowest {@code sortOrder}, "To Do"
 * in the fixed default scheme) — set it to move the issue to a representative status in that category via
 * {@code IssueService#changeStatus} once the issue exists. {@code inSprint} is unset for a backlog issue,
 * otherwise a name from the same project's {@code sprints} list.
 */
public final class SeedIssue {

    private final IssueType type;
    private final String title;
    private String description;
    private IssuePriority priority;
    private Integer storyPoints;
    private String assigneeEmail;
    private String reporterEmail;
    private List<String> labelNames = List.of();
    private List<String> componentNames = List.of();
    private String parentTitle;
    private StatusCategory targetStatusCategory;
    private String sprintName;
    private List<SeedComment> comments = List.of();

    private SeedIssue(IssueType type, String title) {
        this.type = type;
        this.title = title;
    }

    public static SeedIssue epic(String title) {
        return new SeedIssue(IssueType.EPIC, title);
    }

    public static SeedIssue story(String title) {
        return new SeedIssue(IssueType.STORY, title);
    }

    public static SeedIssue task(String title) {
        return new SeedIssue(IssueType.TASK, title);
    }

    public static SeedIssue bug(String title) {
        return new SeedIssue(IssueType.BUG, title);
    }

    public static SeedIssue subtask(String title) {
        return new SeedIssue(IssueType.SUBTASK, title);
    }

    public SeedIssue describedAs(String description) {
        this.description = description;
        return this;
    }

    public SeedIssue priority(IssuePriority priority) {
        this.priority = priority;
        return this;
    }

    public SeedIssue storyPoints(int storyPoints) {
        this.storyPoints = storyPoints;
        return this;
    }

    public SeedIssue assignedTo(String email) {
        this.assigneeEmail = email;
        return this;
    }

    public SeedIssue reportedBy(String email) {
        this.reporterEmail = email;
        return this;
    }

    public SeedIssue labeled(String... labelNames) {
        this.labelNames = List.of(labelNames);
        return this;
    }

    public SeedIssue inComponents(String... componentNames) {
        this.componentNames = List.of(componentNames);
        return this;
    }

    public SeedIssue childOf(String parentTitle) {
        this.parentTitle = parentTitle;
        return this;
    }

    public SeedIssue movedTo(StatusCategory targetStatusCategory) {
        this.targetStatusCategory = targetStatusCategory;
        return this;
    }

    public SeedIssue inSprint(String sprintName) {
        this.sprintName = sprintName;
        return this;
    }

    public SeedIssue commented(SeedComment... comments) {
        this.comments = List.of(comments);
        return this;
    }

    public IssueType type() {
        return type;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public IssuePriority priority() {
        return priority;
    }

    public Integer storyPoints() {
        return storyPoints;
    }

    public String assigneeEmail() {
        return assigneeEmail;
    }

    public String reporterEmail() {
        return reporterEmail;
    }

    public List<String> labelNames() {
        return labelNames;
    }

    public List<String> componentNames() {
        return componentNames;
    }

    public String parentTitle() {
        return parentTitle;
    }

    public StatusCategory targetStatusCategory() {
        return targetStatusCategory;
    }

    public String sprintName() {
        return sprintName;
    }

    public List<SeedComment> comments() {
        return comments;
    }
}
