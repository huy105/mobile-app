import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { z } from 'zod';
import { DataSource } from '../data/dataSource.js';
import { Config } from '../config.js';
import { resolveDateRange } from '../util/dates.js';
import { computeCancellations } from '../metrics/cancellations.js';
import { createToolSuccess, createToolError, formatPercent } from '../util/format.js';

export function registerCancellationsTool(server: McpServer, dataSource: DataSource, config: Config) {
  server.tool(
    'get_cancellations',
    'Dùng khi hỏi về các đơn hàng bị hủy, tỷ lệ hủy, lý do hủy, ai thực hiện hủy hoặc món ăn bị hủy nhiều.',
    {
      date_from: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).optional().describe('Ngày bắt đầu (yyyy-MM-dd)'),
      date_to: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).optional().describe('Ngày kết thúc (yyyy-MM-dd)'),
    },
    async ({ date_from, date_to }) => {
      try {
        const range = resolveDateRange(date_from, date_to, config.timezone);
        const orders = await dataSource.getOrders(range);

        const result = computeCancellations(orders);

        let summary = `Từ ${range.from} đến ${range.to}, có ${result.total} đơn bị hủy (${formatPercent(
          result.ratePct
        )} trên tổng đơn hoàn thành và hủy). `;

        const reasons = Object.entries(result.byReason)
          .map(([r, c]) => `${r}: ${c}`)
          .join(', ');
        if (reasons) {
          summary += `Lý do: ${reasons}. `;
        }

        if (result.noShow > 0) {
          summary += `Trong đó có ${result.noShow} đơn khách không đến lấy (NO_SHOW).`;
        }

        return createToolSuccess(summary, { range, ...result } as unknown as Record<string, unknown>);
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : String(err);
        return createToolError(msg);
      }
    }
  );
}
