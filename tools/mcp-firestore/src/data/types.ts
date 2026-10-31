export type OrderStatus = 'RECEIVED' | 'PREPARING' | 'READY' | 'COMPLETED' | 'CANCELLED';

export type CancelledBy = 'STUDENT' | 'CANTEEN';

export type CancelReason = 'SOLD_OUT' | 'OVERLOAD' | 'NO_SHOW' | 'CHANGED_MIND' | 'OTHER';

export interface OrderItem {
  menuItemId: string;
  name: string;
  price: number;
  quantity: number;
  category?: string;
  dishKey?: string;
}

export interface Order {
  id: string;
  userId: string;
  userName: string;
  items: OrderItem[];
  slotId: string;
  slotLabel: string;
  pickupCode: string;
  status: OrderStatus;
  total: number;
  date: string; // yyyy-MM-dd (giờ VN)
  createdAt: Date;
  updatedAt?: Date;
  preparingAt?: Date;
  readyAt?: Date;
  completedAt?: Date;
  cancelledAt?: Date;
  cancelledBy?: CancelledBy;
  cancelReason?: CancelReason;
}

export interface MenuItem {
  id: string;
  name: string;
  description: string;
  price: number;
  imageUrl: string;
  category: string;
  inStock: boolean;
  date: string;
  dishKey?: string;
  tags?: string[];
  dailyQuota?: number; // 0 hoặc thiếu = không giới hạn
  soldQty?: number;
}

export interface TimeSlot {
  id: string;
  label: string;
  startMinute: number;
  endMinute: number;
  maxOrders: number;
}

export interface DateRange {
  from: string; // yyyy-MM-dd
  to: string;   // yyyy-MM-dd, gồm cả hai đầu
}
