# Thiết kế MCP server báo cáo (cho Antigravity code)

Tài liệu dành cho AI agent (Antigravity). Đọc cùng `docs/analytics-data-plan.md` (các trường dữ liệu và công thức, mục 8) và `docs/data/seed-history.json` (dữ liệu mẫu để test). Mục tiêu: code một **MCP server chỉ-đọc bằng TypeScript** để chủ căng tin hỏi AI (Claude Desktop, Claude Code) bằng tiếng Việt về doanh thu, món bán chạy, khung giờ, hủy đơn, thời gian chuẩn bị, dự báo và thực đơn.

Quy ước làm việc:
- Làm tuần tự theo mục 10, sau mỗi bước chạy `npm run build` và sửa hết lỗi mới sang bước tiếp.
- Không thêm tính năng ngoài phạm vi (mục 1). Không sửa nội dung `docs/`, không sửa module Android `app/`.
- Gặp điểm chưa rõ thì chọn phương án đơn giản nhất và ghi chú trong code.

## 1. Mục tiêu và phạm vi

- **Có**: server MCP **chỉ-đọc** chạy **local** (stdio) cho một người dùng tin cậy là chủ căng tin; 8 công cụ ở mục 6; đọc từ Firestore hoặc từ file mẫu.
- **Không**: ghi hoặc xóa dữ liệu, deploy cloud, OAuth, nhiều người dùng, chatbot trong app (đó là việc khác).

## 2. Stack bắt buộc

- Node 20+, **TypeScript** (`strict`, ES modules, `"type": "module"`).
- `@modelcontextprotocol/sdk` (dùng `McpServer` và `StdioServerTransport`), `firebase-admin`, `zod`.
- Dev: `typescript`, `vitest`, `@types/node`. Build bằng `tsc` ra `dist/`; chạy `node dist/index.js`.
- **Cảnh báo quan trọng**: stdout dành riêng cho giao thức MCP. Mọi log phải ghi ra **stderr** (`console.error`). Tuyệt đối không dùng `console.log` trong server (sẽ làm hỏng kết nối).
- Script `package.json`: `build` (`tsc`), `start` (`node dist/index.js`), `test` (`vitest run`), `dev` (`tsx src/index.ts` nếu cài `tsx`).

## 3. Cấu trúc thư mục

Đặt tại `tools/mcp-firestore/` (ngoài module `app`):

```
tools/mcp-firestore/
├─ package.json, tsconfig.json, README.md, .env.example
├─ src/
│  ├─ index.ts            (tạo McpServer, đăng ký tool, nối stdio)
│  ├─ config.ts           (đọc biến môi trường)
│  ├─ data/
│  │  ├─ types.ts
│  │  ├─ dataSource.ts    (interface DataSource)
│  │  ├─ firestoreSource.ts
│  │  └─ fixtureSource.ts (đọc file mẫu, để test không cần key)
│  ├─ metrics/            (hàm thuần: sales.ts, items.ts, slots.ts, cancellations.ts, prep.ts, forecast.ts)
│  ├─ tools/              (mỗi tool một file, đăng ký vào server)
│  └─ util/               (dates.ts, privacy.ts, format.ts)
└─ test/                  (vitest, chạy trên fixtureSource)
```

Nguyên tắc: tách **dữ liệu → tính toán → tool** thành ba lớp. Hàm trong `metrics/` là **hàm thuần** (nhận mảng dữ liệu, trả số liệu), không import Firebase, nên test được bằng dữ liệu mẫu.

## 4. Nguồn dữ liệu

### 4.1 Kiểu dữ liệu (`data/types.ts`)
```ts
type OrderStatus = 'RECEIVED' | 'PREPARING' | 'READY' | 'COMPLETED' | 'CANCELLED';

interface OrderItem { menuItemId: string; name: string; price: number; quantity: number; category?: string; dishKey?: string; }

interface Order {
  id: string; userId: string; userName: string;
  items: OrderItem[]; slotId: string; slotLabel: string; pickupCode: string;
  status: OrderStatus; total: number; date: string;        // yyyy-MM-dd (giờ VN)
  createdAt: Date; updatedAt?: Date; preparingAt?: Date; readyAt?: Date;
  completedAt?: Date; cancelledAt?: Date;
  cancelledBy?: 'STUDENT' | 'CANTEEN'; cancelReason?: 'SOLD_OUT' | 'OVERLOAD' | 'NO_SHOW' | 'CHANGED_MIND' | 'OTHER';
}

interface MenuItem {
  id: string; name: string; description: string; price: number; imageUrl: string;
  category: string; inStock: boolean; date: string;
  dishKey?: string; tags?: string[]; dailyQuota?: number; soldQty?: number;  // dailyQuota 0 hoặc thiếu = không giới hạn
}

interface TimeSlot { id: string; label: string; startMinute: number; endMinute: number; maxOrders: number; }
```
Đơn cũ có thể thiếu các trường tùy chọn: code phải chịu được (xem mục 5).

### 4.2 Interface
```ts
interface DateRange { from: string; to: string }   // yyyy-MM-dd, gồm cả hai đầu

interface DataSource {
  getOrders(range: DateRange): Promise<Order[]>;        // lọc theo trường `date`
  getMenuItems(range: DateRange): Promise<MenuItem[]>;  // lọc theo trường `date`
  getTimeSlots(): Promise<TimeSlot[]>;
}
```

### 4.3 `FirestoreSource`
- Khởi tạo bằng `firebase-admin` với service account đọc từ `SERVICE_ACCOUNT_PATH` (`credential.cert(JSON.parse(...))`).
- Truy vấn: `collection('orders').where('date', '>=', from).where('date', '<=', to)`. Chỉ một trường nên **không cần composite index**. Tương tự cho `menuItems`.
- `getTimeSlots()`: đọc collection `timeSlots`, sắp theo `startMinute`.
- Chuyển mọi `Timestamp` thành `Date` bằng `.toDate()`; trường không có thì để `undefined`.
- Chỉ dùng thao tác đọc (`get()`); không import hay gọi `set`, `update`, `delete`, `add`.

### 4.4 `FixtureSource`
- Đọc `docs/data/seed-history.json` (đường dẫn qua `FIXTURE_PATH`, mặc định tính từ thư mục repo). File có ba mảng `menuItems`, `orders`, `timeSlotCounters`; các trường thời gian là chuỗi ISO, cần đổi sang `Date`.
- File **không có** `timeSlots`. Dựng từ `docs/data/seed-menu.json` (mảng `timeSlots`), sắp theo `startMinute`, gán `id = "slot" + (chỉ số + 1)` (khớp `slotId` mà script sinh dữ liệu dùng ở chế độ dry-run).
- Lọc theo `date` giống `FirestoreSource`.

### 4.5 Chọn nguồn
`DATA_SOURCE=firestore | fixture`, mặc định `firestore`. Nếu chọn `firestore` mà thiếu hoặc sai `SERVICE_ACCOUNT_PATH` thì báo lỗi tiếng Việt rõ ràng và gợi ý "đặt DATA_SOURCE=fixture để chạy thử bằng dữ liệu mẫu".

### 4.6 Giới hạn
Mỗi lần gọi tool đọc tối đa **92 ngày**; vượt thì trả lỗi yêu cầu thu hẹp khoảng ngày.

## 5. Quy ước chung

- **Ngày giờ**: dùng múi giờ `TIMEZONE` (mặc định `Asia/Ho_Chi_Minh`). "Hôm nay", "hôm qua", "tuần này" (Thứ 2 đến Chủ nhật), "7 ngày qua" tính theo giờ Việt Nam. Mọi tool nhận `date_from` và `date_to` (yyyy-MM-dd) **tùy chọn**; thiếu cả hai thì mặc định là hôm nay; thiếu một đầu thì lấy đầu kia. Kiểm tra `from <= to`.
- **Tiền**: VND, số nguyên. **Thời lượng**: phút, làm tròn 1 chữ số thập phân. **Tỷ lệ**: số 0..1 trong JSON, hiển thị phần trăm trong câu tóm tắt.
- **Trạng thái đơn**: chỉ `COMPLETED` tính doanh thu và số suất bán. `CANCELLED` loại khỏi bán ra, thống kê riêng. `RECEIVED`, `PREPARING`, `READY` là đơn đang xử lý: báo riêng, không tính vào doanh thu.
- **Thiếu dữ liệu**: nếu đơn thiếu một trường cần cho phép tính (ví dụ không có `readyAt`) thì **bỏ đơn đó khỏi phép tính** và nêu số đơn bị bỏ trong kết quả (`skipped`). Không bịa số.
- **Định danh món**: nhóm theo `dishKey`; nếu thiếu thì dùng `name`. Hiển thị bằng `name`.
- **Quyền riêng tư**: `MASK_PII=true` mặc định. Không bao giờ trả `userId`, `userName`, email. Nếu cần phân biệt khách thì dùng mã băm ngắn kiểu `khach_a3f` (băm `userId`, lấy 3 ký tự).
- **Định dạng kết quả** mỗi tool: `content` gồm một khối text duy nhất: dòng đầu là **tóm tắt tiếng Việt ngắn** (1 đến 3 câu), tiếp theo là khối JSON dữ liệu trong ``` ```json ``` ```. Có thêm `structuredContent` cùng nội dung JSON nếu SDK hỗ trợ.
- **Lỗi**: trả `isError: true` kèm thông điệp tiếng Việt dễ hiểu (ví dụ "Khoảng ngày không hợp lệ"). Không lộ stack trace, đường dẫn tệp key hay nội dung `.env`.
- **Mô tả tool** (`description`) viết bằng tiếng Việt, nói rõ khi nào nên dùng, để model chọn đúng tool.

## 6. Đặc tả 8 công cụ

Đăng ký bằng `server.registerTool(name, { title, description, inputSchema }, handler)` với `inputSchema` là object `zod`. Mọi tham số đều tùy chọn trừ khi ghi khác. Kiểu ngày: `z.string().regex(/^\d{4}-\d{2}-\d{2}$/)`.

### 6.1 `get_sales_summary`
- Dùng khi: hỏi doanh thu, số đơn, số suất bán.
- Input: `date_from?`, `date_to?`, `group_by?: 'none' | 'day' | 'week' | 'category'` (mặc định `none`).
- Tính: doanh thu = tổng `total` của đơn `COMPLETED`; số đơn = đếm đơn `COMPLETED`; số suất = tổng `items[].quantity`; giá trị đơn trung bình = doanh thu / số đơn. So với **kỳ liền trước có cùng số ngày** (đọc thêm dữ liệu kỳ trước), tính `% thay đổi` doanh thu (null nếu kỳ trước bằng 0). `group_by` chia nhỏ theo ngày, tuần (Thứ 2 đầu tuần) hoặc `category` của món (dùng `items[].category`, thiếu thì "Không rõ"). Đếm riêng đơn đang xử lý và đơn hủy.
- Output JSON mẫu:
```json
{ "range": {"from": "2026-10-24", "to": "2026-10-30"}, "revenue": 5120000, "orders": 150, "portions": 218,
  "avgOrderValue": 34133, "changePctVsPrevious": 6.4, "inProgressOrders": 0, "cancelledOrders": 9,
  "groups": [ {"key": "2026-10-24", "revenue": 790000, "orders": 23, "portions": 33} ] }
```

### 6.2 `get_top_items`
- Dùng khi: món bán chạy hoặc bán kém.
- Input: `date_from?`, `date_to?`, `limit?: number` (mặc định 5, tối đa 20), `order?: 'best' | 'worst'` (mặc định `best`).
- Tính: gom theo `dishKey`, tổng `quantity` và doanh thu (`price × quantity`) trên đơn `COMPLETED`. `best` sắp giảm dần theo số suất; `worst` sắp tăng dần và **bao gồm món có trong menu của khoảng ngày nhưng không có đơn nào** (số suất 0).
- Output: `items: [{ dishKey, name, category, portions, revenue, sharePct }]`.

### 6.3 `get_orders_by_slot`
- Dùng khi: hỏi khung giờ đông, khung hay đầy.
- Input: `date_from?`, `date_to?`.
- Tính: theo từng khung (`slotLabel`): số đơn **không** `CANCELLED`, tỷ lệ lấp đầy = số đơn / (`maxOrders` × số ngày có đơn trong khoảng), số ngày khung đó đạt `maxOrders` (đầy). Khung đông nhất = tỷ lệ lấp đầy cao nhất. `maxOrders` lấy từ `getTimeSlots()` (khớp theo `slotId`, nếu không khớp thì theo `label`).
- Output: `slots: [{ slotLabel, orders, fillRate, fullDays, days }]`, `busiest`, `oftenFull`.

### 6.4 `get_cancellations`
- Dùng khi: hỏi đơn bị hủy, lý do, ai hủy, hủy món nào.
- Input: `date_from?`, `date_to?`.
- Tính: tổng đơn `CANCELLED`, tỷ lệ hủy = hủy / (hủy + hoàn thành). Đếm theo `cancelReason` (thiếu thì "UNKNOWN"), theo `cancelledBy`. Top món xuất hiện trong đơn hủy theo `dishKey`. Riêng số `NO_SHOW`.
- Output: `{ total, ratePct, byReason: {...}, byCancelledBy: {...}, topDishesInCancelled: [...], noShow }`.

### 6.5 `get_prep_time_stats`
- Dùng khi: hỏi bếp chuẩn bị mất bao lâu, đơn chờ lâu, ngày nào chậm.
- Input: `date_from?`, `date_to?`.
- Tính trên đơn `COMPLETED` có đủ mốc thời gian: `prep = readyAt − preparingAt`; `waitToStart = preparingAt − createdAt`; `pickupDelay = completedAt − readyAt` (phút). Mỗi chỉ số cho trung bình và **P90** (nội suy gần nhất). Tách theo khung giờ. **Ngày chậm bất thường**: trung bình `prep` của ngày > (trung bình toàn kỳ + 1,5 × độ lệch chuẩn của các trung bình theo ngày). Đơn thiếu mốc thời gian đếm vào `skipped`.
- Output: `{ prep: {avg, p90}, waitToStart: {...}, pickupDelay: {...}, bySlot: [...], slowDays: [{date, avgPrep}], skipped }`.

### 6.6 `get_unsold_items`
- Dùng khi: hỏi món ế, món bán hết sớm, nên giảm hay tăng suất.
- Input: `date_from?`, `date_to?`, `threshold?: number` (0..1, mặc định 0.5).
- Tính từ `menuItems` của khoảng ngày (món có `dailyQuota > 0`): tỷ lệ bán mỗi ngày = `soldQty / dailyQuota`; gom theo `dishKey`: tỷ lệ bán trung bình, số ngày dưới `threshold`, số ngày hết suất (`soldQty >= dailyQuota`). Đánh dấu `status`: `"ế"` nếu tỷ lệ trung bình < `threshold`, `"bán hết sớm"` nếu hết suất ≥ 30% số ngày, ngược lại `"ổn"`.
- Output: `items: [{ dishKey, name, avgSellThrough, daysBelowThreshold, soldOutDays, days, status }]` sắp theo `avgSellThrough` tăng dần.

### 6.7 `forecast_tomorrow`
- Dùng khi: hỏi mai nên chuẩn bị bao nhiêu suất mỗi món.
- Input: `buffer_pct?: number` (mặc định 10, 0 đến 50), `date?` (ngày dự báo, mặc định ngày mai theo giờ VN).
- Tính: đọc `menuItems` 5 tuần gần nhất. Với mỗi `dishKey`, lấy `soldQty` của **các ngày cùng thứ** trong tối đa 4 tuần gần nhất (trước ngày dự báo). Dự báo = `ceil(trung bình × (1 + buffer_pct/100))`. Ghi `weeksOfData` (số ngày cùng thứ thực có). Nếu `weeksOfData < 2` thì không dự báo món đó, trả `forecast: null` và `note: "chưa đủ dữ liệu"`. Nếu dự báo > `dailyQuota` hiện tại của món thì thêm `warning: "vượt hạn mức hiện tại"`.
- Output: `{ forDate, weekday, buffer_pct, items: [{ dishKey, name, forecast, weeksOfData, currentQuota, warning?, note? }] }`.

### 6.8 `list_menu`
- Dùng khi: hỏi thực đơn một ngày, lọc theo loại hoặc giá ("món chay dưới 30k").
- Input: `date?` (mặc định hôm nay), `tags?: string[]` (lọc món có **tất cả** tag; giá trị hợp lệ `vegetarian`, `filling`, `quick`, `healthy`, `drink`), `max_price?: number`, `only_available?: boolean` (mặc định true).
- Tính: lấy `menuItems` của ngày. `remaining = dailyQuota − soldQty` nếu `dailyQuota > 0`, ngược lại `null` (không giới hạn). `available = inStock && (remaining === null || remaining > 0)`. Nếu `only_available` thì loại món không `available`. Lọc theo `tags` và `price <= max_price`. Sắp theo `category` rồi `price`.
- Output: `items: [{ name, category, price, tags, remaining, available }]`. Nếu rỗng, câu tóm tắt nói rõ "không có món nào khớp" và gợi ý nới điều kiện.

## 7. Cấu hình và chạy

`.env.example` (không commit `.env`):
```
DATA_SOURCE=fixture            # firestore | fixture
SERVICE_ACCOUNT_PATH=          # đường dẫn tới serviceAccount.json (khi dùng firestore)
TIMEZONE=Asia/Ho_Chi_Minh
MASK_PII=true
FIXTURE_PATH=                  # tùy chọn, mặc định ../../docs/data/seed-history.json
```
`config.ts` đọc biến môi trường (không bắt buộc cài `dotenv`; MCP client truyền env qua cấu hình).

Đăng ký vào **Claude Code**:
```
claude mcp add canteen-report -e DATA_SOURCE=firestore -e SERVICE_ACCOUNT_PATH=/duong/dan/serviceAccount.json -- node /duong/dan/tools/mcp-firestore/dist/index.js
```
Đăng ký vào **Claude Desktop** (`claude_desktop_config.json`), ghi mẫu này vào `README.md`:
```json
{ "mcpServers": { "canteen-report": {
    "command": "node",
    "args": ["/duong/dan/tools/mcp-firestore/dist/index.js"],
    "env": { "DATA_SOURCE": "firestore", "SERVICE_ACCOUNT_PATH": "/duong/dan/serviceAccount.json" } } } }
```
Bảo mật: file key để **ngoài repo** (hoặc tên `serviceAccount.json`, đã nằm trong `.gitignore`); server chỉ-đọc nên nên dùng service account có vai trò chỉ xem Firestore nếu muốn siết thêm.

## 8. Câu hỏi mẫu và tool kỳ vọng

| Câu hỏi | Tool |
|---|---|
| "Hôm qua bán được bao nhiêu?" | `get_sales_summary` |
| "Khung giờ nào đông nhất tuần này?" | `get_orders_by_slot` |
| "Món nào bán chạy nhất 4 tuần qua?" | `get_top_items` (best) |
| "Món nào ế?" | `get_unsold_items` hoặc `get_top_items` (worst) |
| "Bao nhiêu đơn bị hủy và vì sao?" | `get_cancellations` |
| "Bếp chuẩn bị mất bao lâu, ngày nào chậm?" | `get_prep_time_stats` |
| "Mai nên chuẩn bị bao nhiêu suất mỗi món?" | `forecast_tomorrow` |
| "Hôm nay có món chay dưới 30k không?" | `list_menu` |

## 9. Kiểm thử và tiêu chí hoàn thành

Test bằng `vitest` trên `FixtureSource` (dữ liệu: `docs/data/seed-history.json`, sinh bằng `node tools/seed-history/seed-history.js --dry-run`; ngày trong file tính theo thời điểm sinh nên **không hardcode ngày hay tổng tiền cụ thể**, hãy tính đối chiếu từ chính file JSON).

- [ ] Tổng đơn = số `COMPLETED` + số `CANCELLED` (dữ liệu mẫu không có đơn đang xử lý).
- [ ] `get_sales_summary`: doanh thu khớp tổng `total` của đơn `COMPLETED` cộng tay từ JSON; số suất khớp tổng `quantity`.
- [ ] Mọi tỷ lệ (`fillRate`, `ratePct`/100, `avgSellThrough`) nằm trong 0..1 (hoặc 0..100 với `Pct`); mọi thời lượng ≥ 0.
- [ ] `get_prep_time_stats`: ngày có trung bình `prep` cao nhất trong dữ liệu mẫu nằm trong `slowDays`.
- [ ] `get_unsold_items`: "Xôi thập cẩm" và "Mì xào" có `status = "ế"`.
- [ ] `get_cancellations`: `byReason` cộng lại bằng `total`; có đủ 4 lý do `SOLD_OUT`, `OVERLOAD`, `NO_SHOW`, `CHANGED_MIND`.
- [ ] `forecast_tomorrow`: với dữ liệu 4 tuần trả dự báo số nguyên cho mọi món; khi chỉ nạp dưới 2 tuần thì trả `forecast: null` kèm `note`.
- [ ] `list_menu` với `tags: ["vegetarian"]` và `max_price: 30000` chỉ trả món đúng điều kiện.
- [ ] Khi `MASK_PII=true`, không có kết quả tool nào chứa `userName` hoặc `userId` (kiểm bằng cách tìm chuỗi `demo_student_` và tên mẫu trong toàn bộ output).
- [ ] Không có `console.log` ra stdout trong mã nguồn (tìm bằng `grep`); không có `set`/`update`/`delete`/`add` Firestore trong mã nguồn.
- [ ] `npm run build` và `npm test` thành công.
- [ ] Kiểm giao thức: `npx @modelcontextprotocol/inspector node dist/index.js` (với `DATA_SOURCE=fixture`) liệt kê đủ **8 tool** và gọi thử từng tool không lỗi.
- [ ] Nghiệm thu bằng Claude Code hoặc Claude Desktop: hỏi ít nhất 5 câu ở mục 8, câu trả lời có số liệu và đúng tool.

## 10. Thứ tự làm

1. **Khung dự án**: `package.json`, `tsconfig.json`, `config.ts`, `index.ts` chạy được với một tool tạm `ping` → build, kiểm tra bằng Inspector.
2. **Dữ liệu**: `types.ts`, `dataSource.ts`, `fixtureSource.ts`, `util/dates.ts` + test đọc dữ liệu và lọc theo ngày.
3. **Tool 1, 2, 3** (`metrics/sales.ts`, `items.ts`, `slots.ts`) + test.
4. **Tool 4, 5, 6** (`cancellations.ts`, `prep.ts`, phần ế của `items.ts`) + test.
5. **Tool 7, 8** (`forecast.ts`, `list_menu`) + test.
6. **`firestoreSource.ts`**, `util/privacy.ts` áp dụng cho mọi tool, `README.md` (cách chạy, đăng ký MCP), chạy Inspector và nghiệm thu mục 9.

Sau mỗi bước chạy `npm run build` và (từ bước 2) `npm test`; chỉ sang bước sau khi xanh.
