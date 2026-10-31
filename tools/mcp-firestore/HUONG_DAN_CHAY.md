# HƯỚNG DẪN CẤU HÌNH VÀ CHẠY MCP SERVER BÁO CÁO CĂNG TIN

Server MCP `mcp-firestore` sử dụng **SQLite trong bộ nhớ** với **4 công cụ cốt lõi**, cho phép AI Assistant (Claude Desktop, Cursor, Claude Code, Windsurf) trả lời mọi câu hỏi về doanh thu, suất ăn, khung giờ, món bán chạy/ế, tỷ lệ hủy, thời gian chuẩn bị và dự báo bằng tiếng Việt.

---

## 4 CÔNG CỤ CỐT LÕI

| Tool | Chức năng | Khi nào nên dùng |
|---|---|---|
| **`get_schema`** | Xem tài liệu cấu trúc bảng, view công thức và câu SQL mẫu | **Luôn gọi đầu tiên** trong hội thoại mới trước khi truy vấn SQL |
| **`run_sql`** | Chạy 1 câu `SELECT` (SQLite) chỉ-đọc với bảo vệ Guard 2 lớp | Mọi câu hỏi về doanh thu, món ăn, khung giờ, hủy đơn, thời gian chuẩn bị... |
| **`forecast_tomorrow`** | Dự báo số suất món ăn cho ngày mai theo ngày cùng thứ | Hỏi *"Mai nên chuẩn bị bao nhiêu suất mỗi món?"* |
| **`find_slow_days`** | Phát hiện ngày bếp chuẩn bị chậm bất thường (`mean + 1.5 * stdDev`) | Hỏi *"Ngày nào bếp chậm bất thường?"* |

---

## BƯỚC 1: KHỞI ĐỘNG SERVER

### Cách 1: Khởi động chế độ URL (HTTP SSE) - Khuyên dùng
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
3. Bấm **Add**. Cursor sẽ hiển thị chấm xanh (Connected) với 4 tools.

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

## BƯỚC 3: CÁC CÂU HỎI MẪU CHO CHỦ CĂNG TIN

Chủ quán có thể hỏi trực tiếp bằng tiếng Việt tự nhiên:

### 1. Doanh thu & Tăng trưởng
- *"Hôm qua bán được bao nhiêu tiền và bao nhiêu đơn?"*
- *"Doanh thu tuần này so với tuần trước thế nào?"*
- *"Doanh thu các thứ trong tuần khác nhau ra sao?"*

### 2. Món ăn & Xu hướng
- *"Món nào bán chạy nhất vào thứ Sáu?"*
- *"Món nào ế, có tỷ lệ bán thấp nhất?"*
- *"Hôm nay có món chay nào dưới 30k còn suất không?"*
- *"Đồ uống đóng góp bao nhiêu doanh thu trong mỗi khung giờ?"*

### 3. Khung giờ & Vận hành
- *"Khung giờ nào hay bị đầy nhất?"*
- *"Khung chiều tối có đông không, có đáng mở tiếp không?"*
- *"Bếp chuẩn bị món mất bao lâu ở từng khung giờ?"*
- *"Những ngày nào bếp chuẩn bị chậm bất thường?"*

### 4. Đơn hủy & Quản lý thất thoát
- *"Có bao nhiêu đơn bị hủy và nguyên nhân là gì?"*
- *"Đơn hủy vì quá tải chủ yếu tập trung vào thứ mấy?"*

### 5. Dự báo chuẩn bị
- *"Ngày mai nên chuẩn bị bao nhiêu suất mỗi món?"*
- *"Mai là thứ Hai, có món nào cần chuẩn bị thêm so với hạn mức không?"*
