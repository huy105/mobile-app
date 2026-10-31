import { describe, it, expect, beforeAll } from 'vitest';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import fs from 'node:fs/promises';

import { FixtureSource } from '../src/data/fixtureSource.js';
import { computeOrderMetrics } from '../src/metrics/engine.js';
import { applyOrderFilters } from '../src/util/filters.js';
import { computeTopItems, computeUnsoldItems } from '../src/metrics/items.js';
import { computeOrdersBySlot } from '../src/metrics/slots.js';
import { computeCancellations } from '../src/metrics/cancellations.js';
import { computePrepTimeStats } from '../src/metrics/prep.js';
import { computeForecast } from '../src/metrics/forecast.js';
import { computeListMenu } from '../src/metrics/menu.js';
import { addDays, getIsoWeekday } from '../src/util/dates.js';

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
      items: Array<{ quantity: number; price: number; category?: string; dishKey?: string; name: string }>;
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

  it('Criteria 2: Engine totals match sum of COMPLETED total from JSON', async () => {
    const orders = await source.getOrders({ from: minDate, to: maxDate });
    const timeSlots = await source.getTimeSlots();
    const filterCtx = applyOrderFilters(orders, { date_from: minDate, date_to: maxDate }, timeSlots);
    const engineRes = computeOrderMetrics(filterCtx.orders, filterCtx.appliedFilters, filterCtx.hasLineItemFilter, timeSlots);

    const expectedRevenue = rawJson.orders
      .filter((o) => o.status === 'COMPLETED')
      .reduce((acc, o) => acc + o.total, 0);

    const expectedPortions = rawJson.orders
      .filter((o) => o.status === 'COMPLETED')
      .reduce((acc, o) => acc + o.items.reduce((sum, i) => sum + i.quantity, 0), 0);

    expect(engineRes.totals.revenue).toBe(expectedRevenue);
    expect(engineRes.totals.portions).toBe(expectedPortions);
    expect(engineRes.totals.orders).toBe(rawJson.orders.filter((o) => o.status === 'COMPLETED').length);
  });

  it('Criteria 3: Fill rates, rate percentages, and sell-through ratios are valid', async () => {
    const orders = await source.getOrders({ from: minDate, to: maxDate });
    const timeSlots = await source.getTimeSlots();
    const menuItems = await source.getMenuItems({ from: minDate, to: maxDate });

    const slotStats = computeOrdersBySlot(orders, timeSlots);
    for (const s of slotStats.slots) {
      expect(s.fillRate).toBeGreaterThanOrEqual(0);
    }

    const cancelStats = computeCancellations(orders);
    expect(cancelStats.ratePct).toBeGreaterThanOrEqual(0);
    expect(cancelStats.ratePct).toBeLessThanOrEqual(100);

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

    const targetDate = addDays(maxDate, 1);
    const fullForecast = computeForecast(menuItems, targetDate, 10);

    expect(fullForecast.items.length).toBeGreaterThan(0);
    for (const item of fullForecast.items) {
      if (item.weeksOfData >= 2) {
        expect(typeof item.forecast).toBe('number');
        expect(Number.isInteger(item.forecast)).toBe(true);
      }
    }

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

  it('Criteria 9: Shared Filters (weekdays, daypart, categories line item revenue)', async () => {
    const orders = await source.getOrders({ from: minDate, to: maxDate });
    const timeSlots = await source.getTimeSlots();

    // weekdays: [5] (Friday)
    const friCtx = applyOrderFilters(orders, { date_from: minDate, date_to: maxDate, weekdays: [5] }, timeSlots);
    for (const o of friCtx.orders) {
      expect(getIsoWeekday(o.date)).toBe(5);
    }

    // daypart: lunch vs evening
    const lunchCtx = applyOrderFilters(orders, { date_from: minDate, date_to: maxDate, daypart: 'lunch' }, timeSlots);
    const eveCtx = applyOrderFilters(orders, { date_from: minDate, date_to: maxDate, daypart: 'evening' }, timeSlots);
    expect(lunchCtx.orders.length + eveCtx.orders.length).toBe(orders.length);

    // categories: line-item revenue
    const drinkCtx = applyOrderFilters(orders, { date_from: minDate, date_to: maxDate, categories: ['Đồ uống'] }, timeSlots);
    const drinkRes = computeOrderMetrics(drinkCtx.orders, drinkCtx.appliedFilters, drinkCtx.hasLineItemFilter, timeSlots);

    let manualDrinkRev = 0;
    for (const o of rawJson.orders) {
      if (o.status !== 'COMPLETED') continue;
      for (const item of o.items) {
        if (item.category === 'Đồ uống') {
          manualDrinkRev += item.price * item.quantity;
        }
      }
    }
    expect(drinkRes.totals.revenue).toBe(manualDrinkRev);
  });
});
