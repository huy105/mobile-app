import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { Config } from '../config.js';
import { getSchemaDocumentation } from '../sql/schema.js';
import { getToday } from '../util/dates.js';

export function registerGetSchemaTool(server: McpServer, config: Config) {
  const getDoc = () => {
    const today = getToday(config.timezone);
    return getSchemaDocumentation(today);
  };

  // Đăng ký resource canteen://schema
  try {
    server.resource(
      'canteen-schema',
      'canteen://schema',
      async () => ({
        contents: [
          {
            uri: 'canteen://schema',
            mimeType: 'text/markdown',
            text: getDoc(),
          },
        ],
      })
    );
  } catch {
    // Không làm gián đoạn nếu client không hỗ trợ resource
  }

  // Đăng ký tool get_schema
  server.tool(
    'get_schema',
    'Xem lược đồ CSDL SQLite chỉ-đọc: bảng, cột, view tính sẵn công thức và các câu SQL mẫu. Luôn gọi đầu tiên trước khi dùng run_sql.',
    {},
    async () => {
      const text = getDoc();
      return {
        content: [
          {
            type: 'text',
            text,
          },
        ],
      };
    }
  );
}

