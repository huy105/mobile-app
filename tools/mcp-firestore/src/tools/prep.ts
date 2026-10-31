import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { z } from 'zod';
import { DataSource } from '../data/dataSource.js';
import { Config } from '../config.js';
import { resolveDateRange } from '../util/dates.js';
import { computePrepTimeStats } from '../metrics/prep.js';
import { createToolSuccess, createToolError } from '../util/format.js';

export function registerPrepTool(server: McpServer, dataSource: DataSource, config: Config) {
  server.tool(
    'get_prep_time_stats',
    'Dùng khi hỏi bếp chuẩn bị món mất bao lâu, thời gian chờ bắt đầu nấu, độ trễ nhận món hoặc ngày nào bếp chậm bất thường.',
    {
      date_from: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).optional().describe('Ngày bắt đầu (yyyy-MM-dd)'),
      date_to: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).optional().describe('Ngày kết thúc (yyyy-MM-dd)'),
    },
    async ({ date_from, date_to }) => {
      try {
        const range = resolveDateRange(date_from, date_to, config.timezone);
        const orders = await dataSource.getOrders(range);

        const result = computePrepTimeStats(orders);

        let summary = `Từ ${range.from} đến ${range.to}, thời gian chuẩn bị món trung bình là ${result.prep.avg} phút (P90: ${result.prep.p90} phút). `;
        summary += `Thời gian chờ bắt đầu nấu trung bình ${result.waitToStart.avg} phút, độ trễ lấy món trung bình ${result.pickupDelay.avg} phút. `;

        if (result.slowDays.length > 0) {
          const slowDayStrs = result.slowDays.map((d) => `${d.date} (${d.avgPrep} phút)`).join(', ');
          summary += `Phát hiện ${result.slowDays.length} ngày chậm bất thường: ${slowDayStrs}. `;
        } else {
          summary += 'Không có ngày nào bị chậm bất thường. ';
        }

        if (result.skipped > 0) {
          summary += `(Bỏ qua ${result.skipped} đơn thiếu mốc thời gian).`;
        }

        return createToolSuccess(summary, { range, ...result } as unknown as Record<string, unknown>);
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : String(err);
        return createToolError(msg);
      }
    }
  );
}
