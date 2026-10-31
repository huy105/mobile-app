import initSqlJs, { Database, SqlJsStatic } from 'sql.js';
import { DataSource } from '../data/dataSource.js';
import { DateRange, Order, MenuItem, TimeSlot } from '../data/types.js';
import { SCHEMA_SQL } from './schema.js';
import {
  formatDateTimeInTz,
  getIsoWeekday,
  getMondayOfWeek,
} from '../util/dates.js';
import { maskUserId } from '../util/privacy.js';

function parseStartMinute(
  slotId?: string,
  slotLabel?: string,
  timeSlots: TimeSlot[] = []
): number {
  if (slotId) {
    const ts = timeSlots.find((s) => s.id === slotId);
    if (ts) return ts.startMinute;
  }
  if (slotLabel) {
    const ts = timeSlots.find((s) => s.label === slotLabel);
    if (ts) return ts.startMinute;
    const match = slotLabel.match(/^(\d{1,2}):(\d{2})/);
    if (match) {
      return parseInt(match[1], 10) * 60 + parseInt(match[2], 10);
    }
  }
  return 0;
}

let SQLInstance: SqlJsStatic | null = null;

export async function getSqlJs(): Promise<SqlJsStatic> {
  if (!SQLInstance) {
    SQLInstance = await initSqlJs();
  }
  return SQLInstance;
}

export async function loadDatabase(
  dataSource: DataSource,
  range: DateRange,
  tz: string = 'Asia/Ho_Chi_Minh'
): Promise<{ db: Database; range: DateRange; ordersCount: number; itemsCount: number }> {
  const SQL = await getSqlJs();
  const db = new SQL.Database();

  // 1. Tạo bảng và view
  db.run(SCHEMA_SQL);

  // 2. Lấy dữ liệu nguồn
  const [orders, menuItems, timeSlots] = await Promise.all([
    dataSource.getOrders(range),
    dataSource.getMenuItems(range),
    dataSource.getTimeSlots(),
  ]);

  db.run('BEGIN TRANSACTION;');

  let itemsCount = 0;

  try {
    // 3. Nạp time_slots
    const insertSlotStmt = db.prepare(
      'INSERT INTO time_slots (slot_id, label, start_minute, end_minute, max_orders, daypart) VALUES (?, ?, ?, ?, ?, ?);'
    );
    for (const s of timeSlots) {
      const daypart = s.startMinute < 840 ? 'lunch' : 'evening';
      insertSlotStmt.run([s.id, s.label, s.startMinute, s.endMinute, s.maxOrders, daypart]);
    }
    insertSlotStmt.free();

    // Lập bản đồ tra cứu giá vốn của món theo ngày
    const menuCostMap = new Map<string, number>();
    for (const m of menuItems) {
      const cost = m.costPrice || 0;
      menuCostMap.set(`${m.date}|${m.id}`, cost);
      const dishKey = m.dishKey || m.name;
      menuCostMap.set(`${m.date}|${dishKey}`, cost);
      menuCostMap.set(`${m.date}|${m.name}`, cost);
    }

    // 4. Nạp orders và order_items
    const insertOrderStmt = db.prepare(`
      INSERT INTO orders (
        order_id, date, weekday, week_start, slot_id, slot_label, daypart, status, total,
        subtotal, discount_amount, voucher_code, points_used,
        customer, created_at, preparing_at, ready_at, completed_at, cancelled_at,
        cancelled_by, cancel_reason, prep_minutes, wait_minutes, pickup_delay_minutes
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
    `);

    const insertItemStmt = db.prepare(`
      INSERT INTO order_items (
        order_id, date, weekday, week_start, slot_label, daypart, status,
        dish_key, name, category, price, quantity, line_revenue, cost_price
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
    `);

    for (const o of orders) {
      const weekday = getIsoWeekday(o.date);
      const weekStart = getMondayOfWeek(o.date);
      const startMin = parseStartMinute(o.slotId, o.slotLabel, timeSlots);
      const daypart = startMin < 840 ? 'lunch' : 'evening';
      const customer = maskUserId(o.userId);

      const subtotal = o.subtotal !== undefined && o.subtotal > 0 ? o.subtotal : o.total;
      const discountAmount = o.discountAmount ?? 0;
      const voucherCode = o.voucherCode || null;
      const pointsUsed = o.pointsUsed ?? 0;

      const createdAtStr = formatDateTimeInTz(o.createdAt, tz);
      const preparingAtStr = o.preparingAt ? formatDateTimeInTz(o.preparingAt, tz) : null;
      const readyAtStr = o.readyAt ? formatDateTimeInTz(o.readyAt, tz) : null;
      const completedAtStr = o.completedAt ? formatDateTimeInTz(o.completedAt, tz) : null;
      const cancelledAtStr = o.cancelledAt ? formatDateTimeInTz(o.cancelledAt, tz) : null;

      let prepMinutes: number | null = null;
      if (o.readyAt && o.preparingAt) {
        const diff = (o.readyAt.getTime() - o.preparingAt.getTime()) / 60000;
        if (diff >= 0) prepMinutes = Math.round(diff * 10) / 10;
      }

      let waitMinutes: number | null = null;
      if (o.preparingAt && o.createdAt) {
        const diff = (o.preparingAt.getTime() - o.createdAt.getTime()) / 60000;
        if (diff >= 0) waitMinutes = Math.round(diff * 10) / 10;
      }

      let pickupDelayMinutes: number | null = null;
      if (o.completedAt && o.readyAt) {
        const diff = (o.completedAt.getTime() - o.readyAt.getTime()) / 60000;
        if (diff >= 0) pickupDelayMinutes = Math.round(diff * 10) / 10;
      }

      insertOrderStmt.run([
        o.id,
        o.date,
        weekday,
        weekStart,
        o.slotId || null,
        o.slotLabel || null,
        daypart,
        o.status,
        o.total,
        subtotal,
        discountAmount,
        voucherCode,
        pointsUsed,
        customer,
        createdAtStr,
        preparingAtStr,
        readyAtStr,
        completedAtStr,
        cancelledAtStr,
        o.cancelledBy || null,
        o.cancelReason || null,
        prepMinutes,
        waitMinutes,
        pickupDelayMinutes,
      ]);

      // Nạp các dòng món
      for (const item of o.items) {
        itemsCount++;
        const dishKey = item.dishKey || item.name;
        const lineRev = item.price * item.quantity;
        const itemCost =
          menuCostMap.get(`${o.date}|${item.menuItemId}`) ??
          menuCostMap.get(`${o.date}|${dishKey}`) ??
          menuCostMap.get(`${o.date}|${item.name}`) ??
          0;

        insertItemStmt.run([
          o.id,
          o.date,
          weekday,
          weekStart,
          o.slotLabel || null,
          daypart,
          o.status,
          dishKey,
          item.name,
          item.category || 'Không rõ',
          item.price,
          item.quantity,
          lineRev,
          itemCost,
        ]);
      }
    }

    insertOrderStmt.free();
    insertItemStmt.free();

    // 5. Nạp menu_items
    const insertMenuStmt = db.prepare(`
      INSERT INTO menu_items (
        menu_item_id, date, weekday, dish_key, name, category,
        price, cost_price, tags, daily_quota, sold_qty, remaining_qty, in_stock, available
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
    `);

    for (const m of menuItems) {
      const weekday = getIsoWeekday(m.date);
      const dishKey = m.dishKey || m.name;
      const tagsStr = m.tags && m.tags.length > 0 ? `,${m.tags.join(',')},` : null;
      const dailyQuota = m.dailyQuota || 0;
      const soldQty = m.soldQty || 0;
      const remainingQty = dailyQuota > 0 ? Math.max(0, dailyQuota - soldQty) : null;
      const inStock = m.inStock ? 1 : 0;
      const available = inStock === 1 && (remainingQty === null || remainingQty > 0) ? 1 : 0;

      insertMenuStmt.run([
        m.id,
        m.date,
        weekday,
        dishKey,
        m.name,
        m.category || 'Không rõ',
        m.price,
        m.costPrice || 0,
        tagsStr,
        dailyQuota,
        soldQty,
        remainingQty,
        inStock,
        available,
      ]);
    }

    insertMenuStmt.free();

    db.run('COMMIT;');
  } catch (err) {
    db.run('ROLLBACK;');
    db.close();
    throw err;
  }

  return {
    db,
    range,
    ordersCount: orders.length,
    itemsCount,
  };
}
