package com.application.ryft.issues.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.application.ryft.issues.dto.IssueResponse;
import com.application.ryft.issues.entity.Component;
import com.application.ryft.issues.entity.Issue;
import com.application.ryft.issues.entity.IssueComponent;
import com.application.ryft.issues.entity.IssueLabel;
import com.application.ryft.issues.entity.IssuePriority;
import com.application.ryft.issues.entity.IssueType;
import com.application.ryft.issues.entity.Label;
import com.application.ryft.issues.exception.InvalidComponentReferenceException;
import com.application.ryft.issues.exception.InvalidLabelReferenceException;
import com.application.ryft.issues.repository.ComponentRepository;
import com.application.ryft.issues.repository.IssueComponentRepository;
import com.application.ryft.issues.repository.IssueLabelRepository;
import com.application.ryft.issues.repository.LabelRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class IssueLabelingServiceTest {

    @Mock
    private IssueLabelRepository issueLabelRepository;

    @Mock
    private LabelRepository labelRepository;

    @Mock
    private IssueComponentRepository issueComponentRepository;

    @Mock
    private ComponentRepository componentRepository;

    private IssueLabelingService issueLabelingService;

    private final UUID projectId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        issueLabelingService = new IssueLabelingService(issueLabelRepository, labelRepository,
                issueComponentRepository, componentRepository);
    }

    private Issue issueWithId() {
        Issue issue = new Issue(projectId, "TRK-1", IssueType.BUG, "Title", null, IssuePriority.MEDIUM, null,
                UUID.randomUUID(), 1000.0);
        ReflectionTestUtils.setField(issue, "id", UUID.randomUUID());
        return issue;
    }

    private Label labelWithId(String name) {
        Label label = new Label(projectId, name, "#FF0000");
        ReflectionTestUtils.setField(label, "id", UUID.randomUUID());
        return label;
    }

    private Component componentWithId(String name) {
        Component component = new Component(projectId, name);
        ReflectionTestUtils.setField(component, "id", UUID.randomUUID());
        return component;
    }

    @Test
    void attachOnCreateSavesLabelsAndComponentsBelongingToTheProject() {
        Issue issue = issueWithId();
        Label label = labelWithId("Bug");
        Component component = componentWithId("Backend");
        when(labelRepository.findAllByProjectIdAndIdIn(projectId, List.of(label.getId())))
                .thenReturn(List.of(label));
        when(componentRepository.findAllByProjectIdAndIdIn(projectId, List.of(component.getId())))
                .thenReturn(List.of(component));

        issueLabelingService.attachOnCreate(issue, projectId, List.of(label.getId()), List.of(component.getId()));

        ArgumentCaptor<List<IssueLabel>> savedLabels = ArgumentCaptor.forClass(List.class);
        verify(issueLabelRepository).saveAll(savedLabels.capture());
        assertThat(savedLabels.getValue()).extracting(IssueLabel::getIssueId, IssueLabel::getLabelId)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(issue.getId(), label.getId()));

        ArgumentCaptor<List<IssueComponent>> savedComponents = ArgumentCaptor.forClass(List.class);
        verify(issueComponentRepository).saveAll(savedComponents.capture());
        assertThat(savedComponents.getValue()).extracting(IssueComponent::getIssueId, IssueComponent::getComponentId)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(issue.getId(), component.getId()));
    }

    @Test
    void attachOnCreateWithNullListsAttachesNothing() {
        Issue issue = issueWithId();

        issueLabelingService.attachOnCreate(issue, projectId, null, null);

        verify(issueLabelRepository).deleteAllByIssueId(issue.getId());
        verify(issueLabelRepository).saveAll(List.of());
        verify(issueComponentRepository).deleteAllByIssueId(issue.getId());
        verify(issueComponentRepository).saveAll(List.of());
        verify(labelRepository, never()).findAllByProjectIdAndIdIn(any(), any());
    }

    @Test
    void attachOnCreateRejectsLabelFromAnotherProject() {
        Issue issue = issueWithId();
        UUID foreignLabelId = UUID.randomUUID();
        when(labelRepository.findAllByProjectIdAndIdIn(projectId, List.of(foreignLabelId))).thenReturn(List.of());

        assertThatThrownBy(() -> issueLabelingService.attachOnCreate(issue, projectId, List.of(foreignLabelId), null))
                .isInstanceOf(InvalidLabelReferenceException.class);
        verify(issueLabelRepository, never()).saveAll(any());
    }

    @Test
    void attachOnCreateRejectsComponentFromAnotherProject() {
        Issue issue = issueWithId();
        UUID foreignComponentId = UUID.randomUUID();
        when(componentRepository.findAllByProjectIdAndIdIn(projectId, List.of(foreignComponentId)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> issueLabelingService.attachOnCreate(issue, projectId, null,
                List.of(foreignComponentId)))
                .isInstanceOf(InvalidComponentReferenceException.class);
        verify(issueComponentRepository, never()).saveAll(any());
    }

    @Test
    void applyLabelsWithNullLeavesExistingLabelsUnchanged() {
        Issue issue = issueWithId();

        issueLabelingService.applyLabels(issue, projectId, null);

        verify(issueLabelRepository, never()).deleteAllByIssueId(any());
        verify(issueLabelRepository, never()).saveAll(any());
    }

    @Test
    void applyLabelsWithEmptyListClearsAllLabels() {
        Issue issue = issueWithId();

        issueLabelingService.applyLabels(issue, projectId, List.of());

        verify(issueLabelRepository).deleteAllByIssueId(issue.getId());
        verify(issueLabelRepository).saveAll(List.of());
    }

    @Test
    void applyComponentsWithNullLeavesExistingComponentsUnchanged() {
        Issue issue = issueWithId();

        issueLabelingService.applyComponents(issue, projectId, null);

        verify(issueComponentRepository, never()).deleteAllByIssueId(any());
        verify(issueComponentRepository, never()).saveAll(any());
    }

    @Test
    void applyComponentsWithEmptyListClearsAllComponents() {
        Issue issue = issueWithId();

        issueLabelingService.applyComponents(issue, projectId, List.of());

        verify(issueComponentRepository).deleteAllByIssueId(issue.getId());
        verify(issueComponentRepository).saveAll(List.of());
    }

    @Test
    void toResponseIncludesResolvedLabelsAndComponents() {
        Issue issue = issueWithId();
        Label label = labelWithId("Bug");
        Component component = componentWithId("Backend");
        when(issueLabelRepository.findAllByIssueIdIn(List.of(issue.getId())))
                .thenReturn(List.of(new IssueLabel(issue.getId(), label.getId())));
        when(labelRepository.findAllById(any())).thenReturn(List.of(label));
        when(issueComponentRepository.findAllByIssueIdIn(List.of(issue.getId())))
                .thenReturn(List.of(new IssueComponent(issue.getId(), component.getId())));
        when(componentRepository.findAllById(any())).thenReturn(List.of(component));

        IssueResponse result = issueLabelingService.toResponse(issue);

        assertThat(result.labels()).extracting("name").containsExactly("Bug");
        assertThat(result.components()).extracting("name").containsExactly("Backend");
    }

    @Test
    void toResponsesReturnsEmptyListForNoIssues() {
        assertThat(issueLabelingService.toResponses(List.of())).isEmpty();
    }
}
