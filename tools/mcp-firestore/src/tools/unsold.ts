import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { z } from 'zod';
import { DataSource } from '../data/dataSource.js';
import { Config } from '../config.js';
import { resolveDateRange, getIsoWeekday } from '../util/dates.js';
import { computeUnsoldItems } from '../metrics/items.js';
import { createToolSuccess, createToolError, formatPercent } from '../util/format.js';

export function registerUnsoldTool(server: McpServer, dataSource: DataSource, config: Config) {
  server.tool(
    'get_unsold_items',
    'Dùng khi hỏi món ế, món bán hết sớm hoặc cần đánh giá để điều chỉnh tăng/giảm số lượng chuẩn bị mỗi ngày.',
    {
      date_from: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).optional().describe('Ngày bắt đầu (yyyy-MM-dd)'),
      date_to: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).optional().describe('Ngày kết thúc (yyyy-MM-dd)'),
      weekdays: z.array(z.number().int().min(1).max(7)).optional().describe('Thứ trong tuần (1 = Thứ Hai, 7 = Chủ nhật)'),
      categories: z.array(z.string()).optional().describe('Danh sách nhóm món'),
      dish_keys: z.array(z.string()).optional().describe('Danh sách mã món dishKey'),
      daypart: z.enum(['lunch', 'evening']).optional().describe('Không áp dụng cho menuItems (sẽ bị bỏ qua)'),
      slots: z.array(z.string()).optional().describe('Không áp dụng cho menuItems (sẽ bị bỏ qua)'),
      threshold: z
        .number()
        .min(0)
        .max(1)
        .optional()
        .describe('Ngưỡng tỷ lệ bán để coi là ế (0..1, mặc định 0.5 tức 50%)'),
    },
    async (args) => {
      try {
        const range = resolveDateRange(args.date_from, args.date_to, config.timezone);
        const rawMenuItems = await dataSource.getMenuItems(range);

        const ignoredFilters: string[] = [];
        if (args.daypart) ignoredFilters.push('daypart');
        if (args.slots && args.slots.length > 0) ignoredFilters.push('slots');

        // Lọc menuItems theo weekdays, categories, dish_keys
        const filteredMenuItems = rawMenuItems.filter((m) => {
          if (args.weekdays && args.weekdays.length > 0) {
            const w = getIsoWeekday(m.date);
            if (!args.weekdays.includes(w)) return false;
          }
          if (args.categories && args.categories.length > 0) {
            if (!args.categories.includes(m.category || '')) return false;
          }
          if (args.dish_keys && args.dish_keys.length > 0) {
            const key = m.dishKey || m.name;
            if (!args.dish_keys.includes(key) && !args.dish_keys.includes(m.name)) return false;
          }
          return true;
        });

        const safeThreshold = args.threshold !== undefined ? args.threshold : 0.5;
        const items = computeUnsoldItems(filteredMenuItems, safeThreshold);

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

        const appliedFilters: Record<string, unknown> = { range };
        if (args.weekdays) appliedFilters.weekdays = args.weekdays;
        if (args.categories) appliedFilters.categories = args.categories;
        if (args.dish_keys) appliedFilters.dish_keys = args.dish_keys;

        return createToolSuccess(
          summary,
          {
            appliedFilters,
            ignoredFilters: ignoredFilters.length > 0 ? ignoredFilters : undefined,
            threshold: safeThreshold,
            items,
          } as unknown as Record<string, unknown>
        );
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : String(err);
        return createToolError(msg);
      }
    }
  );
}
