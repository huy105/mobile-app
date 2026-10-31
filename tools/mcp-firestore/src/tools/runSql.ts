import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { z } from 'zod';
import { DataSource } from '../data/dataSource.js';
import { Config } from '../config.js';
import { resolveDateRange, getToday, addDays } from '../util/dates.js';
import { validateSql } from '../sql/guard.js';
import { sharedDatabaseCache } from '../sql/cache.js';
import { executeQueryInWorker } from '../sql/runner.js';
import { createToolSuccess, createToolError } from '../util/format.js';

export function registerRunSqlTool(server: McpServer, dataSource: DataSource, config: Config) {
  server.tool(
    'run_sql',
    'Chạy một câu SELECT (SQLite) trên bản sao dữ liệu căng tin; gọi get_schema trước để biết bảng và view; ưu tiên truy vấn trên các view v_* vì đã chốt công thức; dữ liệu chỉ gồm khoảng ngày date_from đến date_to.',
    {
      sql: z.string().max(4000).describe('Câu lệnh SQL SELECT hoặc WITH (tối đa 4000 ký tự)'),
      date_from: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).optional().describe('Ngày bắt đầu nạp dữ liệu (yyyy-MM-dd, mặc định 30 ngày trước)'),
      date_to: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).optional().describe('Ngày kết thúc nạp dữ liệu (yyyy-MM-dd, mặc định hôm nay)'),
    },
    async ({ sql, date_from, date_to }) => {
      // 1. Kiểm tra an toàn SQL bằng Guard
      const guardResult = validateSql(sql);
      if (!guardResult.valid || !guardResult.cleanSql) {
        return createToolError(guardResult.error || 'Câu truy vấn SQL không hợp lệ.');
      }

      try {
        // 2. Xác định khoảng ngày nạp dữ liệu (mặc định 30 ngày gần nhất)
        const today = getToday(config.timezone);
        const defaultFrom = addDays(today, -29);

        const range = resolveDateRange(
          date_from || defaultFrom,
          date_to || today,
          config.timezone
        );

        // 3. Lấy hoặc nạp database từ Cache
        const db = await sharedDatabaseCache.getDatabase(dataSource, range, config);

        // 4. Chạy câu lệnh SQL qua Runner
        const result = await executeQueryInWorker(
          db,
          guardResult.cleanSql,
          config.sqlMaxRows,
          config.sqlTimeoutMs
        );

        let note: string | undefined;
        if (result.rowCount === 0) {
          note = 'Không có dòng dữ liệu nào khớp. Hãy kiểm tra lại khoảng ngày date_from/date_to hoặc điều kiện lọc trong mệnh đề WHERE.';
        } else if (result.truncated) {
          note = `Kết quả đã được giới hạn ở ${config.sqlMaxRows} dòng để tránh tràn ngữ cảnh.`;
        }

        let summary = `Đã thực thi câu truy vấn trên dữ liệu từ ${range.from} đến ${range.to} (${config.dataSource}): trả về ${result.rows.length} dòng`;
        if (result.truncated) {
          summary += ` (tổng cộng có ${result.rowCount} dòng, đã cắt bớt)`;
        }
        summary += '.';

        return createToolSuccess(summary, {
          columns: result.columns,
          rows: result.rows,
          rowCount: result.rowCount,
          truncated: result.truncated,
          loadedRange: range,
          source: config.dataSource,
          note,
        });
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : String(err);
        return createToolError(
          `Lỗi thực thi SQL: ${msg}. Bạn có thể gọi tool get_schema để xem lại cấu trúc bảng, view và các câu lệnh mẫu.`
        );
      }
    }
  );
}
