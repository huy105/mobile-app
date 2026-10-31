import { z } from 'zod';
import { Order, TimeSlot } from '../data/types.js';
import { getIsoWeekday, resolveDateRange } from './dates.js';

export const filterZodShape = {
  date_from: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).optional().describe('Ngày bắt đầu (yyyy-MM-dd)'),
  date_to: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).optional().describe('Ngày kết thúc (yyyy-MM-dd)'),
  weekdays: z
    .array(z.number().int().min(1).max(7))
    .optional()
    .describe('Thứ trong tuần (1 = Thứ Hai, 7 = Chủ nhật)'),
  daypart: z.enum(['lunch', 'evening']).optional().describe('Buổi: lunch (trước 14:00) hoặc evening (từ 14:00)'),
  slots: z.array(z.string()).optional().describe('Danh sách nhãn khung giờ, ví dụ ["11:30 - 11:45"]'),
  categories: z.array(z.string()).optional().describe('Danh sách nhóm món, ví dụ ["Đồ uống", "Cơm"]'),
  dish_keys: z.array(z.string()).optional().describe('Danh sách mã món dishKey (ví dụ ["tra-chanh", "com-suon-nuong"])'),
};

export interface CommonFilterInput {
  date_from?: string;
  date_to?: string;
  weekdays?: number[];
  daypart?: 'lunch' | 'evening';
  slots?: string[];
  categories?: string[];
  dish_keys?: string[];
}

export interface AppliedFilters {
  range: { from: string; to: string };
  weekdays?: number[];
  daypart?: 'lunch' | 'evening';
  slots?: string[];
  categories?: string[];
  dish_keys?: string[];
}

export function parseStartMinute(slotId?: string, slotLabel?: string, timeSlots: TimeSlot[] = []): number {
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

export interface FilteredOrderContext {
  orders: Order[];
  appliedFilters: AppliedFilters;
  hasLineItemFilter: boolean;
}

export function applyOrderFilters(
  orders: Order[],
  filters: CommonFilterInput,
  timeSlots: TimeSlot[] = [],
  tz?: string
): FilteredOrderContext {
  const range = resolveDateRange(filters.date_from, filters.date_to, tz);

  const appliedFilters: AppliedFilters = { range };
  if (filters.weekdays && filters.weekdays.length > 0) {
    // Validate weekdays 1..7
    for (const w of filters.weekdays) {
      if (w < 1 || w > 7) {
        throw new Error(`Giá trị weekdays không hợp lệ: ${w}. Chỉ chấp nhận từ 1 (Thứ Hai) đến 7 (Chủ nhật).`);
      }
    }
    appliedFilters.weekdays = filters.weekdays;
  }
  if (filters.daypart) appliedFilters.daypart = filters.daypart;
  if (filters.slots && filters.slots.length > 0) appliedFilters.slots = filters.slots;
  if (filters.categories && filters.categories.length > 0) appliedFilters.categories = filters.categories;
  if (filters.dish_keys && filters.dish_keys.length > 0) appliedFilters.dish_keys = filters.dish_keys;

  const hasLineItemFilter = Boolean(
    (appliedFilters.categories && appliedFilters.categories.length > 0) ||
      (appliedFilters.dish_keys && appliedFilters.dish_keys.length > 0)
  );

  const filteredOrders: Order[] = [];

  for (const o of orders) {
    // 1. Lọc khoảng ngày
    if (o.date < range.from || o.date > range.to) continue;

    // 2. Lọc weekdays
    if (appliedFilters.weekdays) {
      const weekday = getIsoWeekday(o.date);
      if (!appliedFilters.weekdays.includes(weekday)) continue;
    }

    // 3. Lọc slots
    if (appliedFilters.slots && appliedFilters.slots.length > 0) {
      if (!appliedFilters.slots.includes(o.slotLabel) && !appliedFilters.slots.includes(o.slotId)) {
        continue;
      }
    }

    // 4. Lọc daypart
    if (appliedFilters.daypart) {
      const startMin = parseStartMinute(o.slotId, o.slotLabel, timeSlots);
      const isLunch = startMin < 840;
      if (appliedFilters.daypart === 'lunch' && !isLunch) continue;
      if (appliedFilters.daypart === 'evening' && isLunch) continue;
    }

    // 5. Lọc theo dòng món nếu có categories hoặc dish_keys
    if (hasLineItemFilter) {
      const matchingItems = o.items.filter((item) => {
        let catMatch = true;
        let dishMatch = true;

        if (appliedFilters.categories && appliedFilters.categories.length > 0) {
          catMatch = appliedFilters.categories.includes(item.category || '');
        }
        if (appliedFilters.dish_keys && appliedFilters.dish_keys.length > 0) {
          const itemKey = item.dishKey || item.name;
          dishMatch = appliedFilters.dish_keys.includes(itemKey) || appliedFilters.dish_keys.includes(item.name);
        }

        return catMatch && dishMatch;
      });

      if (matchingItems.length === 0) {
        continue;
      }

      // Tạo đơn bản sao với chỉ các dòng món khớp
      filteredOrders.push({
        ...o,
        items: matchingItems,
      });
    } else {
      filteredOrders.push(o);
    }
  }

  return {
    orders: filteredOrders,
    appliedFilters,
    hasLineItemFilter,
  };
}
