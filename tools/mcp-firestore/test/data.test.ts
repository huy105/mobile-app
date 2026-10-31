import { describe, it, expect, beforeAll } from 'vitest';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import fs from 'node:fs/promises';
import {
  formatDateInTz,
  addDays,
  diffInDays,
  resolveDateRange,
  getPreviousPeriod,
  getWeekday,
  getMondayOfWeek,
  getToday,
} from '../src/util/dates.js';
import { FixtureSource } from '../src/data/fixtureSource.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const fixturePath = path.resolve(__dirname, '../../../docs/data/seed-history.json');
const seedMenuPath = path.resolve(__dirname, '../../../docs/data/seed-menu.json');

describe('dates utility', () => {
  it('formatDateInTz formats correctly in Asia/Ho_Chi_Minh', () => {
    const d = new Date('2026-10-31T03:00:00.000Z'); // 10:00 VN
    expect(formatDateInTz(d, 'Asia/Ho_Chi_Minh')).toBe('2026-10-31');
  });

  it('addDays correctly shifts dates across month boundaries', () => {
    expect(addDays('2026-10-31', 1)).toBe('2026-11-01');
    expect(addDays('2026-10-01', -1)).toBe('2026-09-30');
    expect(addDays('2026-02-28', 1)).toBe('2026-03-01');
  });

  it('diffInDays computes correct difference', () => {
    expect(diffInDays('2026-10-01', '2026-10-01')).toBe(0);
    expect(diffInDays('2026-10-01', '2026-10-05')).toBe(4);
    expect(diffInDays('2026-10-05', '2026-10-01')).toBe(-4);
  });

  it('resolveDateRange handles defaults and validations', () => {
    const today = getToday('Asia/Ho_Chi_Minh');
    // both undefined
    expect(resolveDateRange()).toEqual({ from: today, to: today });

    // only one supplied
    expect(resolveDateRange('2026-10-15', undefined)).toEqual({ from: '2026-10-15', to: '2026-10-15' });
    expect(resolveDateRange(undefined, '2026-10-20')).toEqual({ from: '2026-10-20', to: '2026-10-20' });

    // normal range
    expect(resolveDateRange('2026-10-01', '2026-10-07')).toEqual({ from: '2026-10-01', to: '2026-10-07' });

    // invalid: from > to
    expect(() => resolveDateRange('2026-10-10', '2026-10-05')).toThrow(/Khoảng ngày không hợp lệ/);

    // invalid: > 92 days (e.g. 93 days)
    const tooFar = addDays('2026-01-01', 93);
    expect(() => resolveDateRange('2026-01-01', tooFar)).toThrow(/vượt quá giới hạn tối đa 92 ngày/);
  });

  it('getPreviousPeriod computes equal-length immediately preceding period', () => {
    // 7 days: 2026-10-08 to 2026-10-14 (7 days inclusive)
    const range = { from: '2026-10-08', to: '2026-10-14' };
    const prev = getPreviousPeriod(range);
    expect(prev).toEqual({ from: '2026-10-01', to: '2026-10-07' });
    expect(diffInDays(prev.from, prev.to)).toBe(diffInDays(range.from, range.to));
  });

  it('weekday and Monday calculations', () => {
    // 2026-10-31 is Saturday (6)
    expect(getWeekday('2026-10-31')).toBe(6);
    // Monday of that week is 2026-10-26
    expect(getMondayOfWeek('2026-10-31')).toBe('2026-10-26');

    // 2026-10-26 is Monday (1)
    expect(getWeekday('2026-10-26')).toBe(1);
    expect(getMondayOfWeek('2026-10-26')).toBe('2026-10-26');

    // 2026-10-25 is Sunday (0)
    expect(getWeekday('2026-10-25')).toBe(0);
    // Monday of that week is 2026-10-19
    expect(getMondayOfWeek('2026-10-25')).toBe('2026-10-19');
  });
});

describe('FixtureSource', () => {
  let source: FixtureSource;
  let rawJson: { orders: Array<{ date: string; status: string; total: number }>; menuItems: Array<{ date: string }> };

  beforeAll(async () => {
    source = new FixtureSource(fixturePath, seedMenuPath);
    const content = await fs.readFile(fixturePath, 'utf-8');
    rawJson = JSON.parse(content);
  });

  it('loads timeSlots correctly matching seed-menu.json', async () => {
    const slots = await source.getTimeSlots();
    expect(slots.length).toBe(4);
    expect(slots[0]).toEqual({
      id: 'slot1',
      label: '11:15 - 11:30',
      startMinute: 675,
      endMinute: 690,
      maxOrders: 10,
    });
    expect(slots[3].id).toBe('slot4');
    expect(slots[0].startMinute).toBeLessThan(slots[1].startMinute);
  });

  it('filters orders by date range and parses timestamps into Date objects', async () => {
    const allDates = rawJson.orders.map((o) => o.date).sort();
    const minDate = allDates[0];
    const maxDate = allDates[allDates.length - 1];

    // Pick a 3-day sub-range
    const midDate1 = addDays(minDate, 5);
    const midDate2 = addDays(minDate, 7);

    const filtered = await source.getOrders({ from: midDate1, to: midDate2 });
    const expectedCount = rawJson.orders.filter((o) => o.date >= midDate1 && o.date <= midDate2).length;

    expect(filtered.length).toBe(expectedCount);
    expect(filtered.length).toBeGreaterThan(0);

    for (const order of filtered) {
      expect(order.date >= midDate1 && order.date <= midDate2).toBe(true);
      expect(order.createdAt).toBeInstanceOf(Date);
      if (order.status === 'COMPLETED') {
        expect(order.readyAt).toBeInstanceOf(Date);
      }
      if (order.status === 'CANCELLED') {
        expect(order.cancelledAt).toBeInstanceOf(Date);
      }
    }
  });

  it('filters menuItems by date range', async () => {
    const allDates = rawJson.menuItems.map((m) => m.date).sort();
    const minDate = allDates[0];
    const midDate = addDays(minDate, 3);

    const items = await source.getMenuItems({ from: minDate, to: midDate });
    const expectedCount = rawJson.menuItems.filter((m) => m.date >= minDate && m.date <= midDate).length;

    expect(items.length).toBe(expectedCount);
    expect(items.length).toBeGreaterThan(0);

    for (const item of items) {
      expect(item.date >= minDate && item.date <= midDate).toBe(true);
      expect(item.name).toBeTruthy();
      expect(item.dishKey).toBeTruthy();
    }
  });
});
