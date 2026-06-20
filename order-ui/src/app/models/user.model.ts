export interface UserInfo {
  email: string;
  name: string;
  role: string;
}

export interface LoginResponse {
  token: string;
  email: string;
  name: string;
  role: string;
}

export interface OrderLineItemRequest { sku: string; quantity: number; }
export type OrderStatus = 'PENDING' | 'CONFIRMED' | 'REJECTED';
export interface Order {
  orderId: string;
  customerEmail: string;
  items: { sku: string; quantity: number }[];
  totalAmount: number;
  status: OrderStatus;
  placedAt: string;
  updatedAt: string;
}
