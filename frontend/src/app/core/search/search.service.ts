import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Issue } from '../issue/models';
import { CreateSavedFilterRequest, IssueSearchRequest, SavedFilter } from './models';

@Injectable({ providedIn: 'root' })
export class SearchService {
  private readonly http = inject(HttpClient);

  search(projectKey: string, request: IssueSearchRequest): Observable<Issue[]> {
    return this.http.post<Issue[]>(
      `${environment.apiBaseUrl}/projects/${projectKey}/search`,
      request,
    );
  }

  listSavedFilters(projectKey: string): Observable<SavedFilter[]> {
    return this.http.get<SavedFilter[]>(
      `${environment.apiBaseUrl}/projects/${projectKey}/filters`,
    );
  }

  createSavedFilter(
    projectKey: string,
    request: CreateSavedFilterRequest,
  ): Observable<SavedFilter> {
    return this.http.post<SavedFilter>(
      `${environment.apiBaseUrl}/projects/${projectKey}/filters`,
      request,
    );
  }

  deleteSavedFilter(projectKey: string, filterId: string): Observable<void> {
    return this.http.delete<void>(
      `${environment.apiBaseUrl}/projects/${projectKey}/filters/${filterId}`,
    );
  }
}
