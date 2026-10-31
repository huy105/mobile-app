import { describe, it, expect, beforeAll, vi } from 'vitest';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import fs from 'node:fs/promises';

import { createServer } from '../src/index.js';
import { FixtureSource } from '../src/data/fixtureSource.js';
import { DatabaseCache } from '../src/sql/cache.js';
import { computeForecast } from '../src/metrics/forecast.js';
import { computeSlowDays } from '../src/metrics/slowDays.js';
import { MenuItem, Order } from '../src/data/types.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const fixturePath = path.resolve(__dirname, '../../../docs/data/seed-history.json');
const seedMenuPath = path.resolve(__dirname, '../../../docs/data/seed-menu.json');

describe('The 4 Core MCP Tools Integration Test', () => {
  let server: ReturnType<typeof createServer>;
  let source: FixtureSource;
  let minDate: string;
  let maxDate: string;

  beforeAll(async () => {
    source = new FixtureSource(fixturePath, seedMenuPath);
    server = createServer(source);

    const content = await fs.readFile(fixturePath, 'utf-8');
    const raw = JSON.parse(content);
    const dates = raw.orders.map((o: any) => o.date).sort();
    minDate = dates[0];
    maxDate = dates[dates.length - 1];
  });

  async function callTool(name: string, args: Record<string, unknown> = {}) {
    const tool = (server as any)._registeredTools[name];
    expect(tool).toBeDefined();
    const result = await tool.handler(args, {});
    return result;
  }

  it('Verifies exactly 4 tools registered in McpServer', () => {
    const tools = Object.keys((server as any)._registeredTools);
    expect(tools.length).toBe(4);
    expect(tools).toContain('get_schema');
    expect(tools).toContain('run_sql');
    expect(tools).toContain('forecast_tomorrow');
    expect(tools).toContain('find_slow_days');
  });

  describe('Tool 1: get_schema', () => {
    it('Returns schema documentation with tables, views, sample queries and no customer names', async () => {
      const res = await callTool('get_schema', {});
      expect(res.isError).toBeFalsy();
      const text = res.content[0].text;

      // Table names
      expect(text).toContain('orders');
      expect(text).toContain('order_items');
      expect(text).toContain('menu_items');
      expect(text).toContain('time_slots');

      // View names
      expect(text).toContain('v_completed_orders');
      expect(text).toContain('v_completed_items');
      expect(text).toContain('v_cancelled_orders');
      expect(text).toContain('v_daily');
      expect(text).toContain('v_item_daily');
      expect(text).toContain('v_slot_daily');
      expect(text).toContain('v_item_sellthrough');

      // Notes & sample queries
      expect(text).toContain('SELECT');
      expect(text).toContain('Hôm nay');

      // Privacy: no student names or userIds
      expect(text).not.toContain('demo_student_');
      expect(text).not.toContain('Minh Anh');
      expect(text).not.toContain('Tuấn Kiệt');
    });
  });

  describe('Tool 2: run_sql', () => {
    it('Executes valid SQL SELECT on v_daily within date range', async () => {
      const res = await callTool('run_sql', {
        sql: 'SELECT date, revenue, orders FROM v_daily ORDER BY date DESC LIMIT 3;',
        date_from: minDate,
        date_to: maxDate,
      });

      expect(res.isError).toBeFalsy();
      const content = res.structuredContent;
      expect(content.columns).toEqual(['date', 'revenue', 'orders']);
      expect(content.rows.length).toBe(3);
      expect(content.rowCount).toBe(3);
      expect(content.loadedRange.from).toBe(minDate);
      expect(content.loadedRange.to).toBe(maxDate);
      expect(content.source).toBeDefined();
    });

    it('Rejects non-SELECT or multiple queries with clear Vietnamese error', async () => {
      const res1 = await callTool('run_sql', {
        sql: 'DROP TABLE orders;',
      });
      expect(res1.isError).toBe(true);
      expect(res1.content[0].text).toContain('SELECT hoặc WITH');

      const res2 = await callTool('run_sql', {
        sql: 'SELECT 1; SELECT 2;',
      });
      expect(res2.isError).toBe(true);
      expect(res2.content[0].text).toContain('một câu lệnh SQL duy nhất');

      const res3 = await callTool('run_sql', {
        sql: 'SELECT * FROM orders WHERE total > 0 AND PRAGMA query_only = 1;',
      });
      expect(res3.isError).toBe(true);
      expect(res3.content[0].text).toContain('không được phép');
    });

    it('Returns note when rowCount = 0', async () => {
      const res = await callTool('run_sql', {
        sql: "SELECT * FROM orders WHERE status = 'NON_EXISTENT';",
        date_from: minDate,
        date_to: maxDate,
      });

      expect(res.isError).toBeFalsy();
      expect(res.structuredContent.rowCount).toBe(0);
      expect(res.structuredContent.note).toContain('Không có dòng dữ liệu nào');
    });
  });

  describe('Tool 3: forecast_tomorrow', () => {
    it('Forecasts items with same-weekday average and buffer percentage', async () => {
      // In fixture data, maxDate is 2026-10-30 (Friday).
      // If we forecast for 2026-10-31 (Saturday) or a date with history
      const res = await callTool('forecast_tomorrow', {
        date: maxDate,
        buffer_pct: 10,
      });

      expect(res.isError).toBeFalsy();
      const content = res.structuredContent;
      expect(content.forDate).toBe(maxDate);
      expect(content.items).toBeInstanceOf(Array);
      expect(content.items.length).toBeGreaterThan(0);

      // Verify each item structure
      for (const item of content.items) {
        expect(item).toHaveProperty('dishKey');
        expect(item).toHaveProperty('name');
        expect(item).toHaveProperty('weeksOfData');
        expect(item).toHaveProperty('currentQuota');
      }
    });

    it('Returns forecast: null and note when history has < 2 weeks of data', () => {
      const fakeMenuItems: MenuItem[] = [
        {
          id: 'm1',
          name: 'Cơm tấm',
          description: '',
          price: 35000,
          imageUrl: '',
          category: 'Cơm',
          inStock: true,
          date: '2026-10-23', // Friday
          dishKey: 'com_tam',
          soldQty: 40,
        },
      ];

      const res = computeForecast(fakeMenuItems, '2026-10-30', 10);
      const item = res.items.find((i) => i.dishKey === 'com_tam');
      expect(item).toBeDefined();
      expect(item!.forecast).toBeNull();
      expect(item!.note).toContain('chưa đủ dữ liệu');
      expect(item!.weeksOfData).toBe(1);
    });

    it('Adds warning if forecast exceeds currentQuota', () => {
      const fakeMenuItems: MenuItem[] = [
        {
          id: 'm1',
          name: 'Món đặc biệt',
          description: '',
          price: 50000,
          imageUrl: '',
          category: 'Món nước',
          inStock: true,
          date: '2026-10-09',
          dishKey: 'special',
          soldQty: 100,
          dailyQuota: 50,
        },
        {
          id: 'm2',
          name: 'Món đặc biệt',
          description: '',
          price: 50000,
          imageUrl: '',
          category: 'Món nước',
          inStock: true,
          date: '2026-10-16',
          dishKey: 'special',
          soldQty: 100,
          dailyQuota: 50,
        },
        {
          id: 'm3',
          name: 'Món đặc biệt',
          description: '',
          price: 50000,
          imageUrl: '',
          category: 'Món nước',
          inStock: true,
          date: '2026-10-23',
          dishKey: 'special',
          soldQty: 100,
          dailyQuota: 50,
        },
      ];

      const res = computeForecast(fakeMenuItems, '2026-10-30', 10);
      const item = res.items.find((i) => i.dishKey === 'special');
      expect(item).toBeDefined();
      expect(item!.forecast).toBe(110);
      expect(item!.warning).toContain('vượt hạn mức hiện tại');
    });
  });

  describe('Tool 4: find_slow_days', () => {
    it('Finds anomalous slow kitchen prep days using mean + 1.5 * stdDev', async () => {
      const res = await callTool('find_slow_days', {
        date_from: minDate,
        date_to: maxDate,
      });

      expect(res.isError).toBeFalsy();
      const content = res.structuredContent;
      expect(content.avgPrepOverall).toBeGreaterThan(0);
      expect(content.stdDev).toBeGreaterThan(0);
      expect(content.daysWithData).toBeGreaterThanOrEqual(5);
      expect(content.slowDays).toBeInstanceOf(Array);

      // In seed-history, at least one day is engineered to be slow
      expect(content.slowDays.length).toBeGreaterThan(0);
      for (const d of content.slowDays) {
        expect(d.avgPrep).toBeGreaterThan(content.avgPrepOverall);
      }
    });

    it('Returns slowDays: [] with note if < 5 days of data', () => {
      const fakeOrders: Order[] = [
        {
          id: 'o1',
          userId: 'u1',
          userName: 'U1',
          items: [],
          slotId: 'slot1',
          slotLabel: '11:15',
          pickupCode: '1234',
          status: 'COMPLETED',
          total: 30000,
          date: '2026-10-01',
          createdAt: new Date('2026-10-01T11:00:00Z'),
          preparingAt: new Date('2026-10-01T11:05:00Z'),
          readyAt: new Date('2026-10-01T11:20:00Z'),
        },
      ];

      const res = computeSlowDays(fakeOrders, { from: '2026-10-01', to: '2026-10-01' });
      expect(res.daysWithData).toBe(1);
      expect(res.slowDays).toEqual([]);
      expect(res.note).toContain('ít nhất 5 ngày');
    });
  });

  describe('5. DatabaseCache Behavior', () => {
    it('Reuses database for identical date range within TTL', async () => {
      const cache = new DatabaseCache(60);
      const range = { from: minDate, to: maxDate };

      let callCount = 0;
      const mockSource = {
        getOrders: vi.fn(async (r) => {
          callCount++;
          return source.getOrders(r);
        }),
        getMenuItems: vi.fn(async (r) => source.getMenuItems(r)),
        getTimeSlots: vi.fn(async () => source.getTimeSlots()),
      };

      const cfg: any = { timezone: 'Asia/Ho_Chi_Minh', sqlCacheSeconds: 60 };

      // Call 1
      await cache.getDatabase(mockSource, range, cfg);
      expect(callCount).toBe(1);

      // Call 2 with same range
      await cache.getDatabase(mockSource, range, cfg);
      expect(callCount).toBe(1); // Cached! No additional load

      // Call 3 with different range
      const diffRange = { from: minDate, to: minDate };
      await cache.getDatabase(mockSource, diffRange, cfg);
      expect(callCount).toBe(2); // Reloaded for new range
    });

    it('Bypasses cache when sqlCacheSeconds is 0', async () => {
      const cache = new DatabaseCache(0);
      const range = { from: minDate, to: maxDate };

      let callCount = 0;
      const mockSource = {
        getOrders: vi.fn(async (r) => {
          callCount++;
          return source.getOrders(r);
        }),
        getMenuItems: vi.fn(async (r) => source.getMenuItems(r)),
        getTimeSlots: vi.fn(async () => source.getTimeSlots()),
      };

      const cfg: any = { timezone: 'Asia/Ho_Chi_Minh', sqlCacheSeconds: 0 };

      await cache.getDatabase(mockSource, range, cfg);
      await cache.getDatabase(mockSource, range, cfg);
      expect(callCount).toBe(2); // Always reloads
    });
  });
});
