import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { z } from 'zod';
import { DataSource } from '../data/dataSource.js';
import { Config } from '../config.js';
import { getToday } from '../util/dates.js';
import { computeListMenu } from '../metrics/menu.js';
import { createToolSuccess, createToolError, formatCurrencyVND } from '../util/format.js';

export function registerMenuTool(server: McpServer, dataSource: DataSource, config: Config) {
  server.tool(
    'list_menu',
    'Dùng khi hỏi thực đơn một ngày, lọc theo tag (vegetarian, filling, quick, healthy, drink) hoặc theo mức giá tối đa.',
    {
      date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).optional().describe('Ngày xem thực đơn (yyyy-MM-dd, mặc định là hôm nay)'),
      tags: z
        .array(z.string())
        .optional()
        .describe('Danh sách tag cần khớp tất cả (vegetarian, filling, quick, healthy, drink)'),
      max_price: z.number().positive().optional().describe('Mức giá tối đa (VND)'),
      only_available: z
        .boolean()
        .optional()
        .describe('Chỉ lấy món còn hàng và còn suất (mặc định true)'),
    },
    async ({ date, tags, max_price, only_available }) => {
      try {
        const queryDate = date || getToday(config.timezone);
        const menuItems = await dataSource.getMenuItems({ from: queryDate, to: queryDate });

        const result = computeListMenu(menuItems, queryDate, {
          tags,
          maxPrice: max_price,
          onlyAvailable: only_available !== false,
        });

        let summary = '';
        if (result.items.length === 0) {
          summary = `Không có món nào khớp với điều kiện tìm kiếm trong ngày ${queryDate}. Bạn có thể thử nới rộng mức giá tối đa hoặc giảm bớt các tiêu chí lọc thẻ tags.`;
        } else {
          summary = `Thực đơn ngày ${queryDate} có ${result.items.length} món phù hợp: `;
          summary += result.items
            .map((i) => {
              const remainStr = i.remaining !== null ? ` (còn ${i.remaining} suất)` : '';
              return `${i.name} - ${formatCurrencyVND(i.price)}${remainStr}`;
            })
            .join(', ') + '.';
        }

        return createToolSuccess(summary, result as unknown as Record<string, unknown>);
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : String(err);
        return createToolError(msg);
      }
    }
  );
}
