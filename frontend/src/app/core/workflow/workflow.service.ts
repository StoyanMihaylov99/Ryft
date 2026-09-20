import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { UpdateWorkflowRequest, WorkflowScheme } from './models';

@Injectable({ providedIn: 'root' })
export class WorkflowService {
  private readonly http = inject(HttpClient);

  /** Any project member. The default scheme (and its any-to-any transition graph) is created
   *  lazily server-side on first request for a project, so this never 404s for an existing project. */
  get(projectKey: string): Observable<WorkflowScheme> {
    return this.http.get<WorkflowScheme>(`${environment.apiBaseUrl}/projects/${projectKey}/workflow`);
  }

  /** Owner/Admin only — server-enforced (403 otherwise). A full statuses+transitions diff, batch-saved
   *  in one request so the backend can validate the whole graph atomically. */
  update(projectKey: string, request: UpdateWorkflowRequest): Observable<WorkflowScheme> {
    return this.http.patch<WorkflowScheme>(
      `${environment.apiBaseUrl}/projects/${projectKey}/workflow`,
      request,
    );
  }
}
