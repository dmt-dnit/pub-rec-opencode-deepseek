import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Order, OrderLineItemRequest } from '../models/user.model';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class OrderService {
  private apiBase = `${environment.ordersApiBase}/api/orders`;

  constructor(private http: HttpClient) {}

  placeOrder(items: OrderLineItemRequest[]): Observable<Order> {
    return this.http.post<Order>(this.apiBase, { items });
  }

  listOrders(): Observable<Order[]> {
    return this.http.get<Order[]>(this.apiBase);
  }
}
