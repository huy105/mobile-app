# Canteen Report MCP Server

Server MCP chỉ-đọc (stdio) cung cấp dữ liệu báo cáo căng tin cho AI Assistant (Claude Desktop, Claude Code, Cursor, Antigravity) từ Google Firestore hoặc dữ liệu mẫu (`fixture`).

## Cài đặt & Khởi chạy

### 1. Cài đặt thư viện
```bash
npm install
```

### 2. Cấu hình môi trường
Sao chép `.env.example` thành `.env` (hoặc truyền qua cấu hình MCP client):
- `DATA_SOURCE`: `fixture` (dữ liệu mẫu) hoặc `firestore` (kết nối trực tiếp).
- `SERVICE_ACCOUNT_PATH`: Đường dẫn tới file `serviceAccount.json` khi dùng `firestore`.
- `TIMEZONE`: `Asia/Ho_Chi_Minh` (mặc định).
- `MASK_PII`: `true` (mặc định, che thông tin cá nhân khách hàng).
- `FIXTURE_PATH`: Đường dẫn tới file `seed-history.json` khi dùng `fixture`.

### 3. Build & Test
```bash
npm run build
npm test
```

### 4. Khởi chạy Server

#### Cách 1: Chế độ URL (HTTP SSE) - Khuyên dùng
```bash
npm run start:sse
# Hoặc click đúp file run-sse.bat
# Server sẽ lắng nghe tại: http://localhost:3000/sse
```

#### Cách 2: Chế độ Stdio (Command)
```bash
npm start
# Hoặc click đúp file run-fixture.bat
```

### 5. Cấu hình AI Client qua URL

#### Cursor IDE:
- Settings -> MCP -> Add new MCP server:
  - Name: `canteen-report`
  - Type: `sse`
  - URL: `http://localhost:3000/sse`

#### Claude Desktop / Windsurf (`claude_desktop_config.json`):
```json
{
  "mcpServers": {
    "canteen-report": {
      "url": "http://localhost:3000/sse"
    }
  }
}
```

#### Claude Code (CLI):
```bash
claude mcp add canteen-report http://localhost:3000/sse
```

Xem chi tiết tại: **[HUONG_DAN_CHAY.md](HUONG_DAN_CHAY.md)**.

