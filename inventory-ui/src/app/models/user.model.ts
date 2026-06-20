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

export interface Product {
  sku: string;
  name: string;
  quantityOnHand: number;
}

export type ReservationStatus = 'RESERVED' | 'REJECTED';

export interface InventoryReservation {
  orderId: string;
  status: ReservationStatus;
  reason: string | null;
  processedAt: string;
}
