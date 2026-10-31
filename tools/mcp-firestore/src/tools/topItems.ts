import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { z } from 'zod';
import { DataSource } from '../data/dataSource.js';
import { Config } from '../config.js';
import { resolveDateRange } from '../util/dates.js';
import { computeTopItems } from '../metrics/items.js';
import { createToolSuccess, createToolError } from '../util/format.js';

export function registerTopItemsTool(server: McpServer, dataSource: DataSource, config: Config) {
  server.tool(
    'get_top_items',
    'Dùng khi hỏi món bán chạy (best) hoặc món bán kém (worst) trong khoảng thời gian.',
    {
      date_from: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).optional().describe('Ngày bắt đầu (yyyy-MM-dd)'),
      date_to: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).optional().describe('Ngày kết thúc (yyyy-MM-dd)'),
      limit: z.number().min(1).max(20).optional().describe('Số lượng món cần lấy (1-20, mặc định 5)'),
      order: z.enum(['best', 'worst']).optional().describe('Thứ tự: "best" (bán chạy nhất) hoặc "worst" (bán kém nhất)'),
    },
    async ({ date_from, date_to, limit, order }) => {
      try {
        const range = resolveDateRange(date_from, date_to, config.timezone);
        const [orders, menuItems] = await Promise.all([
          dataSource.getOrders(range),
          dataSource.getMenuItems(range),
        ]);

        const safeOrder = order || 'best';
        const safeLimit = limit || 5;

        const items = computeTopItems(orders, menuItems, safeOrder, safeLimit);

        const orderText = safeOrder === 'best' ? 'bán chạy nhất' : 'bán kém nhất';
        let summary = `Danh sách ${items.length} món ${orderText} từ ${range.from} đến ${range.to}: `;
        if (items.length > 0) {
          summary += items.map((i) => `${i.name} (${i.portions} suất, ${i.sharePct}%)`).join(', ') + '.';
        } else {
          summary += 'Không có dữ liệu trong khoảng thời gian này.';
        }

        return createToolSuccess(summary, { range, order: safeOrder, items } as unknown as Record<string, unknown>);
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : String(err);
        return createToolError(msg);
      }
    }
  );
}
