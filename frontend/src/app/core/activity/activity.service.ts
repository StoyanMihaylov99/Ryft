import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ActivityEvent } from './models';

@Injectable({ providedIn: 'root' })
export class ActivityService {
  private readonly http = inject(HttpClient);

  listForIssue(issueKey: string): Observable<ActivityEvent[]> {
    return this.http.get<ActivityEvent[]>(`${environment.apiBaseUrl}/issues/${issueKey}/activity`);
  }
}
