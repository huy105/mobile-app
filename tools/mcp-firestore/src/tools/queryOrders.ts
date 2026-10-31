import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { z } from 'zod';
import { DataSource } from '../data/dataSource.js';
import { Config } from '../config.js';
import { filterZodShape, applyOrderFilters } from '../util/filters.js';
import {
  computeOrderMetrics,
  VALID_DIMENSIONS,
  VALID_METRICS,
  Dimension,
  Metric,
} from '../metrics/engine.js';
import { createToolSuccess, createToolError } from '../util/format.js';

export function registerQueryOrdersTool(server: McpServer, dataSource: DataSource, config: Config) {
  server.tool(
    'query_orders',
    'Công cụ tổng quát: dùng khi câu hỏi cần lọc hoặc nhóm theo chiều không có sẵn ở tool lối tắt (ghép thứ, buổi, nhóm món, khung giờ, lý do hủy).',
    {
      ...filterZodShape,
      group_by: z
        .array(z.enum(VALID_DIMENSIONS))
        .max(2)
        .optional()
        .describe(
          `Chiều nhóm (tối đa 2 chiều). Các giá trị: ${VALID_DIMENSIONS.join(', ')}`
        ),
      metrics: z
        .array(z.enum(VALID_METRICS))
        .optional()
        .describe(`Các chỉ số cần lấy. Mặc định: revenue, orders, portions. Các giá trị: ${VALID_METRICS.join(', ')}`),
      sort_by: z.enum(VALID_METRICS).optional().describe('Chỉ số dùng để sắp xếp kết quả (mặc định là chỉ số đầu tiên)'),
      sort_dir: z.enum(['desc', 'asc']).optional().describe('Chiều sắp xếp: desc (giảm dần) hoặc asc (tăng dần)'),
      limit: z.number().int().min(1).max(100).optional().describe('Giới hạn số dòng trả về (1-100, mặc định 20)'),
    },
    async (args) => {
      try {
        const timeSlots = await dataSource.getTimeSlots();

        // Tạm giải date range để đọc từ data source
        const initialFiltered = applyOrderFilters(
          [],
          { date_from: args.date_from, date_to: args.date_to },
          timeSlots,
          config.timezone
        );

        const rawOrders = await dataSource.getOrders(initialFiltered.appliedFilters.range);

        // Áp dụng bộ lọc hoàn chỉnh (weekdays, daypart, slots, categories, dish_keys)
        const filterCtx = applyOrderFilters(rawOrders, args, timeSlots, config.timezone);

        const result = computeOrderMetrics(
          filterCtx.orders,
          filterCtx.appliedFilters,
          filterCtx.hasLineItemFilter,
          timeSlots,
          {
            groupBy: args.group_by as Dimension[],
            metrics: args.metrics as Metric[],
            sortBy: args.sort_by as Metric,
            sortDir: args.sort_dir,
            limit: args.limit,
          }
        );

        let summary = `Truy vấn dữ liệu từ ${result.appliedFilters.range.from} đến ${result.appliedFilters.range.to}`;
        if (result.appliedFilters.daypart) summary += ` (buổi: ${result.appliedFilters.daypart})`;
        if (result.appliedFilters.weekdays) summary += ` (thứ: ${result.appliedFilters.weekdays.join(', ')})`;
        if (result.groupBy.length > 0) summary += ` nhóm theo ${result.groupBy.join(' + ')}`;
        summary += `: tìm thấy ${result.rowCount} dòng kết quả. Tổng doanh thu: ${result.totals.revenue ?? 0}đ (${result.totals.orders ?? 0} đơn, ${result.totals.portions ?? 0} suất).`;

        return createToolSuccess(summary, result as unknown as Record<string, unknown>);
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : String(err);
        return createToolError(msg);
      }
    }
  );
}
