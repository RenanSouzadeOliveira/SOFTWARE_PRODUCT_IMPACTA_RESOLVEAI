import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';

export interface ApiRequestOptions {
  readonly params?: HttpParams | Record<string, string | number | boolean>;
}

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiUrl.replace(/\/$/, '');

  get<TResponse>(path: string, options?: ApiRequestOptions): Observable<TResponse> {
    return this.http.get<TResponse>(this.resolveUrl(path), options);
  }

  post<TResponse, TBody>(path: string, body: TBody): Observable<TResponse> {
    return this.http.post<TResponse>(this.resolveUrl(path), body);
  }

  patch<TResponse, TBody>(path: string, body: TBody): Observable<TResponse> {
    return this.http.patch<TResponse>(this.resolveUrl(path), body);
  }

  private resolveUrl(path: string): string {
    const normalizedPath = path.replace(/^\//, '');
    return `${this.baseUrl}/${normalizedPath}`;
  }
}
