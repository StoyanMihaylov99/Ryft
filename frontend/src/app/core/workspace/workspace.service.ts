import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ChangeRoleRequest, CreateWorkspaceRequest, InviteRequest, WorkspaceMember, WorkspaceRole } from './models';

@Injectable({ providedIn: 'root' })
export class WorkspaceService {
  private readonly http = inject(HttpClient);

  completeSetup(name: string, slug: string): Observable<WorkspaceMember> {
    const request: CreateWorkspaceRequest = { name, slug };
    return this.http.post<WorkspaceMember>(`${environment.apiBaseUrl}/workspace/setup`, request);
  }

  listMembers(): Observable<WorkspaceMember[]> {
    return this.http.get<WorkspaceMember[]>(`${environment.apiBaseUrl}/workspace/members`);
  }

  invite(email: string, role: WorkspaceRole): Observable<WorkspaceMember> {
    const request: InviteRequest = { email, role };
    return this.http.post<WorkspaceMember>(`${environment.apiBaseUrl}/workspace/invite`, request);
  }

  changeRole(userId: string, role: WorkspaceRole): Observable<WorkspaceMember> {
    const request: ChangeRoleRequest = { role };
    return this.http.patch<WorkspaceMember>(`${environment.apiBaseUrl}/workspace/members/${userId}`, request);
  }
}
