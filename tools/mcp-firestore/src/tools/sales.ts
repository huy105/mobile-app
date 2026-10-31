import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { z } from 'zod';
import { DataSource } from '../data/dataSource.js';
import { Config } from '../config.js';
import { filterZodShape, applyOrderFilters } from '../util/filters.js';
import { getPreviousPeriod } from '../util/dates.js';
import { computeOrderMetrics, Dimension } from '../metrics/engine.js';
import { createToolSuccess, createToolError, formatCurrencyVND } from '../util/format.js';

export function registerSalesTool(server: McpServer, dataSource: DataSource, config: Config) {
  server.tool(
    'get_sales_summary',
    'Lối tắt hỏi doanh thu, số đơn, số suất bán, giá trị đơn trung bình và so sánh với kỳ liền trước.',
    {
      ...filterZodShape,
      group_by: z
        .enum(['none', 'day', 'week', 'category'])
        .optional()
        .describe('Gom nhóm số liệu theo ngày (day), tuần (week), loại món (category) hoặc không (none)'),
    },
    async (args) => {
      try {
        const timeSlots = await dataSource.getTimeSlots();

        // 1. Áp dụng bộ lọc cho kỳ hiện tại
        const filterCtx = applyOrderFilters([], args, timeSlots, config.timezone);
        const currentOrdersRaw = await dataSource.getOrders(filterCtx.appliedFilters.range);
        const currentCtx = applyOrderFilters(currentOrdersRaw, args, timeSlots, config.timezone);

        const groupDims: Dimension[] =
          args.group_by && args.group_by !== 'none' ? [args.group_by as Dimension] : [];

        const currentEngine = computeOrderMetrics(
          currentCtx.orders,
          currentCtx.appliedFilters,
          currentCtx.hasLineItemFilter,
          timeSlots,
          {
            groupBy: groupDims,
            metrics: [
              'revenue',
              'orders',
              'portions',
              'avg_order_value',
              'cancelled_orders',
              'in_progress_orders',
            ],
            sortBy: 'revenue',
            sortDir: 'desc',
            limit: 100,
          }
        );

        // 2. Tính kỳ liền trước cùng số ngày
        const prevRange = getPreviousPeriod(currentCtx.appliedFilters.range);
        const prevOrdersRaw = await dataSource.getOrders(prevRange);
        const prevArgs = { ...args, date_from: prevRange.from, date_to: prevRange.to };
        const prevCtx = applyOrderFilters(prevOrdersRaw, prevArgs, timeSlots, config.timezone);

        const prevEngine = computeOrderMetrics(
          prevCtx.orders,
          prevCtx.appliedFilters,
          prevCtx.hasLineItemFilter,
          timeSlots,
          {
            metrics: ['revenue'],
          }
        );

        const revenue = (currentEngine.totals.revenue as number) ?? 0;
        const prevRevenue = (prevEngine.totals.revenue as number) ?? 0;

        let changePctVsPrevious: number | null = null;
        if (prevRevenue > 0) {
          changePctVsPrevious = Math.round(((revenue - prevRevenue) / prevRevenue) * 1000) / 10;
        }

        const groups =
          groupDims.length > 0
            ? currentEngine.rows.map((r) => ({
                key: String(r.key[groupDims[0]]),
                revenue: (r.revenue as number) ?? 0,
                orders: (r.orders as number) ?? 0,
                portions: (r.portions as number) ?? 0,
              }))
            : undefined;

        const result = {
          range: currentCtx.appliedFilters.range,
          revenue,
          orders: (currentEngine.totals.orders as number) ?? 0,
          portions: (currentEngine.totals.portions as number) ?? 0,
          avgOrderValue: (currentEngine.totals.avg_order_value as number) ?? 0,
          changePctVsPrevious,
          inProgressOrders: (currentEngine.totals.in_progress_orders as number) ?? 0,
          cancelledOrders: (currentEngine.totals.cancelled_orders as number) ?? 0,
          appliedFilters: currentCtx.appliedFilters,
          skipped: currentEngine.skipped,
          groups,
        };

        let summary = `Từ ${result.range.from} đến ${result.range.to}, tổng doanh thu đạt ${formatCurrencyVND(
          result.revenue
        )} từ ${result.orders} đơn hoàn thành (${result.portions} suất).`;

        if (changePctVsPrevious !== null) {
          const sign = changePctVsPrevious > 0 ? '+' : '';
          summary += ` So với kỳ liền trước (${prevRange.from} đến ${prevRange.to}), doanh thu thay đổi ${sign}${changePctVsPrevious}%.`;
        }

        if (result.inProgressOrders > 0 || result.cancelledOrders > 0) {
          summary += ` Ngoài ra có ${result.inProgressOrders} đơn đang xử lý và ${result.cancelledOrders} đơn đã hủy.`;
        }

        return createToolSuccess(summary, result as unknown as Record<string, unknown>);
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : String(err);
        return createToolError(msg);
      }
    }
  );
}
