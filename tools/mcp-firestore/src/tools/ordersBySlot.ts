import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { z } from 'zod';
import { DataSource } from '../data/dataSource.js';
import { Config } from '../config.js';
import { resolveDateRange } from '../util/dates.js';
import { computeOrdersBySlot } from '../metrics/slots.js';
import { createToolSuccess, createToolError, formatPercent } from '../util/format.js';

export function registerOrdersBySlotTool(server: McpServer, dataSource: DataSource, config: Config) {
  server.tool(
    'get_orders_by_slot',
    'Dùng khi hỏi khung giờ đông, khung giờ hay đầy hoặc tỷ lệ lấp đầy đơn hàng theo từng khung giờ.',
    {
      date_from: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).optional().describe('Ngày bắt đầu (yyyy-MM-dd)'),
      date_to: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).optional().describe('Ngày kết thúc (yyyy-MM-dd)'),
    },
    async ({ date_from, date_to }) => {
      try {
        const range = resolveDateRange(date_from, date_to, config.timezone);
        const [orders, timeSlots] = await Promise.all([
          dataSource.getOrders(range),
          dataSource.getTimeSlots(),
        ]);

        const result = computeOrdersBySlot(orders, timeSlots);

        let summary = `Thống kê khung giờ nhận đồ từ ${range.from} đến ${range.to}: `;
        if (result.busiest) {
          const busySlot = result.slots.find((s) => s.slotLabel === result.busiest);
          summary += `Khung giờ đông nhất là "${result.busiest}" với tỷ lệ lấp đầy ${formatPercent(
            busySlot?.fillRate || 0
          )} (${busySlot?.orders} đơn). `;
        }
        if (result.oftenFull.length > 0) {
          summary += `Các khung giờ từng chạm ngưỡng tối đa (đầy): ${result.oftenFull.join(', ')}.`;
        } else {
          summary += 'Chưa có khung giờ nào bị quá tải hoặc đạt mức tối đa.';
        }

        return createToolSuccess(summary, { range, ...result } as unknown as Record<string, unknown>);
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : String(err);
        return createToolError(msg);
      }
    }
  );
}
