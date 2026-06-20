import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Product } from '../models/user.model';

@Injectable({ providedIn: 'root' })
export class InventoryService {
  private apiBase = '/api/inventory';

  constructor(private http: HttpClient) {}

  listProducts(): Observable<Product[]> {
    return this.http.get<Product[]>(this.apiBase);
  }
}
