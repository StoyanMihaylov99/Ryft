import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import {
  CreateComponentRequest,
  CreateIssueRequest,
  CreateLabelRequest,
  CreateSubtaskRequest,
  EpicProgress,
  Issue,
  Label,
  ProjectComponent,
  UpdateComponentRequest,
  UpdateIssueRequest,
  UpdateLabelRequest,
} from './models';

@Injectable({ providedIn: 'root' })
export class IssueService {
  private readonly http = inject(HttpClient);

  /** At most one of `sprintId`/`epicId`/`labelId`/`componentId` should be passed — the server applies
   *  only the highest-precedence one present (sprintId > epicId > labelId > componentId), never
   *  combining them. */
  listForProject(
    projectKey: string,
    sprintId?: string,
    epicId?: string,
    labelId?: string,
    componentId?: string,
  ): Observable<Issue[]> {
    const url = `${environment.apiBaseUrl}/projects/${projectKey}/issues`;
    const params = sprintId
      ? `sprintId=${sprintId}`
      : epicId
        ? `epicId=${epicId}`
        : labelId
          ? `labelId=${labelId}`
          : componentId
            ? `componentId=${componentId}`
            : null;
    return this.http.get<Issue[]>(params ? `${url}?${params}` : url);
  }

  /** No dedicated backend endpoint exists for this — it's the project's issue list filtered client-side. */
  listEpics(projectKey: string): Observable<Issue[]> {
    return this.listForProject(projectKey).pipe(
      map((issues) => issues.filter((issue) => issue.type === 'EPIC')),
    );
  }

  listBacklog(projectKey: string): Observable<Issue[]> {
    return this.http.get<Issue[]>(`${environment.apiBaseUrl}/projects/${projectKey}/backlog`);
  }

  moveToSprint(issueKey: string, sprintId: string | null): Observable<Issue> {
    return this.http.patch<Issue>(`${environment.apiBaseUrl}/issues/${issueKey}/sprint`, {
      sprintId,
    });
  }

  reorderBacklog(
    issueKey: string,
    beforeIssueKey: string | null,
    afterIssueKey: string | null,
  ): Observable<Issue> {
    return this.http.patch<Issue>(`${environment.apiBaseUrl}/issues/${issueKey}/backlog-rank`, {
      beforeIssueKey,
      afterIssueKey,
    });
  }

  create(projectKey: string, request: CreateIssueRequest): Observable<Issue> {
    return this.http.post<Issue>(
      `${environment.apiBaseUrl}/projects/${projectKey}/issues`,
      request,
    );
  }

  get(issueKey: string): Observable<Issue> {
    return this.http.get<Issue>(`${environment.apiBaseUrl}/issues/${issueKey}`);
  }

  update(issueKey: string, request: UpdateIssueRequest): Observable<Issue> {
    return this.http.patch<Issue>(`${environment.apiBaseUrl}/issues/${issueKey}`, request);
  }

  /** `statusId` must resolve to a `WorkflowStatus` in the issue's project scheme, and the move from
   *  the issue's current status must be legal per that scheme's transition graph — otherwise this
   *  409s (`IllegalStatusTransitionException` server-side). */
  changeStatus(issueKey: string, statusId: string): Observable<Issue> {
    return this.http.patch<Issue>(`${environment.apiBaseUrl}/issues/${issueKey}/status`, {
      statusId,
    });
  }

  delete(issueKey: string): Observable<void> {
    return this.http.delete<void>(`${environment.apiBaseUrl}/issues/${issueKey}`);
  }

  createSubtask(issueKey: string, request: CreateSubtaskRequest): Observable<Issue> {
    return this.http.post<Issue>(`${environment.apiBaseUrl}/issues/${issueKey}/subtasks`, request);
  }

  listSubtasks(issueKey: string): Observable<Issue[]> {
    return this.http.get<Issue[]>(`${environment.apiBaseUrl}/issues/${issueKey}/subtasks`);
  }

  /** `epicKey` must identify an EPIC issue — 400 otherwise. See EpicProgress's javadoc for scope. */
  getEpicProgress(epicKey: string): Observable<EpicProgress> {
    return this.http.get<EpicProgress>(`${environment.apiBaseUrl}/issues/${epicKey}/progress`);
  }

  listLabels(projectKey: string): Observable<Label[]> {
    return this.http.get<Label[]>(`${environment.apiBaseUrl}/projects/${projectKey}/labels`);
  }

  /** Owner/Admin only — server-enforced (403 otherwise). */
  createLabel(projectKey: string, request: CreateLabelRequest): Observable<Label> {
    return this.http.post<Label>(
      `${environment.apiBaseUrl}/projects/${projectKey}/labels`,
      request,
    );
  }

  /** Owner/Admin only — server-enforced (403 otherwise). */
  updateLabel(projectKey: string, labelId: string, request: UpdateLabelRequest): Observable<Label> {
    return this.http.patch<Label>(
      `${environment.apiBaseUrl}/projects/${projectKey}/labels/${labelId}`,
      request,
    );
  }

  /** Owner/Admin only — server-enforced (403 otherwise). Cascades: removes the label from every
   *  issue it was attached to rather than failing. */
  deleteLabel(projectKey: string, labelId: string): Observable<void> {
    return this.http.delete<void>(
      `${environment.apiBaseUrl}/projects/${projectKey}/labels/${labelId}`,
    );
  }

  listComponents(projectKey: string): Observable<ProjectComponent[]> {
    return this.http.get<ProjectComponent[]>(
      `${environment.apiBaseUrl}/projects/${projectKey}/components`,
    );
  }

  /** Owner/Admin only — server-enforced (403 otherwise). */
  createComponent(
    projectKey: string,
    request: CreateComponentRequest,
  ): Observable<ProjectComponent> {
    return this.http.post<ProjectComponent>(
      `${environment.apiBaseUrl}/projects/${projectKey}/components`,
      request,
    );
  }

  /** Owner/Admin only — server-enforced (403 otherwise). */
  updateComponent(
    projectKey: string,
    componentId: string,
    request: UpdateComponentRequest,
  ): Observable<ProjectComponent> {
    return this.http.patch<ProjectComponent>(
      `${environment.apiBaseUrl}/projects/${projectKey}/components/${componentId}`,
      request,
    );
  }

  /** Owner/Admin only — server-enforced (403 otherwise). Cascades: removes the component from every
   *  issue it was attached to rather than failing. */
  deleteComponent(projectKey: string, componentId: string): Observable<void> {
    return this.http.delete<void>(
      `${environment.apiBaseUrl}/projects/${projectKey}/components/${componentId}`,
    );
  }
}
