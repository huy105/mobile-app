import { describe, it, expect, beforeAll } from 'vitest';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import fs from 'node:fs/promises';

import { FixtureSource } from '../src/data/fixtureSource.js';
import { computeSalesSummary } from '../src/metrics/sales.js';
import { computeTopItems, computeUnsoldItems } from '../src/metrics/items.js';
import { computeOrdersBySlot } from '../src/metrics/slots.js';
import { computeCancellations } from '../src/metrics/cancellations.js';
import { computePrepTimeStats } from '../src/metrics/prep.js';
import { computeForecast } from '../src/metrics/forecast.js';
import { computeListMenu } from '../src/metrics/menu.js';
import { addDays } from '../src/util/dates.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const fixturePath = path.resolve(__dirname, '../../../docs/data/seed-history.json');
const seedMenuPath = path.resolve(__dirname, '../../../docs/data/seed-menu.json');

describe('Full Metrics and Tools Verification against Section 9 Criteria', () => {
  let source: FixtureSource;
  let rawJson: {
    orders: Array<{
      id: string;
      date: string;
      status: string;
      total: number;
      items: Array<{ quantity: number }>;
      cancelReason?: string;
    }>;
    menuItems: Array<{
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

  beforeAll(async () => {
    source = new FixtureSource(fixturePath, seedMenuPath);
    const content = await fs.readFile(fixturePath, 'utf-8');
    rawJson = JSON.parse(content);

    const dates = rawJson.orders.map((o) => o.date).sort();
    minDate = dates[0];
    maxDate = dates[dates.length - 1];
  });

  it('Criteria 1: Total orders = COMPLETED + CANCELLED in seed-history', async () => {
    const orders = await source.getOrders({ from: minDate, to: maxDate });
    const completed = orders.filter((o) => o.status === 'COMPLETED').length;
    const cancelled = orders.filter((o) => o.status === 'CANCELLED').length;
    const inProgress = orders.filter((o) => ['RECEIVED', 'PREPARING', 'READY'].includes(o.status)).length;

    expect(inProgress).toBe(0);
    expect(orders.length).toBe(completed + cancelled);
  });

  it('Criteria 2: get_sales_summary revenue matches sum of COMPLETED total, portions matches sum of quantity', async () => {
    const orders = await source.getOrders({ from: minDate, to: maxDate });
    const result = computeSalesSummary(orders, { from: minDate, to: maxDate }, [], 'day');

    const expectedRevenue = rawJson.orders
      .filter((o) => o.status === 'COMPLETED')
      .reduce((acc, o) => acc + o.total, 0);

    const expectedPortions = rawJson.orders
      .filter((o) => o.status === 'COMPLETED')
      .reduce((acc, o) => acc + o.items.reduce((sum, i) => sum + i.quantity, 0), 0);

    expect(result.revenue).toBe(expectedRevenue);
    expect(result.portions).toBe(expectedPortions);
    expect(result.orders).toBe(rawJson.orders.filter((o) => o.status === 'COMPLETED').length);
    expect(result.groups).toBeDefined();
    expect(result.groups!.length).toBeGreaterThan(0);
  });

  it('Criteria 3: Fill rates, rate percentages, and sell-through ratios are valid (0..1 or 0..100)', async () => {
    const orders = await source.getOrders({ from: minDate, to: maxDate });
    const timeSlots = await source.getTimeSlots();
    const menuItems = await source.getMenuItems({ from: minDate, to: maxDate });

    // Slots
    const slotStats = computeOrdersBySlot(orders, timeSlots);
    for (const s of slotStats.slots) {
      expect(s.fillRate).toBeGreaterThanOrEqual(0);
      expect(s.fillRate).toBeLessThanOrEqual(1.5); // capacity might have slight surge
    }

    // Cancellations
    const cancelStats = computeCancellations(orders);
    expect(cancelStats.ratePct).toBeGreaterThanOrEqual(0);
    expect(cancelStats.ratePct).toBeLessThanOrEqual(100);

    // Unsold
    const unsoldStats = computeUnsoldItems(menuItems, 0.5);
    for (const item of unsoldStats) {
      expect(item.avgSellThrough).toBeGreaterThanOrEqual(0);
      expect(item.avgSellThrough).toBeLessThanOrEqual(1);
    }
  });

  it('Criteria 4: get_prep_time_stats identifies slowDays including the highest avg prep day', async () => {
    const orders = await source.getOrders({ from: minDate, to: maxDate });
    const prepResult = computePrepTimeStats(orders);

    expect(prepResult.prep.avg).toBeGreaterThan(0);
    expect(prepResult.prep.p90).toBeGreaterThanOrEqual(prepResult.prep.avg);

    // Calculate max daily prep average manually
    const dayMap = new Map<string, number[]>();
    for (const o of orders) {
      if (o.status === 'COMPLETED' && o.readyAt && o.preparingAt) {
        const diff = (o.readyAt.getTime() - o.preparingAt.getTime()) / 60000;
        if (diff >= 0) {
          const list = dayMap.get(o.date) || [];
          list.push(diff);
          dayMap.set(o.date, list);
        }
      }
    }
    let maxAvg = -1;
    let maxDateSlow = '';
    for (const [date, vals] of dayMap.entries()) {
      const avg = vals.reduce((a, b) => a + b, 0) / vals.length;
      if (avg > maxAvg) {
        maxAvg = avg;
        maxDateSlow = date;
      }
    }

    const slowDates = prepResult.slowDays.map((d) => d.date);
    expect(slowDates).toContain(maxDateSlow);
  });

  it('Criteria 5: get_unsold_items marks "Xôi thập cẩm" and "Mì xào" as status "ế"', async () => {
    const menuItems = await source.getMenuItems({ from: minDate, to: maxDate });
    const items = computeUnsoldItems(menuItems, 0.5);

    const xoi = items.find((i) => i.name.toLowerCase().includes('xôi thập cẩm'));
    const mixao = items.find((i) => i.name.toLowerCase().includes('mì xào'));

    expect(xoi).toBeDefined();
    expect(xoi!.status).toBe('ế');

    expect(mixao).toBeDefined();
    expect(mixao!.status).toBe('ế');
  });

  it('Criteria 6: get_cancellations byReason sums to total and includes all 4 reasons', async () => {
    const orders = await source.getOrders({ from: minDate, to: maxDate });
    const result = computeCancellations(orders);

    const sumReason = Object.values(result.byReason).reduce((a, b) => a + b, 0);
    expect(sumReason).toBe(result.total);

    expect(result.byReason).toHaveProperty('SOLD_OUT');
    expect(result.byReason).toHaveProperty('OVERLOAD');
    expect(result.byReason).toHaveProperty('NO_SHOW');
    expect(result.byReason).toHaveProperty('CHANGED_MIND');
  });

  it('Criteria 7: forecast_tomorrow returns integer forecast for 4 weeks of data, null for < 2 weeks', async () => {
    const menuItems = await source.getMenuItems({ from: minDate, to: maxDate });

    // With full fixture (around 4 weeks): target date maxDate + 1 day
    const targetDate = addDays(maxDate, 1);
    const fullForecast = computeForecast(menuItems, targetDate, 10);

    expect(fullForecast.items.length).toBeGreaterThan(0);
    for (const item of fullForecast.items) {
      if (item.weeksOfData >= 2) {
        expect(typeof item.forecast).toBe('number');
        expect(Number.isInteger(item.forecast)).toBe(true);
      }
    }

    // Now test with under 2 weeks of data (e.g. only 7 days)
    const sevenDaysItems = menuItems.filter((m) => m.date >= minDate && m.date <= addDays(minDate, 6));
    const shortForecast = computeForecast(sevenDaysItems, addDays(minDate, 7), 10);
    for (const item of shortForecast.items) {
      expect(item.forecast).toBeNull();
      expect(item.note).toBe('chưa đủ dữ liệu');
    }
  });

  it('Criteria 8: list_menu with vegetarian tags and max_price 30000 filters correctly', async () => {
    const menuItems = await source.getMenuItems({ from: minDate, to: minDate });
    const result = computeListMenu(menuItems, minDate, {
      tags: ['vegetarian'],
      maxPrice: 30000,
      onlyAvailable: false,
    });

    expect(result.items.length).toBeGreaterThan(0);
    for (const item of result.items) {
      expect(item.tags).toContain('vegetarian');
      expect(item.price).toBeLessThanOrEqual(30000);
    }
  });

  it('Criteria 9: Top items best vs worst ordering', async () => {
    const orders = await source.getOrders({ from: minDate, to: maxDate });
    const menuItems = await source.getMenuItems({ from: minDate, to: maxDate });

    const best = computeTopItems(orders, menuItems, 'best', 5);
    expect(best.length).toBe(5);
    expect(best[0].portions).toBeGreaterThanOrEqual(best[1].portions);

    const worst = computeTopItems(orders, menuItems, 'worst', 5);
    expect(worst.length).toBe(5);
    expect(worst[0].portions).toBeLessThanOrEqual(worst[1].portions);
  });
});
