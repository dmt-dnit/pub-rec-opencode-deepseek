import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ArticleEvent } from '../models/user.model';

@Injectable({ providedIn: 'root' })
export class ArticleService {
  private apiBase = '/api/articles';

  constructor(private http: HttpClient) {}

  publish(event: Partial<ArticleEvent>): Observable<ArticleEvent> {
    return this.http.post<ArticleEvent>(`${this.apiBase}/publish`, event);
  }
}
