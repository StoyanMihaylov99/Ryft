import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Burndown, CreateSprintRequest, Sprint, SprintBoard, UpdateSprintRequest } from './models';

@Injectable({ providedIn: 'root' })
export class SprintService {
  private readonly http = inject(HttpClient);

  listForProject(projectKey: string): Observable<Sprint[]> {
    return this.http.get<Sprint[]>(`${environment.apiBaseUrl}/projects/${projectKey}/sprints`);
  }

  create(projectKey: string, request: CreateSprintRequest): Observable<Sprint> {
    return this.http.post<Sprint>(
      `${environment.apiBaseUrl}/projects/${projectKey}/sprints`,
      request,
    );
  }

  update(sprintId: string, request: UpdateSprintRequest): Observable<Sprint> {
    return this.http.patch<Sprint>(`${environment.apiBaseUrl}/sprints/${sprintId}`, request);
  }

  start(sprintId: string): Observable<Sprint> {
    return this.http.post<Sprint>(`${environment.apiBaseUrl}/sprints/${sprintId}/start`, {});
  }

  complete(sprintId: string): Observable<Sprint> {
    return this.http.post<Sprint>(`${environment.apiBaseUrl}/sprints/${sprintId}/complete`, {});
  }

  getBoard(projectKey: string): Observable<SprintBoard> {
    return this.http.get<SprintBoard>(`${environment.apiBaseUrl}/projects/${projectKey}/board/sprint`);
  }

  getBurndown(sprintId: string): Observable<Burndown> {
    return this.http.get<Burndown>(`${environment.apiBaseUrl}/sprints/${sprintId}/burndown`);
  }
}
