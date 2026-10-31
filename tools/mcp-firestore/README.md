# Canteen Report MCP Server

Server MCP chỉ-đọc dựa trên **SQLite trong bộ nhớ** (`sql.js`), cung cấp dữ liệu báo cáo và phân tích căng tin cho AI Assistant (Claude Desktop, Claude Code, Cursor, Windsurf, Antigravity) từ Google Cloud Firestore hoặc dữ liệu mẫu (`fixture`).

## Kiến trúc & Nguyên tắc Hoạt động

1. **Nạp dữ liệu vào SQLite trong bộ nhớ**: Đọc dữ liệu chỉ-đọc từ Firestore hoặc `docs/data/seed-history.json`, chuẩn hóa, che PII (thông tin cá nhân) và nạp vào SQLite tạm.
2. **View công thức cố định**: Toàn bộ công thức chuẩn (doanh thu, đơn hoàn thành, tỷ lệ hủy, thời gian bếp chuẩn bị, tỷ lệ lấp đầy khung giờ, tỷ lệ bán hết) được chốt cố định trong các bảng và view SQL (`v_daily`, `v_item_daily`, `v_slot_daily`, `v_item_sellthrough`, ...).
3. **Thực thi SQL an toàn 2 lớp**:
   - **Lớp 1 (Guard)**: Chỉ chấp nhận 1 câu `SELECT` hoặc `WITH`, chặn mọi từ khóa thay đổi dữ liệu (`INSERT`, `UPDATE`, `DELETE`, `DROP`, `PRAGMA`...).
   - **Lớp 2 (Runner)**: Luôn bật `PRAGMA query_only = ON`, chạy trong worker thread với giới hạn thời gian (timeout 5s) và giới hạn dòng kết quả tối đa (200 dòng).
4. **Không can thiệp ghi**: Kết nối Firestore 100% chỉ-đọc; không có bất kỳ lệnh sửa/xóa dữ liệu nào.

---

## 4 Công cụ MCP Cốt lõi

1. **`get_schema`**: Xem toàn bộ lược đồ CSDL SQLite chỉ-đọc (các bảng `orders`, `order_items`, `menu_items`, `time_slots`, các view tính sẵn công thức `v_*`, các quy ước và câu lệnh mẫu). AI luôn gọi công cụ này đầu tiên trước khi viết câu truy vấn.
2. **`run_sql`**: Chạy một câu `SELECT` (SQLite) trên bản sao dữ liệu căng tin theo khoảng ngày `date_from` đến `date_to`. AI tự do ghép nhóm, lọc, so sánh để trả lời mọi câu hỏi phân tích kinh doanh.
3. **`forecast_tomorrow`**: Dự báo số suất cần chuẩn bị cho ngày mai (hoặc một ngày tương lai) theo từng món, tính theo trung bình các ngày cùng thứ trong 4 tuần gần nhất + tỷ lệ dự phòng.
4. **`find_slow_days`**: Sử dụng phương sai và độ lệch chuẩn (`mean + 1.5 * stdDev`) để phát hiện những ngày bếp chuẩn bị món chậm bất thường.

---

## Cài đặt & Khởi chạy

### 1. Cài đặt thư viện
```bash
npm install
```

### 2. Cấu hình môi trường
Sao chép `.env.example` thành `.env` (hoặc cấu hình trực tiếp trong client MCP):
- `DATA_SOURCE`: `fixture` (dữ liệu mẫu) hoặc `firestore` (kết nối trực tiếp).
- `SERVICE_ACCOUNT_PATH`: Đường dẫn tới file `serviceAccount.json` khi dùng `firestore`.
- `TIMEZONE`: `Asia/Ho_Chi_Minh` (mặc định).
- `MASK_PII`: `true` (mặc định, che thông tin cá nhân khách hàng).
- `FIXTURE_PATH`: Đường dẫn tới file `seed-history.json` khi dùng `fixture`.
- `SQL_CACHE_SECONDS`: `60` (thời gian lưu cache bản sao SQLite, 0 để tắt).
- `SQL_MAX_ROWS`: `200` (giới hạn số dòng tối đa).
- `SQL_TIMEOUT_MS`: `5000` (thời gian tối đa thực thi SQL).

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
# Server sẽ lắng nghe tại: http://localhost:3000/sse (Health check: http://localhost:3000/health)
```

#### Cách 2: Chế độ Stdio (Command)
```bash
npm start
# Hoặc click đúp file run-fixture.bat
```

---

## Cấu hình AI Client

### 1. Cursor IDE (qua URL SSE)
- Settings -> MCP -> Add new MCP server:
  - Name: `canteen-report`
  - Type: `sse`
  - URL: `http://localhost:3000/sse`

### 2. Claude Desktop (`claude_desktop_config.json`)
```json
{
  "mcpServers": {
    "canteen-report": {
      "command": "node",
      "args": ["E:/Data/AI_Files/mobile-app/tools/mcp-firestore/dist/index.js"],
      "env": {
        "DATA_SOURCE": "fixture",
        "TIMEZONE": "Asia/Ho_Chi_Minh"
      }
    }
  }
}
```

### 3. Claude Code (CLI)
```bash
claude mcp add canteen-report http://localhost:3000/sse
```

Xem hướng dẫn chi tiết và ví dụ câu hỏi tại: **[HUONG_DAN_CHAY.md](HUONG_DAN_CHAY.md)**.
