import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { z } from 'zod';
import { DataSource } from '../data/dataSource.js';
import { Config } from '../config.js';
import { resolveDateRange } from '../util/dates.js';
import { computeUnsoldItems } from '../metrics/items.js';
import { createToolSuccess, createToolError, formatPercent } from '../util/format.js';

export function registerUnsoldTool(server: McpServer, dataSource: DataSource, config: Config) {
  server.tool(
    'get_unsold_items',
    'Dùng khi hỏi món ế, món bán hết sớm hoặc cần đánh giá để điều chỉnh tăng/giảm số lượng chuẩn bị mỗi ngày.',
    {
      date_from: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).optional().describe('Ngày bắt đầu (yyyy-MM-dd)'),
      date_to: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).optional().describe('Ngày kết thúc (yyyy-MM-dd)'),
      threshold: z
        .number()
        .min(0)
        .max(1)
        .optional()
        .describe('Ngưỡng tỷ lệ bán để coi là ế (0..1, mặc định 0.5 tức 50%)'),
    },
    async ({ date_from, date_to, threshold }) => {
      try {
        const range = resolveDateRange(date_from, date_to, config.timezone);
        const menuItems = await dataSource.getMenuItems(range);

        const safeThreshold = threshold !== undefined ? threshold : 0.5;
        const items = computeUnsoldItems(menuItems, safeThreshold);

        const unSoldList = items.filter((i) => i.status === 'ế');
        const soldOutList = items.filter((i) => i.status === 'bán hết sớm');

        let summary = `Đánh giá hiệu suất bán món từ ${range.from} đến ${range.to} (ngưỡng ${formatPercent(
          safeThreshold
        )}): `;

        if (unSoldList.length > 0) {
          summary += `Có ${unSoldList.length} món bị ế: ${unSoldList
            .map((i) => `${i.name} (${formatPercent(i.avgSellThrough)})`)
            .join(', ')}. `;
        } else {
          summary += 'Không có món nào bị ế. ';
        }

        if (soldOutList.length > 0) {
          summary += `Có ${soldOutList.length} món thường bán hết sớm: ${soldOutList
            .map((i) => `${i.name} (hết ${i.soldOutDays}/${i.days} ngày)`)
            .join(', ')}.`;
        }

        return createToolSuccess(
          summary,
          { range, threshold: safeThreshold, items } as unknown as Record<string, unknown>
        );
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : String(err);
        return createToolError(msg);
      }
    }
  );
}
