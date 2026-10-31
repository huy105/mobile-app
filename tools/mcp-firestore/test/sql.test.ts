import { describe, it, expect, beforeAll } from 'vitest';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import fs from 'node:fs/promises';
import { Database } from 'sql.js';

import { FixtureSource } from '../src/data/fixtureSource.js';
import { loadDatabase, getSqlJs } from '../src/sql/loader.js';
import { validateSql } from '../src/sql/guard.js';
import { executeQueryDirect, executeQueryInWorker } from '../src/sql/runner.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const fixturePath = path.resolve(__dirname, '../../../docs/data/seed-history.json');
const seedMenuPath = path.resolve(__dirname, '../../../docs/data/seed-menu.json');

describe('SQL Architecture & Safety Verification (Section 9)', () => {
  let source: FixtureSource;
  let rawJson: {
    orders: Array<{
      id: string;
      date: string;
      status: string;
      total: number;
      userId: string;
      userName: string;
      pickupCode: string;
      items: Array<{ quantity: number; price: number; category?: string; dishKey?: string; name: string }>;
      cancelReason?: string;
    }>;
    menuItems: Array<{
      id: string;
      date: string;
      name: string;
      dishKey?: string;
      dailyQuota?: number;
      soldQty?: number;
      price: number;
      tags?: string[];
      inStock: boolean;
    }>;
  };

  let minDate: string;
  let maxDate: string;
  let db: Database;

  beforeAll(async () => {
    source = new FixtureSource(fixturePath, seedMenuPath);
    const content = await fs.readFile(fixturePath, 'utf-8');
    rawJson = JSON.parse(content);

    const dates = rawJson.orders.map((o) => o.date).sort();
    minDate = dates[0];
    maxDate = dates[dates.length - 1];

    const loaded = await loadDatabase(source, { from: minDate, to: maxDate });
    db = loaded.db;
  });

  describe('1. Data Loading & Privacy in SQLite Tables', () => {
    it('Row counts match JSON data for orders, order_items, menu_items, time_slots', () => {
      const ordersRes = executeQueryDirect(db, 'SELECT COUNT(*) FROM orders;');
      const rawOrdersInRange = rawJson.orders.filter((o) => o.date >= minDate && o.date <= maxDate);
      expect(ordersRes.rows[0][0]).toBe(rawOrdersInRange.length);

      const itemsRes = executeQueryDirect(db, 'SELECT COUNT(*) FROM order_items;');
      const rawItemsCount = rawOrdersInRange.reduce((acc, o) => acc + o.items.length, 0);
      expect(itemsRes.rows[0][0]).toBe(rawItemsCount);

      const menuRes = executeQueryDirect(db, 'SELECT COUNT(*) FROM menu_items;');
      const rawMenuInRange = rawJson.menuItems.filter((m) => m.date >= minDate && m.date <= maxDate);
      expect(menuRes.rows[0][0]).toBe(rawMenuInRange.length);

      const slotRes = executeQueryDirect(db, 'SELECT COUNT(*) FROM time_slots;');
      expect(slotRes.rows[0][0]).toBe(8);
    });

    it('No PII columns (userName, userId, pickupCode) exist in tables', () => {
      const tables = ['orders', 'order_items', 'menu_items', 'time_slots'];
      for (const t of tables) {
        const info = executeQueryDirect(db, `PRAGMA table_info(${t});`);
        const colNames = info.rows.map((r) => r[1] as string);
        expect(colNames).not.toContain('userName');
        expect(colNames).not.toContain('user_name');
        expect(colNames).not.toContain('userId');
        expect(colNames).not.toContain('user_id');
        expect(colNames).not.toContain('pickupCode');
        expect(colNames).not.toContain('pickup_code');
      }
    });

    it('Customer column in orders is masked (khach_xxx)', () => {
      const res = executeQueryDirect(db, 'SELECT customer FROM orders LIMIT 20;');
      for (const row of res.rows) {
        const cust = row[0] as string;
        expect(cust).toMatch(/^khach_[0-9a-f]{3}$/);
        expect(cust).not.toContain('demo_student_');
      }
    });
  });

  describe('2. Views and Analytical Formula Verification', () => {
    it('v_daily.revenue equals manual sum of COMPLETED total from JSON', () => {
      const res = executeQueryDirect(db, 'SELECT SUM(revenue) FROM v_daily;');
      const actualRevenue = res.rows[0][0];

      const expectedRevenue = rawJson.orders
        .filter((o) => o.date >= minDate && o.date <= maxDate && o.status === 'COMPLETED')
        .reduce((sum, o) => sum + o.total, 0);

      expect(actualRevenue).toBe(expectedRevenue);
    });

    it('v_completed_items.line_revenue equals manual sum of price * quantity for COMPLETED', () => {
      const res = executeQueryDirect(db, 'SELECT SUM(line_revenue) FROM v_completed_items;');
      const actualLineRev = res.rows[0][0];

      const expectedLineRev = rawJson.orders
        .filter((o) => o.date >= minDate && o.date <= maxDate && o.status === 'COMPLETED')
        .reduce((sum, o) => sum + o.items.reduce((acc, i) => acc + i.price * i.quantity, 0), 0);

      expect(actualLineRev).toBe(expectedLineRev);
    });

    it('Order status counts: COMPLETED + CANCELLED equals total orders', () => {
      const res = executeQueryDirect(
        db,
        "SELECT SUM(status = 'COMPLETED'), SUM(status = 'CANCELLED'), COUNT(*) FROM orders;"
      );
      const [completed, cancelled, total] = res.rows[0];
      expect(completed + cancelled).toBe(total);
    });

    it('cancel_rate in v_daily is between 0 and 1, *_minutes >= 0 or NULL', () => {
      const dailyRes = executeQueryDirect(db, 'SELECT cancel_rate FROM v_daily WHERE cancel_rate IS NOT NULL;');
      for (const r of dailyRes.rows) {
        const rate = r[0] as number;
        expect(rate).toBeGreaterThanOrEqual(0);
        expect(rate).toBeLessThanOrEqual(1);
      }

      const minutesRes = executeQueryDirect(
        db,
        'SELECT prep_minutes, wait_minutes, pickup_delay_minutes FROM orders;'
      );
      for (const r of minutesRes.rows) {
        if (r[0] !== null) expect(r[0]).toBeGreaterThanOrEqual(0);
        if (r[1] !== null) expect(r[1]).toBeGreaterThanOrEqual(0);
        if (r[2] !== null) expect(r[2]).toBeGreaterThanOrEqual(0);
      }
    });

    it('daypart = evening only has slots with start_minute >= 840 (17:00)', () => {
      const res = executeQueryDirect(
        db,
        "SELECT DISTINCT slot_label FROM orders WHERE daypart = 'evening';"
      );
      for (const r of res.rows) {
        const label = r[0] as string;
        const match = label.match(/^(\d{1,2}):/);
        expect(match).not.toBeNull();
        const hour = parseInt(match![1], 10);
        expect(hour).toBeGreaterThanOrEqual(17);
      }
    });

    it('v_slot_daily.orders never exceeds max_orders', () => {
      const res = executeQueryDirect(
        db,
        'SELECT orders, max_orders FROM v_slot_daily WHERE max_orders > 0;'
      );
      for (const r of res.rows) {
        const [orders, maxOrders] = r;
        expect(orders).toBeLessThanOrEqual(maxOrders);
      }
    });

    it('Xôi thập cẩm and Mì xào are among the 3 lowest avg_sell_through items', () => {
      const res = executeQueryDirect(
        db,
        `SELECT name, AVG(sell_through) AS avg_sell_through
         FROM v_item_sellthrough
         GROUP BY dish_key, name
         ORDER BY avg_sell_through ASC
         LIMIT 3;`
      );
      const names = res.rows.map((r) => r[0] as string);
      expect(names).toContain('Xôi thập cẩm');
      expect(names).toContain('Mì xào');
    });
  });

  describe('3. Sample Queries from Section 8.1 Execute Cleanly', () => {
    it('Query 1: Yesterday / latest day revenue', () => {
      const res = executeQueryDirect(
        db,
        `SELECT revenue, orders, portions FROM v_daily WHERE date = (SELECT MAX(date) FROM v_daily);`
      );
      expect(res.rows.length).toBe(1);
      expect(res.rows[0][0]).toBeGreaterThan(0);
    });

    it('Query 2: Week comparison', () => {
      const res = executeQueryDirect(
        db,
        `SELECT week_start, SUM(revenue) AS revenue, SUM(orders) AS orders
         FROM v_daily GROUP BY week_start ORDER BY week_start DESC LIMIT 2;`
      );
      expect(res.rows.length).toBeGreaterThanOrEqual(1);
    });

    it('Query 3: Best selling items on Friday (weekday = 5)', () => {
      const res = executeQueryDirect(
        db,
        `SELECT name, SUM(portions) AS portions, SUM(revenue) AS revenue
         FROM v_item_daily WHERE weekday = 5 GROUP BY dish_key, name ORDER BY portions DESC LIMIT 5;`
      );
      expect(res.rows.length).toBeGreaterThan(0);
    });

    it('Query 4: Most full time slots', () => {
      const res = executeQueryDirect(
        db,
        `SELECT slot_label, SUM(is_full) AS full_days, COUNT(*) AS days, AVG(fill_rate) AS avg_fill
         FROM v_slot_daily GROUP BY slot_label ORDER BY avg_fill DESC;`
      );
      expect(res.rows.length).toBe(8);
    });

    it('Query 5: Evening slots evaluation', () => {
      const res = executeQueryDirect(
        db,
        `SELECT daypart, AVG(fill_rate) AS avg_fill, SUM(orders) AS orders
         FROM v_slot_daily GROUP BY daypart;`
      );
      expect(res.rows.length).toBe(2);
    });

    it('Query 6: Cancel reasons', () => {
      const res = executeQueryDirect(
        db,
        `SELECT cancel_reason, COUNT(*) AS orders
         FROM v_cancelled_orders GROUP BY cancel_reason ORDER BY orders DESC;`
      );
      expect(res.rows.length).toBeGreaterThan(0);
    });

    it('Query 7: Overload cancellations by weekday', () => {
      const res = executeQueryDirect(
        db,
        `SELECT weekday, COUNT(*) AS orders
         FROM v_cancelled_orders WHERE cancel_reason = 'OVERLOAD' GROUP BY weekday ORDER BY orders DESC;`
      );
      expect(res.rows.length).toBeGreaterThan(0);
    });

    it('Query 8: Prep time by slot', () => {
      const res = executeQueryDirect(
        db,
        `SELECT slot_label, AVG(prep_minutes) AS avg_prep, COUNT(prep_minutes) AS orders
         FROM v_completed_orders GROUP BY slot_label ORDER BY slot_label;`
      );
      expect(res.rows.length).toBe(8);
    });

    it('Query 9: Low sell-through items', () => {
      const res = executeQueryDirect(
        db,
        `SELECT name, AVG(sell_through) AS avg_sell_through, SUM(sold_out) AS sold_out_days, COUNT(*) AS days
         FROM v_item_sellthrough GROUP BY dish_key, name ORDER BY avg_sell_through ASC LIMIT 5;`
      );
      expect(res.rows.length).toBe(5);
    });

    it('Query 10: Drink revenue by slot', () => {
      const res = executeQueryDirect(
        db,
        `SELECT slot_label, SUM(line_revenue) AS revenue
         FROM v_completed_items WHERE category = 'Đồ uống' GROUP BY slot_label ORDER BY slot_label;`
      );
      expect(res.rows.length).toBeGreaterThan(0);
    });

    it('Query 11: Vegetarian items under 30k available', () => {
      const res = executeQueryDirect(
        db,
        `SELECT name, price, remaining_qty
         FROM menu_items
         WHERE tags LIKE '%,vegetarian,%' AND price < 30000 AND available = 1;`
      );
      expect(res.rows.length).toBeGreaterThan(0);
    });
  });

  describe('4. SQL Guard and Safety Enforcement', () => {
    it('Rejects non-SELECT / dangerous statements', () => {
      const forbidden = [
        'INSERT INTO orders (order_id) VALUES ("test");',
        'UPDATE orders SET total = 0;',
        'DELETE FROM orders;',
        'DROP TABLE orders;',
        'CREATE TABLE x(a INT);',
        'ATTACH DATABASE "foo.db" AS foo;',
        'PRAGMA query_only = OFF;',
        'SELECT 1; DELETE FROM orders;',
        '   ',
        '',
      ];

      for (const query of forbidden) {
        const result = validateSql(query);
        expect(result.valid).toBe(false);
      }
    });

    it('Allows valid SELECT and WITH statements', () => {
      const valid = [
        'SELECT 1;',
        'SELECT * FROM orders WHERE total > 10000',
        '-- Chú thích\nSELECT * FROM v_daily;',
        '/* Nhiều dòng\n chú thích */ WITH t AS (SELECT 1 AS val) SELECT * FROM t;',
        "SELECT * FROM menu_items WHERE tags LIKE '%,vegetarian,%';",
      ];

      for (const query of valid) {
        const result = validateSql(query);
        expect(result.valid).toBe(true);
        expect(result.cleanSql).toBeDefined();
      }
    });

    it('PRAGMA query_only = ON prevents mutation even if Guard were bypassed', () => {
      expect(() => {
        executeQueryDirect(db, 'DELETE FROM orders;');
      }).toThrow();
    });

    it('Truncates output to maxRows and sets truncated = true', () => {
      const res = executeQueryDirect(db, 'SELECT * FROM order_items;', 15);
      expect(res.rows.length).toBe(15);
      expect(res.rowCount).toBeGreaterThan(15);
      expect(res.truncated).toBe(true);
    });

    it('Times out runaway queries in worker thread', async () => {
      // Cross join 3 times generates huge dataset that times out
      const slowQuery = `
        SELECT count(*)
        FROM order_items a
        CROSS JOIN order_items b
        CROSS JOIN order_items c;
      `;
      await expect(
        executeQueryInWorker(db, slowQuery, 200, 400)
      ).rejects.toThrow(/Câu truy vấn quá lâu/);
    });
  });

  describe('5. Source Code Audit (Read-only & stdout preservation)', () => {
    it('No Firestore write operations (set, update, delete, add, runTransaction, batch) in firestoreSource.ts and only get* in dataSource.ts', async () => {
      const fsSourcePath = path.resolve(__dirname, '../src/data/firestoreSource.ts');
      const content = await fs.readFile(fsSourcePath, 'utf-8');

      const forbiddenFirestoreMethods = [
        /\.set\s*\(/,
        /\.update\s*\(/,
        /\.delete\s*\(/,
        /\.add\s*\(/,
        /runTransaction\s*\(/,
        /\.batch\s*\(/,
      ];

      for (const regex of forbiddenFirestoreMethods) {
        expect(regex.test(content)).toBe(false);
      }

      // Check dataSource.ts: only get* methods
      const dsPath = path.resolve(__dirname, '../src/data/dataSource.ts');
      const dsContent = await fs.readFile(dsPath, 'utf-8');
      const lines = dsContent.split('\n');
      for (const line of lines) {
        const trimmed = line.trim();
        if (trimmed && !trimmed.startsWith('//') && !trimmed.startsWith('import') && !trimmed.startsWith('export interface') && !trimmed.startsWith('}')) {
          expect(trimmed).toMatch(/^get[A-Za-z0-9_]+\s*\(/);
        }
      }
    });

    it('No console.log (stdout) in src/ to prevent MCP stdio transport corruption', async () => {
      const srcDir = path.resolve(__dirname, '../src');
      const scanDir = async (dir: string): Promise<string[]> => {
        const files: string[] = [];
        const entries = await fs.readdir(dir, { withFileTypes: true });
        for (const entry of entries) {
          const fullPath = path.join(dir, entry.name);
          if (entry.isDirectory()) {
            files.push(...(await scanDir(fullPath)));
          } else if (entry.name.endsWith('.ts')) {
            files.push(fullPath);
          }
        }
        return files;
      };

      const files = await scanDir(srcDir);
      for (const file of files) {
        const content = await fs.readFile(file, 'utf-8');
        expect(content).not.toMatch(/console\.log\s*\(/);
      }
    });
  });
});
