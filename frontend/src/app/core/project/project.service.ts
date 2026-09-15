import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AddProjectMemberRequest, CreateProjectRequest, Project, ProjectMember, ProjectRole } from './models';

@Injectable({ providedIn: 'root' })
export class ProjectService {
  private readonly http = inject(HttpClient);

  /** Non-archived projects the caller is a member of. */
  list(): Observable<Project[]> {
    return this.http.get<Project[]>(`${environment.apiBaseUrl}/projects`);
  }

  get(projectKey: string): Observable<Project> {
    return this.http.get<Project>(`${environment.apiBaseUrl}/projects/${projectKey}`);
  }

  create(request: CreateProjectRequest): Observable<Project> {
    return this.http.post<Project>(`${environment.apiBaseUrl}/projects`, request);
  }

  listMembers(projectKey: string): Observable<ProjectMember[]> {
    return this.http.get<ProjectMember[]>(`${environment.apiBaseUrl}/projects/${projectKey}/members`);
  }

  /** Adds an existing user (by email) to the project. Owner/Admin only; enforced server-side. */
  addMember(projectKey: string, request: AddProjectMemberRequest): Observable<ProjectMember> {
    return this.http.post<ProjectMember>(`${environment.apiBaseUrl}/projects/${projectKey}/members`, request);
  }

  /** Owner only; enforced server-side. */
  changeMemberRole(projectKey: string, userId: string, role: ProjectRole): Observable<ProjectMember> {
    return this.http.patch<ProjectMember>(`${environment.apiBaseUrl}/projects/${projectKey}/members/${userId}`, {
      role,
    });
  }

  /** Owner only; enforced server-side. */
  removeMember(projectKey: string, userId: string): Observable<void> {
    return this.http.delete<void>(`${environment.apiBaseUrl}/projects/${projectKey}/members/${userId}`);
  }
}
