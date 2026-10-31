# HƯỚNG DẪN CẤU HÌNH VÀ CHẠY MCP SERVER BÁO CÁO CĂNG TIN

Server MCP `mcp-firestore` cung cấp **9 công cụ phân tích (1 công cụ tổng quát `query_orders` + 8 lối tắt)** giúp chủ căng tin hỏi đáp linh hoạt bằng tiếng Việt tự nhiên qua AI (Claude Desktop, Cursor, Claude Code, Windsurf).

Server hỗ trợ 2 cơ chế kết nối:
1. **Kết nối qua URL (HTTP SSE: `http://localhost:3000/sse`)** *(Khuyên dùng)*.
2. **Kết nối qua Command (stdio)**: AI client tự khởi động tiến trình Node.

---

## BƯỚC 1: KHỞI ĐỘNG SERVER

### Cách 1: Khởi động chế độ URL (HTTP SSE)
- **Windows (1-click)**: Click đúp vào file 👉 **[`run-sse.bat`](run-sse.bat)**
- **Terminal**:
  ```powershell
  cd E:\Data\AI_Files\mobile-app\tools\mcp-firestore
  npm run build
  npm run start:sse
  ```
  *(Server lắng nghe tại `http://localhost:3000/sse`, kiểm tra trạng thái tại `http://localhost:3000/health`)*.

### Cách 2: Khởi động chế độ Stdio (Command)
- Click đúp vào file 👉 **[`run-fixture.bat`](run-fixture.bat)** hoặc chạy `npm start`.

---

## BƯỚC 2: CẤU HÌNH AI CLIENT

### 1. Trong Cursor IDE (qua URL SSE)
1. Mở **Cursor Settings** -> **Features** -> **MCP Servers** -> Bấm **+ Add New MCP Server**.
2. Thiết lập:
   - **Name**: `canteen-report`
   - **Type**: Chọn `sse`
   - **URL**: `http://localhost:3000/sse`
3. Bấm **Add**. Cursor sẽ hiển thị chấm xanh (Connected) với đủ 9 tools.

### 2. Trong Claude Desktop
Mở file cấu hình `%APPDATA%\Claude\claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "canteen-report": {
      "command": "node",
      "args": ["E:/Data/AI_Files/mobile-app/tools/mcp-firestore/dist/index.js"],
      "env": {
        "DATA_SOURCE": "fixture",
        "TIMEZONE": "Asia/Ho_Chi_Minh",
        "MASK_PII": "true"
      }
    }
  }
}
```

### 3. Trong Claude Code (CLI)
```bash
claude mcp add canteen-report http://localhost:3000/sse
```

---

## BƯỚC 3: DANH SÁCH 9 CÔNG CỤ & CÂU HỎI MẪU

### 1. Công cụ tổng quát
- **`query_orders`**: Tự do ghép nhiều bộ lọc (`weekdays`, `daypart`, `slots`, `categories`, `dish_keys`) và nhóm theo tối đa 2 chiều (`day`, `week`, `weekday`, `daypart`, `slot`, `dish`, `category`, `cancel_reason`, `status`).
  - *"Món nào bán chạy nhất vào thứ Sáu?"*
  - *"Doanh thu từng thứ trong tuần khác nhau thế nào?"*
  - *"Khung chiều tối bán gì nhiều nhất?"*
  - *"Tỷ lệ hủy theo từng khung giờ?"*
  - *"Đồ uống đóng góp bao nhiêu doanh thu mỗi khung giờ?"*

### 2. Tám công cụ lối tắt
- **`get_sales_summary`**: Doanh thu, số đơn, số suất bán, so sánh kỳ trước.
  - *"Hôm qua bán được bao nhiêu?"*
  - *"Tuần này doanh thu so với tuần trước thế nào?"*
- **`get_top_items`**: Món bán chạy nhất (`best`) hoặc bán kém nhất (`worst`).
  - *"Món nào bán chạy nhất 4 tuần qua?"*
  - *"Món nào bán kém nhất tháng này?"*
- **`get_orders_by_slot`**: Khung giờ đông, khung giờ hay bị đầy.
  - *"Khung giờ nào đông nhất tuần này?"*
  - *"Khung giờ nào hay bị đầy nhất?"*
- **`get_cancellations`**: Đơn bị hủy, lý do hủy, ai hủy, khách không đến lấy (`NO_SHOW`).
  - *"Bao nhiêu đơn bị hủy và vì sao?"*
  - *"Có bao nhiêu khách không đến lấy tuần này?"*
- **`get_prep_time_stats`**: Thời gian chuẩn bị của bếp, độ trễ nhận món, ngày chậm bất thường.
  - *"Bếp chuẩn bị mất bao lâu, ngày nào chậm?"*
- **`get_unsold_items`**: Món ế, món bán hết sớm.
  - *"Món nào ế?"*
  - *"Hôm qua món nào hết suất sớm?"*
- **`forecast_tomorrow`**: Dự báo số suất chuẩn bị cho ngày mai theo thứ trong tuần.
  - *"Mai nên chuẩn bị bao nhiêu suất mỗi món?"*
- **`list_menu`**: Thực đơn trong ngày, lọc theo tag và khoảng giá.
  - *"Hôm nay có món chay dưới 30k không?"*
