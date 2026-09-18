import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import {
  CreateIssueRequest,
  CreateSubtaskRequest,
  Issue,
  IssueStatus,
  UpdateIssueRequest,
} from './models';

@Injectable({ providedIn: 'root' })
export class IssueService {
  private readonly http = inject(HttpClient);

  /** At most one of `sprintId`/`epicId` should be passed — if both are, the server prefers sprintId. */
  listForProject(projectKey: string, sprintId?: string, epicId?: string): Observable<Issue[]> {
    const url = `${environment.apiBaseUrl}/projects/${projectKey}/issues`;
    const params = sprintId ? `sprintId=${sprintId}` : epicId ? `epicId=${epicId}` : null;
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

  changeStatus(issueKey: string, status: IssueStatus): Observable<Issue> {
    return this.http.patch<Issue>(`${environment.apiBaseUrl}/issues/${issueKey}/status`, {
      status,
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
}
