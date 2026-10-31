import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { z } from 'zod';
import { DataSource } from '../data/dataSource.js';
import { Config } from '../config.js';
import { getToday, addDays } from '../util/dates.js';
import { computeForecast } from '../metrics/forecast.js';
import { createToolSuccess, createToolError } from '../util/format.js';

const WEEKDAY_NAMES = [
  'Chủ nhật',
  'Thứ hai',
  'Thứ ba',
  'Thứ tư',
  'Thứ năm',
  'Thứ sáu',
  'Thứ bảy',
];

export function registerForecastTomorrowTool(
  server: McpServer,
  dataSource: DataSource,
  config: Config
) {
  server.tool(
    'forecast_tomorrow',
    'Dùng khi hỏi mai (hoặc một ngày tương lai) nên chuẩn bị bao nhiêu suất mỗi món; tính toán theo các ngày cùng thứ trong 4 tuần gần nhất.',
    {
      date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).optional().describe('Ngày cần dự báo (yyyy-MM-dd, mặc định là ngày mai)'),
      buffer_pct: z
        .number()
        .min(0)
        .max(50)
        .optional()
        .describe('Phần trăm dự phòng phát sinh (0-50%, mặc định 10%)'),
      categories: z.array(z.string()).optional().describe('Chỉ dự báo các nhóm món này'),
      dish_keys: z.array(z.string()).optional().describe('Chỉ dự báo các món này'),
    },
    async (args) => {
      try {
        const today = getToday(config.timezone);
        const forDate = args.date || addDays(today, 1);
        const safeBuffer = args.buffer_pct !== undefined ? args.buffer_pct : 10;

        // Đọc 5 tuần gần nhất trước ngày dự báo (35 ngày)
        const fromDate = addDays(forDate, -35);
        const toDate = addDays(forDate, -1);

        const rawMenuItems = await dataSource.getMenuItems({ from: fromDate, to: toDate });

        const filteredMenuItems = rawMenuItems.filter((m) => {
          if (args.categories && args.categories.length > 0) {
            if (!args.categories.includes(m.category || '')) return false;
          }
          if (args.dish_keys && args.dish_keys.length > 0) {
            const key = m.dishKey || m.name;
            if (!args.dish_keys.includes(key) && !args.dish_keys.includes(m.name)) return false;
          }
          return true;
        });

        const result = computeForecast(filteredMenuItems, forDate, safeBuffer);
        const weekdayName = WEEKDAY_NAMES[result.weekday] || '';

        const validForecasts = result.items.filter((i) => i.forecast !== null);
        let summary = `Dự báo số suất cần chuẩn bị cho ngày ${forDate} (${weekdayName}) với hệ số dự phòng ${safeBuffer}%: `;

        if (validForecasts.length > 0) {
          summary += validForecasts
            .map((i) => `${i.name}: ${i.forecast} suất`)
            .join(', ') + '.';
        } else {
          summary += 'Chưa đủ dữ liệu lịch sử các tuần trước để đưa ra dự báo.';
        }

        return createToolSuccess(summary, result as unknown as Record<string, unknown>);
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : String(err);
        return createToolError(msg);
      }
    }
  );
}
