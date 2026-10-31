import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { z } from 'zod';
import { DataSource } from '../data/dataSource.js';
import { Config } from '../config.js';
import { filterZodShape, applyOrderFilters } from '../util/filters.js';
import { computeOrderMetrics } from '../metrics/engine.js';
import { createToolSuccess, createToolError } from '../util/format.js';

export function registerTopItemsTool(server: McpServer, dataSource: DataSource, config: Config) {
  server.tool(
    'get_top_items',
    'Lối tắt hỏi món bán chạy (best) hoặc món bán kém (worst) trong khoảng thời gian.',
    {
      ...filterZodShape,
      limit: z.number().min(1).max(20).optional().describe('Số lượng món cần lấy (1-20, mặc định 5)'),
      order: z.enum(['best', 'worst']).optional().describe('Thứ tự: "best" (bán chạy nhất) hoặc "worst" (bán kém nhất)'),
    },
    async (args) => {
      try {
        const timeSlots = await dataSource.getTimeSlots();
        const filterCtx = applyOrderFilters([], args, timeSlots, config.timezone);
        const [ordersRaw, menuItems] = await Promise.all([
          dataSource.getOrders(filterCtx.appliedFilters.range),
          dataSource.getMenuItems(filterCtx.appliedFilters.range),
        ]);

        const currentCtx = applyOrderFilters(ordersRaw, args, timeSlots, config.timezone);
        const safeOrder = args.order || 'best';
        const safeLimit = Math.max(1, Math.min(args.limit || 5, 20));

        // Gọi engine để tính theo dish
        const engineRes = computeOrderMetrics(
          currentCtx.orders,
          currentCtx.appliedFilters,
          currentCtx.hasLineItemFilter,
          timeSlots,
          {
            groupBy: ['dish'],
            metrics: ['portions', 'revenue'],
            sortBy: 'portions',
            sortDir: safeOrder === 'best' ? 'desc' : 'asc',
            limit: 100,
          }
        );

        const totalPortions = (engineRes.totals.portions as number) ?? 0;

        // Map dishKey -> menu metadata (name, category)
        const menuDishMap = new Map<string, { name: string; category: string }>();
        for (const m of menuItems) {
          const key = m.dishKey || m.name;
          if (!menuDishMap.has(key)) {
            menuDishMap.set(key, { name: m.name, category: m.category || 'Không rõ' });
          }
        }

        const itemMap = new Map<
          string,
          { dishKey: string; name: string; category: string; portions: number; revenue: number }
        >();

        for (const r of engineRes.rows) {
          const key = String(r.key.dish);
          const meta = menuDishMap.get(key) || { name: key, category: 'Không rõ' };
          itemMap.set(key, {
            dishKey: key,
            name: meta.name,
            category: meta.category,
            portions: (r.portions as number) ?? 0,
            revenue: (r.revenue as number) ?? 0,
          });
        }

        if (safeOrder === 'worst') {
          for (const [key, meta] of menuDishMap.entries()) {
            if (!itemMap.has(key)) {
              itemMap.set(key, {
                dishKey: key,
                name: meta.name,
                category: meta.category,
                portions: 0,
                revenue: 0,
              });
            }
          }
        }

        const items = Array.from(itemMap.values()).map((entry) => ({
          dishKey: entry.dishKey,
          name: entry.name,
          category: entry.category,
          portions: entry.portions,
          revenue: entry.revenue,
          sharePct: totalPortions > 0 ? Math.round((entry.portions / totalPortions) * 1000) / 10 : 0,
        }));

        if (safeOrder === 'best') {
          items.sort((a, b) => b.portions - a.portions || b.revenue - a.revenue);
        } else {
          items.sort((a, b) => a.portions - b.portions || a.revenue - b.revenue);
        }

        const topItems = items.slice(0, safeLimit);
        const orderText = safeOrder === 'best' ? 'bán chạy nhất' : 'bán kém nhất';
        let summary = `Danh sách ${topItems.length} món ${orderText} từ ${currentCtx.appliedFilters.range.from} đến ${currentCtx.appliedFilters.range.to}: `;
        if (topItems.length > 0) {
          summary += topItems.map((i) => `${i.name} (${i.portions} suất, ${i.sharePct}%)`).join(', ') + '.';
        } else {
          summary += 'Không có dữ liệu trong khoảng thời gian này.';
        }

        return createToolSuccess(summary, {
          appliedFilters: currentCtx.appliedFilters,
          order: safeOrder,
          items: topItems,
          skipped: engineRes.skipped,
        } as unknown as Record<string, unknown>);
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : String(err);
        return createToolError(msg);
      }
    }
  );
}
