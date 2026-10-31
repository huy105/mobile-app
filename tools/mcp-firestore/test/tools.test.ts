import { describe, it, expect, beforeAll } from 'vitest';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import fs from 'node:fs/promises';

import { createServer } from '../src/index.js';
import { FixtureSource } from '../src/data/fixtureSource.js';
import { addDays } from '../src/util/dates.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const fixturePath = path.resolve(__dirname, '../../../docs/data/seed-history.json');
const seedMenuPath = path.resolve(__dirname, '../../../docs/data/seed-menu.json');

describe('All 9 MCP Tools Integration & Consistency Test', () => {
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

  it('Verifies exactly 9 tools registered in McpServer', () => {
    const tools = Object.keys((server as any)._registeredTools);
    expect(tools.length).toBe(9);
    expect(tools).toContain('query_orders');
    expect(tools).toContain('get_sales_summary');
    expect(tools).toContain('get_top_items');
    expect(tools).toContain('get_orders_by_slot');
    expect(tools).toContain('get_cancellations');
    expect(tools).toContain('get_prep_time_stats');
    expect(tools).toContain('get_unsold_items');
    expect(tools).toContain('forecast_tomorrow');
    expect(tools).toContain('list_menu');
  });

  it('Tool 0: query_orders runs without params and matches get_sales_summary totals', async () => {
    const resQuery = await callTool('query_orders', {
      date_from: minDate,
      date_to: maxDate,
    });
    const resSales = await callTool('get_sales_summary', {
      date_from: minDate,
      date_to: maxDate,
    });

    expect(resQuery.isError).toBeFalsy();
    expect(resSales.isError).toBeFalsy();

    expect(resQuery.structuredContent.totals.revenue).toBe(resSales.structuredContent.revenue);
    expect(resQuery.structuredContent.totals.orders).toBe(resSales.structuredContent.orders);
    expect(resQuery.structuredContent.totals.portions).toBe(resSales.structuredContent.portions);
  });

  it('Cross-tool consistency: get_top_items, get_cancellations, get_prep_time_stats match query_orders', async () => {
    // 1. Top items vs query_orders group_by: ['dish']
    const queryDishes = await callTool('query_orders', {
      date_from: minDate,
      date_to: maxDate,
      group_by: ['dish'],
      metrics: ['portions', 'revenue'],
      sort_by: 'portions',
      sort_dir: 'desc',
      limit: 5,
    });
    const topBest = await callTool('get_top_items', {
      date_from: minDate,
      date_to: maxDate,
      limit: 5,
      order: 'best',
    });
    expect(topBest.structuredContent.items[0].portions).toBe(queryDishes.structuredContent.rows[0].portions);
    expect(topBest.structuredContent.items[0].revenue).toBe(queryDishes.structuredContent.rows[0].revenue);

    // 2. Cancellations total matches query_orders cancelled_orders
    const queryCancel = await callTool('query_orders', {
      date_from: minDate,
      date_to: maxDate,
      metrics: ['cancelled_orders'],
    });
    const cancelTool = await callTool('get_cancellations', {
      date_from: minDate,
      date_to: maxDate,
    });
    expect(cancelTool.structuredContent.total).toBe(queryCancel.structuredContent.totals.cancelled_orders);

    // 3. Prep time avg matches query_orders avg_prep_minutes
    const queryPrep = await callTool('query_orders', {
      date_from: minDate,
      date_to: maxDate,
      metrics: ['avg_prep_minutes'],
    });
    const prepTool = await callTool('get_prep_time_stats', {
      date_from: minDate,
      date_to: maxDate,
    });
    expect(prepTool.structuredContent.prep.avg).toBe(queryPrep.structuredContent.totals.avg_prep_minutes);
  });

  it('get_unsold_items and forecast_tomorrow with daypart returns ignoredFilters without error', async () => {
    const unsoldRes = await callTool('get_unsold_items', {
      date_from: minDate,
      date_to: maxDate,
      daypart: 'evening',
    });
    expect(unsoldRes.isError).toBeFalsy();
    expect(unsoldRes.structuredContent.ignoredFilters).toContain('daypart');

    const forecastRes = await callTool('forecast_tomorrow', {
      date: addDays(maxDate, 1),
      daypart: 'evening',
    });
    expect(forecastRes.isError).toBeFalsy();
    expect(forecastRes.structuredContent.ignoredFilters).toContain('daypart');
  });

  it('Security check: No PII (userId, userName, demo_student_) leaked in any tool output', async () => {
    const tools = [
      'query_orders',
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
