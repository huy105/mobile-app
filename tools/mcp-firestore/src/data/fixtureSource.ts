import fs from 'node:fs/promises';
import path from 'node:path';
import { DataSource } from './dataSource.js';
import { DateRange, MenuItem, Order, TimeSlot } from './types.js';

interface RawHistoryFile {
  menuItems: Array<{
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
    dailyQuota?: number;
    soldQty?: number;
    costPrice?: number;
  }>;
  orders: Array<{
    id: string;
    userId: string;
    userName: string;
    items: Array<{
      menuItemId: string;
      name: string;
      price: number;
      quantity: number;
      category?: string;
      dishKey?: string;
    }>;
    slotId: string;
    slotLabel: string;
    pickupCode: string;
    status: Order['status'];
    total: number;
    subtotal?: number;
    discountAmount?: number;
    voucherCode?: string;
    pointsUsed?: number;
    date: string;
    createdAt: string;
    updatedAt?: string;
    preparingAt?: string;
    readyAt?: string;
    completedAt?: string;
    cancelledAt?: string;
    cancelledBy?: Order['cancelledBy'];
    cancelReason?: Order['cancelReason'];
  }>;
  timeSlotCounters?: Record<string, unknown>;
}

interface RawSeedMenuFile {
  timeSlots: Array<{
    label: string;
    startMinute: number;
    endMinute: number;
    maxOrders: number;
  }>;
}

export class FixtureSource implements DataSource {
  private fixturePath: string;
  private seedMenuPath?: string;
  private cachedOrders?: Order[];
  private cachedMenuItems?: MenuItem[];
  private cachedTimeSlots?: TimeSlot[];

  constructor(fixturePath: string, seedMenuPath?: string) {
    this.fixturePath = path.resolve(fixturePath);
    if (seedMenuPath) {
      this.seedMenuPath = path.resolve(seedMenuPath);
    } else {
      this.seedMenuPath = path.join(path.dirname(this.fixturePath), 'seed-menu.json');
    }
  }

  private async loadHistory(): Promise<{ orders: Order[]; menuItems: MenuItem[] }> {
    if (this.cachedOrders && this.cachedMenuItems) {
      return { orders: this.cachedOrders, menuItems: this.cachedMenuItems };
    }

    const content = await fs.readFile(this.fixturePath, 'utf-8');
    const raw: RawHistoryFile = JSON.parse(content);

    const menuItems: MenuItem[] = (raw.menuItems || []).map((m) => ({
      id: m.id,
      name: m.name,
      description: m.description,
      price: m.price,
      imageUrl: m.imageUrl,
      category: m.category,
      inStock: Boolean(m.inStock),
      date: m.date,
      dishKey: m.dishKey,
      tags: m.tags,
      dailyQuota: m.dailyQuota,
      soldQty: m.soldQty,
      costPrice: m.costPrice ?? 0,
    }));

    const orders: Order[] = (raw.orders || []).map((o) => ({
      id: o.id,
      userId: o.userId,
      userName: o.userName,
      items: (o.items || []).map((i) => ({
        menuItemId: i.menuItemId,
        name: i.name,
        price: i.price,
        quantity: i.quantity,
        category: i.category,
        dishKey: i.dishKey,
      })),
      slotId: o.slotId,
      slotLabel: o.slotLabel,
      pickupCode: o.pickupCode,
      status: o.status,
      total: o.total,
      subtotal: o.subtotal !== undefined ? o.subtotal : o.total,
      discountAmount: o.discountAmount ?? 0,
      voucherCode: o.voucherCode || undefined,
      pointsUsed: o.pointsUsed ?? 0,
      date: o.date,
      createdAt: new Date(o.createdAt),
      updatedAt: o.updatedAt ? new Date(o.updatedAt) : undefined,
      preparingAt: o.preparingAt ? new Date(o.preparingAt) : undefined,
      readyAt: o.readyAt ? new Date(o.readyAt) : undefined,
      completedAt: o.completedAt ? new Date(o.completedAt) : undefined,
      cancelledAt: o.cancelledAt ? new Date(o.cancelledAt) : undefined,
      cancelledBy: o.cancelledBy,
      cancelReason: o.cancelReason,
    }));

    this.cachedOrders = orders;
    this.cachedMenuItems = menuItems;

    return { orders, menuItems };
  }

  async getOrders(range: DateRange): Promise<Order[]> {
    const { orders } = await this.loadHistory();
    return orders.filter((o) => o.date >= range.from && o.date <= range.to);
  }

  async getMenuItems(range: DateRange): Promise<MenuItem[]> {
    const { menuItems } = await this.loadHistory();
    return menuItems.filter((m) => m.date >= range.from && m.date <= range.to);
  }

  async getTimeSlots(): Promise<TimeSlot[]> {
    if (this.cachedTimeSlots) {
      return this.cachedTimeSlots;
    }

    const menuPath = this.seedMenuPath || path.join(path.dirname(this.fixturePath), 'seed-menu.json');
    const content = await fs.readFile(menuPath, 'utf-8');
    const raw: RawSeedMenuFile = JSON.parse(content);

    const sortedSlots = [...(raw.timeSlots || [])].sort((a, b) => a.startMinute - b.startMinute);
    const slots: TimeSlot[] = sortedSlots.map((s, idx) => ({
      id: `slot${idx + 1}`,
      label: s.label,
      startMinute: s.startMinute,
      endMinute: s.endMinute,
      maxOrders: s.maxOrders,
    }));

    this.cachedTimeSlots = slots;
    return slots;
  }
}
