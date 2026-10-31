import { describe, it, expect, beforeAll } from 'vitest';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import fs from 'node:fs/promises';

import { createServer } from '../src/index.js';
import { FixtureSource } from '../src/data/fixtureSource.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const fixturePath = path.resolve(__dirname, '../../../docs/data/seed-history.json');
const seedMenuPath = path.resolve(__dirname, '../../../docs/data/seed-menu.json');

describe('All 8 MCP Tools Integration Test', () => {
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

  it('1. get_sales_summary runs and returns formatted response', async () => {
    const res = await callTool('get_sales_summary', {
      date_from: minDate,
      date_to: maxDate,
      group_by: 'day',
    });
    expect(res.isError).toBeFalsy();
    expect(res.content[0].text).toContain('tổng doanh thu');
    expect(res.structuredContent).toBeDefined();
    expect(res.structuredContent.revenue).toBeGreaterThan(0);
  });

  it('2. get_top_items runs and returns best / worst list', async () => {
    const resBest = await callTool('get_top_items', {
      date_from: minDate,
      date_to: maxDate,
      limit: 5,
      order: 'best',
    });
    expect(resBest.isError).toBeFalsy();
    expect(resBest.structuredContent.items.length).toBe(5);

    const resWorst = await callTool('get_top_items', {
      date_from: minDate,
      date_to: maxDate,
      limit: 5,
      order: 'worst',
    });
    expect(resWorst.isError).toBeFalsy();
    expect(resWorst.structuredContent.items.length).toBe(5);
  });

  it('3. get_orders_by_slot runs and identifies busiest slot', async () => {
    const res = await callTool('get_orders_by_slot', {
      date_from: minDate,
      date_to: maxDate,
    });
    expect(res.isError).toBeFalsy();
    expect(res.structuredContent.slots.length).toBe(8);
    expect(res.structuredContent.busiest).toBeTruthy();
  });

  it('4. get_cancellations runs and details reasons and no-show', async () => {
    const res = await callTool('get_cancellations', {
      date_from: minDate,
      date_to: maxDate,
    });
    expect(res.isError).toBeFalsy();
    expect(res.structuredContent.total).toBeGreaterThan(0);
    expect(res.structuredContent.byReason).toHaveProperty('SOLD_OUT');
  });

  it('5. get_prep_time_stats runs and reports avg and p90', async () => {
    const res = await callTool('get_prep_time_stats', {
      date_from: minDate,
      date_to: maxDate,
    });
    expect(res.isError).toBeFalsy();
    expect(res.structuredContent.prep.avg).toBeGreaterThan(0);
    expect(res.structuredContent.prep.p90).toBeGreaterThanOrEqual(res.structuredContent.prep.avg);
  });

  it('6. get_unsold_items runs and identifies unsold items', async () => {
    const res = await callTool('get_unsold_items', {
      date_from: minDate,
      date_to: maxDate,
      threshold: 0.5,
    });
    expect(res.isError).toBeFalsy();
    expect(res.structuredContent.items.length).toBeGreaterThan(0);
    const statuses = res.structuredContent.items.map((i: any) => i.status);
    expect(statuses).toContain('ế');
  });

  it('7. forecast_tomorrow runs and forecasts portion quantities', async () => {
    const res = await callTool('forecast_tomorrow', {
      date: '2026-10-31',
      buffer_pct: 10,
    });
    expect(res.isError).toBeFalsy();
    expect(res.structuredContent.items.length).toBeGreaterThan(0);
  });

  it('8. list_menu runs and filters vegetarian items under 35k', async () => {
    const res = await callTool('list_menu', {
      date: minDate,
      tags: ['vegetarian'],
      max_price: 35000,
    });
    expect(res.isError).toBeFalsy();
    for (const item of res.structuredContent.items) {
      expect(item.tags).toContain('vegetarian');
      expect(item.price).toBeLessThanOrEqual(35000);
    }
  });

  it('Security check: No PII (userId, demo_student_) leaked in any tool output', async () => {
    const tools = [
      'get_sales_summary',
      'get_top_items',
      'get_orders_by_slot',
      'get_cancellations',
      'get_prep_time_stats',
      'get_unsold_items',
      'forecast_tomorrow',
      'list_menu',
    ];

    for (const name of tools) {
      const res = await callTool(name, { date_from: minDate, date_to: maxDate });
      const rawText = JSON.stringify(res);
      expect(rawText).not.toContain('demo_student_');
      expect(rawText).not.toContain('userName');
      expect(rawText).not.toContain('userId');
    }
  });
});
