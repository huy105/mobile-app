import fs from 'node:fs';
import path from 'node:path';
import admin from 'firebase-admin';
import { DataSource } from './dataSource.js';
import { DateRange, MenuItem, Order, TimeSlot } from './types.js';

export class FirestoreSource implements DataSource {
  private db: admin.firestore.Firestore;

  constructor(serviceAccountPath?: string) {
    if (!serviceAccountPath) {
      throw new Error(
        'Thiếu cấu hình SERVICE_ACCOUNT_PATH khi chọn DATA_SOURCE=firestore. Hãy đặt DATA_SOURCE=fixture để chạy thử bằng dữ liệu mẫu hoặc cung cấp đường dẫn file serviceAccount.json.'
      );
    }

    const resolvedPath = path.resolve(serviceAccountPath);
    if (!fs.existsSync(resolvedPath)) {
      throw new Error(
        `Không tìm thấy file service account tại "${resolvedPath}". Hãy kiểm tra lại đường dẫn hoặc đặt DATA_SOURCE=fixture để chạy thử bằng dữ liệu mẫu.`
      );
    }

    let serviceAccount: admin.ServiceAccount;
    try {
      const fileContent = fs.readFileSync(resolvedPath, 'utf-8');
      serviceAccount = JSON.parse(fileContent);
    } catch {
      throw new Error(
        `Không thể phân tích file service account tại "${resolvedPath}". Hãy kiểm tra lại định dạng JSON hoặc đặt DATA_SOURCE=fixture để chạy thử bằng dữ liệu mẫu.`
      );
    }

    if (!admin.apps.length) {
      admin.initializeApp({
        credential: admin.credential.cert(serviceAccount),
      });
    }

    this.db = admin.firestore();
  }

  async getOrders(range: DateRange): Promise<Order[]> {
    // Chỉ truy vấn trên trường date, không cần composite index
    const snapshot = await this.db
      .collection('orders')
      .where('date', '>=', range.from)
      .where('date', '<=', range.to)
      .get();

    return snapshot.docs.map((doc) => {
      const d = doc.data();
      return {
        id: doc.id,
        userId: d.userId,
        userName: d.userName,
        items: (d.items || []).map((i: any) => ({
          menuItemId: i.menuItemId,
          name: i.name,
          price: i.price,
          quantity: i.quantity,
          category: i.category,
          dishKey: i.dishKey,
        })),
        slotId: d.slotId,
        slotLabel: d.slotLabel,
        pickupCode: d.pickupCode,
        status: d.status,
        total: d.total,
        date: d.date,
        createdAt: d.createdAt?.toDate ? d.createdAt.toDate() : new Date(d.createdAt),
        updatedAt: d.updatedAt?.toDate
          ? d.updatedAt.toDate()
          : d.updatedAt
          ? new Date(d.updatedAt)
          : undefined,
        preparingAt: d.preparingAt?.toDate
          ? d.preparingAt.toDate()
          : d.preparingAt
          ? new Date(d.preparingAt)
          : undefined,
        readyAt: d.readyAt?.toDate
          ? d.readyAt.toDate()
          : d.readyAt
          ? new Date(d.readyAt)
          : undefined,
        completedAt: d.completedAt?.toDate
          ? d.completedAt.toDate()
          : d.completedAt
          ? new Date(d.completedAt)
          : undefined,
        cancelledAt: d.cancelledAt?.toDate
          ? d.cancelledAt.toDate()
          : d.cancelledAt
          ? new Date(d.cancelledAt)
          : undefined,
        cancelledBy: d.cancelledBy,
        cancelReason: d.cancelReason,
      };
    });
  }

  async getMenuItems(range: DateRange): Promise<MenuItem[]> {
    const snapshot = await this.db
      .collection('menuItems')
      .where('date', '>=', range.from)
      .where('date', '<=', range.to)
      .get();

    return snapshot.docs.map((doc) => {
      const d = doc.data();
      return {
        id: doc.id,
        name: d.name,
        description: d.description,
        price: d.price,
        imageUrl: d.imageUrl,
        category: d.category,
        inStock: Boolean(d.inStock),
        date: d.date,
        dishKey: d.dishKey,
        tags: d.tags,
        dailyQuota: d.dailyQuota,
        soldQty: d.soldQty,
      };
    });
  }

  async getTimeSlots(): Promise<TimeSlot[]> {
    const snapshot = await this.db.collection('timeSlots').get();

    const slots = snapshot.docs.map((doc) => {
      const d = doc.data();
      return {
        id: doc.id,
        label: d.label,
        startMinute: d.startMinute,
        endMinute: d.endMinute,
        maxOrders: d.maxOrders,
      };
    });

    slots.sort((a, b) => a.startMinute - b.startMinute);
    return slots;
  }
}
