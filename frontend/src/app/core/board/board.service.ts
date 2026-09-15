import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Board } from './models';

@Injectable({ providedIn: 'root' })
export class BoardService {
  private readonly http = inject(HttpClient);

  get(projectKey: string): Observable<Board> {
    return this.http.get<Board>(`${environment.apiBaseUrl}/projects/${projectKey}/board`);
  }
}
