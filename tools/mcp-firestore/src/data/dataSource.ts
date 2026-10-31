import { DateRange, Order, MenuItem, TimeSlot } from './types.js';

export interface DataSource {
  getOrders(range: DateRange): Promise<Order[]>;
  getMenuItems(range: DateRange): Promise<MenuItem[]>;
  getTimeSlots(): Promise<TimeSlot[]>;
}
