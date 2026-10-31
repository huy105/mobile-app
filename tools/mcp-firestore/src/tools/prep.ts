import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { DataSource } from '../data/dataSource.js';
import { Config } from '../config.js';
import { filterZodShape, applyOrderFilters } from '../util/filters.js';
import { computeOrderMetrics } from '../metrics/engine.js';
import { calculateAvg, calculateP90 } from '../metrics/stats.js';
import { createToolSuccess, createToolError } from '../util/format.js';

export function registerPrepTool(server: McpServer, dataSource: DataSource, config: Config) {
  server.tool(
    'get_prep_time_stats',
    'Lối tắt hỏi bếp chuẩn bị món mất bao lâu, thời gian chờ bắt đầu nấu hoặc ngày nào bếp chậm bất thường.',
    {
      ...filterZodShape,
    },
    async (args) => {
      try {
        const timeSlots = await dataSource.getTimeSlots();
        const filterCtx = applyOrderFilters([], args, timeSlots, config.timezone);
        const ordersRaw = await dataSource.getOrders(filterCtx.appliedFilters.range);
        const currentCtx = applyOrderFilters(ordersRaw, args, timeSlots, config.timezone);

        // Gọi engine để lấy các chỉ số thời gian tổng thể và theo slot
        const engineRes = computeOrderMetrics(
          currentCtx.orders,
          currentCtx.appliedFilters,
          currentCtx.hasLineItemFilter,
          timeSlots,
          {
            groupBy: ['slot'],
            metrics: [
              'avg_prep_minutes',
              'p90_prep_minutes',
              'avg_wait_minutes',
              'avg_pickup_delay_minutes',
            ],
            limit: 100,
          }
        );

        // Tính ngày chậm bất thường (dựa trên trung bình theo ngày)
        const dayMap = new Map<string, number[]>();
        for (const o of currentCtx.orders) {
          if (o.status !== 'COMPLETED') continue;
          if (o.readyAt && o.preparingAt) {
            const diff = (o.readyAt.getTime() - o.preparingAt.getTime()) / 60000;
            if (diff >= 0) {
              const list = dayMap.get(o.date) || [];
              list.push(diff);
              dayMap.set(o.date, list);
            }
          }
        }

        const dailyAvgs: Array<{ date: string; avg: number }> = [];
        for (const [date, values] of dayMap.entries()) {
          dailyAvgs.push({
            date,
            avg: values.reduce((a, b) => a + b, 0) / values.length,
          });
        }

        const slowDays: Array<{ date: string; avgPrep: number }> = [];
        if (dailyAvgs.length > 1) {
          const totalMean = dailyAvgs.reduce((a, b) => a + b.avg, 0) / dailyAvgs.length;
          const variance =
            dailyAvgs.reduce((a, b) => a + Math.pow(b.avg - totalMean, 2), 0) / dailyAvgs.length;
          const stdDev = Math.sqrt(variance);
          const threshold = totalMean + 1.5 * stdDev;

          for (const d of dailyAvgs) {
            if (d.avg > threshold) {
              slowDays.push({
                date: d.date,
                avgPrep: Math.round(d.avg * 10) / 10,
              });
            }
          }
        }
        slowDays.sort((a, b) => b.avgPrep - a.avgPrep);

        const bySlot = engineRes.rows.map((r) => ({
          slotLabel: String(r.key.slot),
          avgPrep: (r.avg_prep_minutes as number) ?? 0,
          p90Prep: (r.p90_prep_minutes as number) ?? 0,
        }));

        const result = {
          appliedFilters: currentCtx.appliedFilters,
          prep: {
            avg: (engineRes.totals.avg_prep_minutes as number) ?? 0,
            p90: (engineRes.totals.p90_prep_minutes as number) ?? 0,
          },
          waitToStart: {
            avg: (engineRes.totals.avg_wait_minutes as number) ?? 0,
          },
          pickupDelay: {
            avg: (engineRes.totals.avg_pickup_delay_minutes as number) ?? 0,
          },
          bySlot,
          slowDays,
          skipped: engineRes.skipped,
        };

        let summary = `Từ ${result.appliedFilters.range.from} đến ${result.appliedFilters.range.to}, thời gian chuẩn bị món trung bình là ${result.prep.avg} phút (P90: ${result.prep.p90} phút). `;
        if (slowDays.length > 0) {
          summary += `Có ${slowDays.length} ngày chậm bất thường: ${slowDays
            .map((d) => `${d.date} (${d.avgPrep} phút)`)
            .join(', ')}.`;
        } else {
          summary += 'Không có ngày nào chậm bất thường.';
        }

        return createToolSuccess(summary, result as unknown as Record<string, unknown>);
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : String(err);
        return createToolError(msg);
      }
    }
  );
}
