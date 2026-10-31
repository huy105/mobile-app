import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { z } from 'zod';
import { DataSource } from '../data/dataSource.js';
import { Config } from '../config.js';
import { resolveDateRange, getToday, addDays } from '../util/dates.js';
import { computeSlowDays } from '../metrics/slowDays.js';
import { createToolSuccess, createToolError } from '../util/format.js';

export function registerFindSlowDaysTool(
  server: McpServer,
  dataSource: DataSource,
  config: Config
) {
  server.tool(
    'find_slow_days',
    'Dùng khi hỏi ngày nào bếp chuẩn bị món chậm bất thường; sử dụng độ lệch chuẩn để phát hiện các ngày vượt ngưỡng.',
    {
      date_from: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).optional().describe('Ngày bắt đầu (yyyy-MM-dd, mặc định 30 ngày trước)'),
      date_to: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).optional().describe('Ngày kết thúc (yyyy-MM-dd, mặc định hôm nay)'),
    },
    async ({ date_from, date_to }) => {
      try {
        const today = getToday(config.timezone);
        const defaultFrom = addDays(today, -29);

        const range = resolveDateRange(
          date_from || defaultFrom,
          date_to || today,
          config.timezone
        );

        const orders = await dataSource.getOrders(range);
        const result = computeSlowDays(orders, range);

        let summary = `Từ ${range.from} đến ${range.to} (${result.daysWithData} ngày có dữ liệu): Thời gian chuẩn bị trung bình toàn kỳ là ${result.avgPrepOverall} phút (độ lệch chuẩn: ${result.stdDev} phút). `;

        if (result.slowDays.length > 0) {
          const listStr = result.slowDays
            .map((d) => `${d.date} (${d.avgPrep} phút, ${d.orders} đơn)`)
            .join(', ');
          summary += `Phát hiện ${result.slowDays.length} ngày chậm bất thường: ${listStr}.`;
        } else {
          summary += result.note || 'Không phát hiện ngày nào bị chậm bất thường.';
        }

        return createToolSuccess(summary, result as unknown as Record<string, unknown>);
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : String(err);
        return createToolError(msg);
      }
    }
  );
}
