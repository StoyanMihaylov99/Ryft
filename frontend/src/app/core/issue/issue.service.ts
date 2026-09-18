import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CreateIssueRequest, Issue, IssueStatus, UpdateIssueRequest } from './models';

@Injectable({ providedIn: 'root' })
export class IssueService {
  private readonly http = inject(HttpClient);

  listForProject(projectKey: string, sprintId?: string): Observable<Issue[]> {
    const url = `${environment.apiBaseUrl}/projects/${projectKey}/issues`;
    return this.http.get<Issue[]>(sprintId ? `${url}?sprintId=${sprintId}` : url);
  }

  listBacklog(projectKey: string): Observable<Issue[]> {
    return this.http.get<Issue[]>(`${environment.apiBaseUrl}/projects/${projectKey}/backlog`);
  }

  moveToSprint(issueKey: string, sprintId: string | null): Observable<Issue> {
    return this.http.patch<Issue>(`${environment.apiBaseUrl}/issues/${issueKey}/sprint`, { sprintId });
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
    return this.http.post<Issue>(`${environment.apiBaseUrl}/projects/${projectKey}/issues`, request);
  }

  get(issueKey: string): Observable<Issue> {
    return this.http.get<Issue>(`${environment.apiBaseUrl}/issues/${issueKey}`);
  }

  update(issueKey: string, request: UpdateIssueRequest): Observable<Issue> {
    return this.http.patch<Issue>(`${environment.apiBaseUrl}/issues/${issueKey}`, request);
  }

  changeStatus(issueKey: string, status: IssueStatus): Observable<Issue> {
    return this.http.patch<Issue>(`${environment.apiBaseUrl}/issues/${issueKey}/status`, { status });
  }

  delete(issueKey: string): Observable<void> {
    return this.http.delete<void>(`${environment.apiBaseUrl}/issues/${issueKey}`);
  }
}
