import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { DataSource } from '../data/dataSource.js';
import { Config } from '../config.js';
import { filterZodShape, applyOrderFilters } from '../util/filters.js';
import { computeOrderMetrics } from '../metrics/engine.js';
import { createToolSuccess, createToolError, formatPercent } from '../util/format.js';

export function registerOrdersBySlotTool(server: McpServer, dataSource: DataSource, config: Config) {
  server.tool(
    'get_orders_by_slot',
    'Lối tắt hỏi khung giờ đông, khung giờ hay đầy hoặc tỷ lệ lấp đầy đơn hàng theo từng khung giờ.',
    {
      ...filterZodShape,
    },
    async (args) => {
      try {
        const timeSlots = await dataSource.getTimeSlots();
        const filterCtx = applyOrderFilters([], args, timeSlots, config.timezone);
        const ordersRaw = await dataSource.getOrders(filterCtx.appliedFilters.range);
        const currentCtx = applyOrderFilters(ordersRaw, args, timeSlots, config.timezone);

        // Gọi engine với group_by: slot
        const engineRes = computeOrderMetrics(
          currentCtx.orders,
          currentCtx.appliedFilters,
          currentCtx.hasLineItemFilter,
          timeSlots,
          {
            groupBy: ['slot'],
            metrics: ['orders', 'in_progress_orders'],
            limit: 100,
          }
        );

        // Số ngày có đơn không CANCELLED
        const validOrders = currentCtx.orders.filter((o) => o.status !== 'CANCELLED');
        const distinctDays = new Set(validOrders.map((o) => o.date));
        const daysCount = distinctDays.size || 1;

        // Đếm daily order counts cho từng slot để tính fullDays
        const slotDailyCounts = new Map<string, Map<string, number>>();
        for (const o of validOrders) {
          const key = o.slotLabel || o.slotId;
          const dayMap = slotDailyCounts.get(key) || new Map<string, number>();
          dayMap.set(o.date, (dayMap.get(o.date) || 0) + 1);
          slotDailyCounts.set(key, dayMap);
        }

        // Tạo danh sách slots từ timeSlots và engine rows
        const slotResultsMap = new Map<
          string,
          { slotLabel: string; orders: number; fillRate: number; fullDays: number; days: number }
        >();

        for (const ts of timeSlots) {
          slotResultsMap.set(ts.label, {
            slotLabel: ts.label,
            orders: 0,
            fillRate: 0,
            fullDays: 0,
            days: daysCount,
          });
        }

        for (const r of engineRes.rows) {
          const label = String(r.key.slot);
          const nonCancelledOrders =
            ((r.orders as number) ?? 0) + ((r.in_progress_orders as number) ?? 0);

          const ts =
            timeSlots.find((s) => s.label === label || s.id === label) ||
            timeSlots.find((s) => s.label === label);
          const maxOrders = ts?.maxOrders || 10;
          const capacity = maxOrders * daysCount;
          const fillRate = capacity > 0 ? Math.round((nonCancelledOrders / capacity) * 1000) / 1000 : 0;

          const dayMap = slotDailyCounts.get(label) || new Map<string, number>();
          let fullDays = 0;
          for (const count of dayMap.values()) {
            if (count >= maxOrders) fullDays++;
          }

          slotResultsMap.set(label, {
            slotLabel: label,
            orders: nonCancelledOrders,
            fillRate,
            fullDays,
            days: daysCount,
          });
        }

        const slots = Array.from(slotResultsMap.values());
        slots.sort((a, b) => b.fillRate - a.fillRate);

        const busiest = slots.length > 0 && slots[0].orders > 0 ? slots[0].slotLabel : null;
        const oftenFull = slots.filter((s) => s.fullDays > 0).map((s) => s.slotLabel);

        let summary = `Thống kê khung giờ nhận đồ từ ${currentCtx.appliedFilters.range.from} đến ${currentCtx.appliedFilters.range.to}: `;
        if (busiest) {
          summary += `Khung giờ đông nhất là "${busiest}" với tỷ lệ lấp đầy ${formatPercent(
            slots[0].fillRate
          )} (${slots[0].orders} đơn). `;
        }
        if (oftenFull.length > 0) {
          summary += `Các khung giờ từng chạm ngưỡng tối đa (đầy): ${oftenFull.join(', ')}.`;
        } else {
          summary += 'Chưa có khung giờ nào bị đầy.';
        }

        return createToolSuccess(summary, {
          appliedFilters: currentCtx.appliedFilters,
          slots,
          busiest,
          oftenFull,
          skipped: engineRes.skipped,
        } as unknown as Record<string, unknown>);
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : String(err);
        return createToolError(msg);
      }
    }
  );
}
