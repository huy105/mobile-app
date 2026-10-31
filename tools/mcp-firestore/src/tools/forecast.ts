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

export function registerForecastTool(server: McpServer, dataSource: DataSource, config: Config) {
  server.tool(
    'forecast_tomorrow',
    'Dùng khi hỏi mai nên chuẩn bị bao nhiêu suất mỗi món hoặc dự báo số lượng theo thứ trong tuần.',
    {
      date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).optional().describe('Ngày cần dự báo (yyyy-MM-dd, mặc định là ngày mai)'),
      buffer_pct: z
        .number()
        .min(0)
        .max(50)
        .optional()
        .describe('Phần trăm dự phòng phát sinh (0-50%, mặc định 10%)'),
    },
    async ({ date, buffer_pct }) => {
      try {
        const today = getToday(config.timezone);
        const forDate = date || addDays(today, 1);
        const safeBuffer = buffer_pct !== undefined ? buffer_pct : 10;

        // Đọc 5 tuần gần nhất trước ngày dự báo (35 ngày)
        const fromDate = addDays(forDate, -35);
        const toDate = addDays(forDate, -1);

        const menuItems = await dataSource.getMenuItems({ from: fromDate, to: toDate });

        const result = computeForecast(menuItems, forDate, safeBuffer);
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
