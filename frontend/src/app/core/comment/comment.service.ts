import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Comment } from './models';

@Injectable({ providedIn: 'root' })
export class CommentService {
  private readonly http = inject(HttpClient);

  listForIssue(issueKey: string): Observable<Comment[]> {
    return this.http.get<Comment[]>(`${environment.apiBaseUrl}/issues/${issueKey}/comments`);
  }

  create(issueKey: string, body: string): Observable<Comment> {
    return this.http.post<Comment>(`${environment.apiBaseUrl}/issues/${issueKey}/comments`, { body });
  }

  update(commentId: string, body: string): Observable<Comment> {
    return this.http.patch<Comment>(`${environment.apiBaseUrl}/comments/${commentId}`, { body });
  }

  delete(commentId: string): Observable<void> {
    return this.http.delete<void>(`${environment.apiBaseUrl}/comments/${commentId}`);
  }
}
