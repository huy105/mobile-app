# HƯỚNG DẪN CẤU HÌNH VÀ CHẠY MCP SERVER QUA URL (HTTP SSE)

Server MCP `mcp-firestore` hỗ trợ 2 cơ chế kết nối:
1. **Kết nối qua URL (HTTP SSE)** *(Khuyên dùng)*: Server chạy như một web service cục bộ, các AI client kết nối tới qua địa chỉ URL `http://localhost:3000/sse`.
2. **Kết nối qua Command (stdio)**: Client tự khởi động và điều khiển tiến trình `node`.

---

## BƯỚC 1: KHỞI ĐỘNG SERVER CHẾ ĐỘ URL (SSE)

Trước khi kết nối AI Client qua URL, hãy bật server:

### Cách nhanh nhất trên Windows:
Click đúp chuột vào file:
👉 **[`run-sse.bat`](file:///E:/Data/AI_Files/mobile-app/tools/mcp-firestore/run-sse.bat)**

### Hoặc chạy qua dòng lệnh terminal:
```powershell
cd E:\Data\AI_Files\mobile-app\tools\mcp-firestore
npm run build
npm run start:sse
```

Server sẽ hiển thị thông báo sẵn sàng:
```text
[mcp-firestore] Server HTTP SSE đang chạy tại: http://localhost:3000/sse
[mcp-firestore] Endpoint Health Check: http://localhost:3000/health
[mcp-firestore] Sẵn sàng nhận kết nối MCP qua URL!
```
*(Bạn có thể mở `http://localhost:3000/health` trên trình duyệt để kiểm tra server đang online).*

---

## BƯỚC 2: CẤU HÌNH AI CLIENT SỬ DỤNG URL

### 1. Cấu hình Cursor IDE
1. Mở **Cursor Settings** -> chọn mục **Features** -> cuộn xuống **MCP Servers**.
2. Bấm **+ Add New MCP Server**.
3. Điền thông tin:
   - **Name**: `canteen-report`
   - **Type**: Chọn `sse`
   - **URL**: `http://localhost:3000/sse`
4. Bấm **Add**. Cursor sẽ hiển thị chấm xanh (Connected) và tải các công cụ.

---

### 2. Cấu hình Claude Desktop (bằng URL)
Mở file `%APPDATA%\Claude\claude_desktop_config.json` và cấu hình trường `url`:

```json
{
  "mcpServers": {
    "canteen-report": {
      "url": "http://localhost:3000/sse"
    }
  }
}
```

> **Ghi chú về Claude Desktop**:
> Một số bản build cũ của Claude Desktop yêu cầu cầu nối stdio-to-sse. Nếu bản Claude Desktop của bạn chưa hỗ trợ trực tiếp thuộc tính `"url"`, bạn có thể dùng lệnh npx cầu nối:
> ```json
> {
>   "mcpServers": {
>     "canteen-report": {
>       "command": "npx",
>       "args": ["-y", "supergateway", "--sse", "http://localhost:3000/sse"]
>     }
>   }
> }
> ```

---

### 3. Cấu hình Windsurf / Zed / LibreChat
Thêm vào file cấu hình MCP của trình soạn thảo:

```json
{
  "mcpServers": {
    "canteen-report": {
      "url": "http://localhost:3000/sse"
    }
  }
}
```

---

### 4. Cấu hình Claude Code (CLI)
Chạy lệnh kết nối trực tiếp tới URL:
```bash
claude mcp add canteen-report http://localhost:3000/sse
```

---

## BƯỚC 3: KIỂM TRA & THỬ NGHIỆM

Sau khi kết nối, bạn có thể kiểm tra ngay trong khung chat:
- Gõ thử: *"Gọi công cụ ping của canteen-report"* -> Server sẽ trả về: `pong (nguồn dữ liệu: fixture)`.
- Khi đã triển khai đủ 8 tool báo cáo, bạn có thể hỏi tự nhiên:
  - *"Hôm qua doanh thu bao nhiêu và có bao nhiêu suất được bán ra?"*
  - *"Món nào bán chạy nhất trong tuần qua?"*
  - *"Khung giờ nào đông nhất?"*

---

## PHỤ LỤC: CẤU HÌNH THAY ĐỔI PORT HOẶC FIRESTORE THẬT

Mặc định server SSE chạy ở cổng `3000` với dữ liệu mẫu (`fixture`). Nếu muốn đổi cổng hoặc dùng Firestore thật:

```powershell
$env:PORT="8080"
$env:DATA_SOURCE="firestore"
$env:SERVICE_ACCOUNT_PATH="E:/Data/AI_Files/mobile-app/serviceAccount.json"
npm run start:sse
```
Lúc này URL cấu hình trong các AI Client sẽ tương ứng là: `http://localhost:8080/sse`.
