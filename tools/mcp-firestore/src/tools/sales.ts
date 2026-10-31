import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { z } from 'zod';
import { DataSource } from '../data/dataSource.js';
import { Config } from '../config.js';
import { resolveDateRange, getPreviousPeriod } from '../util/dates.js';
import { computeSalesSummary } from '../metrics/sales.js';
import { createToolSuccess, createToolError, formatCurrencyVND } from '../util/format.js';

export function registerSalesTool(server: McpServer, dataSource: DataSource, config: Config) {
  server.tool(
    'get_sales_summary',
    'Dùng khi hỏi doanh thu, số đơn, số suất bán, giá trị đơn trung bình trong khoảng thời gian hoặc so sánh với kỳ trước.',
    {
      date_from: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).optional().describe('Ngày bắt đầu (yyyy-MM-dd)'),
      date_to: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).optional().describe('Ngày kết thúc (yyyy-MM-dd)'),
      group_by: z
        .enum(['none', 'day', 'week', 'category'])
        .optional()
        .describe('Gom nhóm số liệu theo ngày (day), tuần (week), loại món (category) hoặc không (none)'),
    },
    async ({ date_from, date_to, group_by }) => {
      try {
        const range = resolveDateRange(date_from, date_to, config.timezone);
        const prevRange = getPreviousPeriod(range);

        const [currentOrders, prevOrders] = await Promise.all([
          dataSource.getOrders(range),
          dataSource.getOrders(prevRange),
        ]);

        const result = computeSalesSummary(currentOrders, range, prevOrders, group_by || 'none');

        let summary = `Từ ${range.from} đến ${range.to}, tổng doanh thu đạt ${formatCurrencyVND(
          result.revenue
        )} từ ${result.orders} đơn hoàn thành (${result.portions} suất).`;

        if (result.changePctVsPrevious !== null) {
          const sign = result.changePctVsPrevious > 0 ? '+' : '';
          summary += ` So với kỳ liền trước cùng số ngày (${prevRange.from} đến ${prevRange.to}), doanh thu thay đổi ${sign}${result.changePctVsPrevious}%.`;
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
