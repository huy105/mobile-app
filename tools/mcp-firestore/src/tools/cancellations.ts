import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { DataSource } from '../data/dataSource.js';
import { Config } from '../config.js';
import { filterZodShape, applyOrderFilters } from '../util/filters.js';
import { computeOrderMetrics } from '../metrics/engine.js';
import { createToolSuccess, createToolError, formatPercent } from '../util/format.js';

export function registerCancellationsTool(server: McpServer, dataSource: DataSource, config: Config) {
  server.tool(
    'get_cancellations',
    'Lối tắt hỏi về các đơn hàng bị hủy, lý do hủy, ai hủy hoặc top món xuất hiện trong đơn hủy.',
    {
      ...filterZodShape,
    },
    async (args) => {
      try {
        const timeSlots = await dataSource.getTimeSlots();
        const filterCtx = applyOrderFilters([], args, timeSlots, config.timezone);
        const ordersRaw = await dataSource.getOrders(filterCtx.appliedFilters.range);
        const currentCtx = applyOrderFilters(ordersRaw, args, timeSlots, config.timezone);

        // Gọi engine để lấy thống kê hủy và phân nhóm theo cancel_reason
        const engineRes = computeOrderMetrics(
          currentCtx.orders,
          currentCtx.appliedFilters,
          currentCtx.hasLineItemFilter,
          timeSlots,
          {
            groupBy: ['cancel_reason'],
            metrics: ['orders', 'cancelled_orders', 'cancel_rate'],
            limit: 100,
          }
        );

        const totalCancelled = (engineRes.totals.cancelled_orders as number) ?? 0;
        const totalCompleted = (engineRes.totals.orders as number) ?? 0;
        const relevantTotal = totalCancelled + totalCompleted;
        const ratePct = relevantTotal > 0 ? Math.round((totalCancelled / relevantTotal) * 1000) / 10 : 0;

        const byReason: Record<string, number> = {};
        for (const r of engineRes.rows) {
          const reason = String(r.key.cancel_reason);
          byReason[reason] = (r.cancelled_orders as number) ?? 0;
        }

        // Đếm theo cancelledBy và top món trong đơn hủy
        const byCancelledBy: Record<string, number> = {};
        const dishMap = new Map<string, { dishKey: string; name: string; quantity: number }>();
        let noShow = 0;

        for (const o of currentCtx.orders) {
          if (o.status !== 'CANCELLED') continue;

          const by = o.cancelledBy || 'UNKNOWN';
          byCancelledBy[by] = (byCancelledBy[by] || 0) + 1;

          if (o.cancelReason === 'NO_SHOW') noShow++;

          for (const item of o.items) {
            const key = item.dishKey || item.name;
            const entry = dishMap.get(key) || { dishKey: key, name: item.name, quantity: 0 };
            entry.quantity += item.quantity;
            dishMap.set(key, entry);
          }
        }

        const topDishesInCancelled = Array.from(dishMap.values())
          .sort((a, b) => b.quantity - a.quantity)
          .slice(0, 5);

        let summary = `Từ ${currentCtx.appliedFilters.range.from} đến ${currentCtx.appliedFilters.range.to}, có ${totalCancelled} đơn bị hủy (${formatPercent(
          ratePct
        )} trên tổng đơn). `;

        const reasons = Object.entries(byReason)
          .map(([r, c]) => `${r}: ${c}`)
          .join(', ');
        if (reasons) summary += `Lý do: ${reasons}. `;
        if (noShow > 0) summary += `Trong đó có ${noShow} đơn khách không đến lấy (NO_SHOW).`;

        return createToolSuccess(summary, {
          appliedFilters: currentCtx.appliedFilters,
          total: totalCancelled,
          ratePct,
          byReason,
          byCancelledBy,
          topDishesInCancelled,
          noShow,
          skipped: engineRes.skipped,
        } as unknown as Record<string, unknown>);
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : String(err);
        return createToolError(msg);
      }
    }
  );
}
