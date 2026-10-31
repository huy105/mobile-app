# Thiết kế MCP server báo cáo (cho Antigravity code)

Tài liệu dành cho AI agent (Antigravity). Đọc cùng `docs/analytics-data-plan.md` (các trường dữ liệu và công thức, mục 8) và `docs/data/seed-history.json` (dữ liệu mẫu để test). Mục tiêu: code một **MCP server chỉ-đọc bằng TypeScript** để chủ căng tin hỏi AI (Claude Desktop, Claude Code) bằng tiếng Việt về doanh thu, món bán chạy, khung giờ, hủy đơn, thời gian chuẩn bị, dự báo và thực đơn.

**Nguyên tắc thiết kế (quan trọng, đọc trước khi code):** server **không map mỗi câu hỏi một công cụ**. Có hai lớp: (1) một công cụ tổng quát **`query_orders`** cùng **bộ lọc dùng chung** (mục 5A) để AI tự ghép được những câu hỏi chưa ai nghĩ tới (ví dụ "món nào bán chạy nhất vào thứ Sáu ở khung chiều tối"); (2) vài công cụ **lối tắt** cho các việc hay gặp hoặc có logic đặc thù (dự báo, ngày bếp chậm, món ế). Cả hai lớp **dùng chung một bộ máy tính toán** (`metrics/engine.ts`), không viết lại công thức riêng cho từng tool. **Server luôn tự tính số; AI không phải cộng trừ từ dữ liệu thô.**

Quy ước làm việc:
- Làm tuần tự theo mục 10, sau mỗi bước chạy `npm run build` và sửa hết lỗi mới sang bước tiếp.
- Không thêm tính năng ngoài phạm vi (mục 1). Không sửa nội dung `docs/`, không sửa module Android `app/`.
- Gặp điểm chưa rõ thì chọn phương án đơn giản nhất và ghi chú trong code.

## 1. Mục tiêu và phạm vi

- **Có**: server MCP **chỉ-đọc** chạy **local** (stdio) cho một người dùng tin cậy là chủ căng tin; 9 công cụ ở mục 6 (1 công cụ tổng quát + 8 lối tắt); đọc từ Firestore hoặc từ file mẫu.
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
│  ├─ metrics/            (hàm thuần)
│  │  ├─ engine.ts        (BỘ MÁY CHUNG: lọc, nhóm, tính chỉ số; mục 5A và 6.0)
│  │  ├─ forecast.ts, unsold.ts   (logic riêng cho dự báo và món ế, dựa trên menuItems)
│  │  └─ stats.ts         (trung bình, P90, độ lệch chuẩn)
│  ├─ tools/              (mỗi tool một file, đăng ký vào server; tool lối tắt chỉ là lớp mỏng gọi engine)
│  └─ util/               (dates.ts, filters.ts, privacy.ts, format.ts)
└─ test/                  (vitest, chạy trên fixtureSource)
```

Nguyên tắc: tách **dữ liệu → tính toán → tool** thành ba lớp. Hàm trong `metrics/` là **hàm thuần** (nhận mảng dữ liệu, trả số liệu), không import Firebase, nên test được bằng dữ liệu mẫu. Mọi công thức doanh thu, số đơn, số suất, tỷ lệ hủy, thời gian chuẩn bị chỉ được viết **một lần** trong `engine.ts`; các tool khác gọi lại, để cùng một câu hỏi cho cùng một con số dù hỏi bằng tool nào.

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

## 5A. Bộ lọc dùng chung

Mọi tool đọc **đơn hàng** (`query_orders`, 6.1 đến 6.5) nhận cùng một bộ lọc, khai báo **một lần** trong `util/filters.ts` bằng một schema `zod` dùng lại (`filterShape`). Tất cả tùy chọn:

| Tham số | Kiểu | Ý nghĩa |
|---|---|---|
| `date_from`, `date_to` | `yyyy-MM-dd` | Khoảng ngày (mục 5). |
| `weekdays` | `number[]` (1 = Thứ Hai … 7 = Chủ nhật) | Chỉ lấy các ngày thuộc những thứ này (tính theo trường `date`, giờ Việt Nam). |
| `daypart` | `'lunch'` hoặc `'evening'` | `lunch` là khung có `startMinute < 840` (trước 14:00), `evening` là từ 14:00 trở đi. |
| `slots` | `string[]` | Nhãn khung giờ, ví dụ `"11:30 - 11:45"`. |
| `categories` | `string[]` | Nhóm món (`items[].category`), ví dụ `"Đồ uống"`. |
| `dish_keys` | `string[]` | Mã món (`items[].dishKey`, thiếu thì khớp theo `name`). |

Quy tắc kết hợp:
- Giữa các tham số khác nhau là **VÀ**; trong một danh sách là **HOẶC**. Ví dụ `weekdays=[5]` và `daypart='evening'` là các đơn chiều tối của thứ Sáu.
- `slots` và `daypart` lọc theo **đơn** (đơn thuộc khung nào). `categories` và `dish_keys` lọc theo **dòng món** trong đơn. Khung của đơn lấy từ `slotId` qua `getTimeSlots()`; không khớp thì đọc giờ bắt đầu từ `slotLabel`.
- **Khi có `categories` hoặc `dish_keys`**: mọi chỉ số tiền và số suất chỉ tính các **dòng món khớp** (doanh thu = tổng `price × quantity` của dòng khớp, không dùng `total` của đơn); số đơn đếm các đơn **có ít nhất một dòng khớp**. Nếu không có hai bộ lọc này thì doanh thu dùng `total` của đơn.
- Tool dùng `menuItems` (6.6 `get_unsold_items`, 6.7 `forecast_tomorrow`) chỉ hỗ trợ `date_from`, `date_to`, `weekdays`, `categories`, `dish_keys`. Bộ lọc không áp dụng được (`slots`, `daypart`) bị **bỏ qua** và nêu trong `ignoredFilters` của kết quả; không báo lỗi.
- Mọi kết quả đều có `appliedFilters` (bộ lọc đã dùng, đã chuẩn hóa) và `skipped` (số đơn bị bỏ vì thiếu trường), để AI và người dùng thấy rõ phạm vi con số.
- Giới hạn 92 ngày (mục 4.6) áp dụng cho khoảng `date_from` đến `date_to`, không phụ thuộc `weekdays`.

## 6. Đặc tả 9 công cụ

**Cách chọn tool** (viết vào `description` của từng tool): câu hỏi chung chung hoặc nhiều chiều, hoặc không tool lối tắt nào khớp → `query_orders`. Câu hỏi đúng một việc quen thuộc (doanh thu kèm so sánh kỳ trước, ngày bếp chậm, món ế, dự báo, thực đơn) → tool lối tắt tương ứng.

Đăng ký bằng `server.registerTool(name, { title, description, inputSchema }, handler)` với `inputSchema` là object `zod`. Mọi tham số đều tùy chọn trừ khi ghi khác. Kiểu ngày: `z.string().regex(/^\d{4}-\d{2}-\d{2}$/)`.

### 6.0 `query_orders` (công cụ tổng quát)
- Dùng khi: câu hỏi cần **lọc hoặc nhóm theo chiều không có sẵn ở tool lối tắt**, hoặc cần ghép nhiều chiều, ví dụ "món nào bán chạy nhất vào thứ Sáu", "doanh thu từng thứ trong tuần", "tỷ lệ hủy theo khung giờ", "trà chanh bán thế nào theo ngày".
- Input: toàn bộ bộ lọc ở mục 5A, cộng:
  - `group_by?: Dimension[]` (tối đa 2 phần tử, mặc định rỗng = một dòng tổng). `Dimension` là một trong: `day`, `week` (Thứ Hai đầu tuần), `weekday`, `daypart`, `slot`, `dish`, `category`, `cancel_reason`, `status`.
  - `metrics?: Metric[]` (mặc định `['revenue','orders','portions']`). `Metric` là một trong: `revenue`, `orders`, `portions`, `avg_order_value`, `cancelled_orders`, `in_progress_orders`, `cancel_rate`, `avg_prep_minutes`, `p90_prep_minutes`, `avg_wait_minutes` (từ `createdAt` đến `preparingAt`), `avg_pickup_delay_minutes` (từ `readyAt` đến `completedAt`).
  - `sort_by?: Metric` (mặc định chỉ số đầu tiên của `metrics`), `sort_dir?: 'desc' | 'asc'` (mặc định `desc`), `limit?: number` (mặc định 20, tối đa 100).
- Định nghĩa chỉ số (đây là **nguồn sự thật duy nhất**, các tool khác dùng lại):
  - `revenue`, `orders`, `portions`, `avg_order_value`: chỉ đơn `COMPLETED` (doanh thu tính theo quy tắc ở mục 5A).
  - `cancelled_orders`: đơn `CANCELLED`. `in_progress_orders`: `RECEIVED`, `PREPARING`, `READY`.
  - `cancel_rate` = `cancelled_orders / (cancelled_orders + orders)`, null nếu mẫu bằng 0.
  - Các chỉ số thời gian: đơn `COMPLETED` có đủ hai mốc tương ứng; đơn thiếu mốc đếm vào `skipped` (theo từng chỉ số), phút làm tròn 1 chữ số thập phân, P90 nội suy gần nhất.
  - `group_by` có `cancel_reason`: chỉ xét đơn `CANCELLED`, và chỉ các chỉ số `cancelled_orders`, `portions`, `revenue` (giá trị của đơn bị hủy) có nghĩa; chỉ số khác trả null.
  - `group_by` có `status`: chỉ số `orders`, `cancelled_orders`, `in_progress_orders` đếm theo đúng trạng thái của từng nhóm.
  - `group_by: dish` hoặc `category`: một đơn nhiều món được chia theo dòng món; `orders` đếm số đơn có món đó, `portions` và `revenue` tính theo dòng món. Cộng các nhóm lại có thể lớn hơn tổng đơn thật, ghi rõ điều này trong `note`.
- Output: `{ appliedFilters, groupBy, metrics, rows: [{ key: {<dimension>: giá trị}, <metric>: số }], totals: {<metric>: số}, rowCount, truncated, skipped, note? }`. `totals` luôn có, tính trên toàn bộ dữ liệu đã lọc (không bị cắt bởi `limit`). `truncated = true` khi `rowCount > limit`.
- Câu tóm tắt: nói rõ phạm vi (khoảng ngày, bộ lọc) và vài dòng đầu của kết quả.
- Lỗi cần có thông điệp tiếng Việt: `group_by` quá 2 phần tử, `Dimension` hoặc `Metric` không hợp lệ (kèm danh sách giá trị hợp lệ), `weekdays` ngoài 1 đến 7.
- Ví dụ gọi: `{ "weekdays": [5], "group_by": ["dish"], "metrics": ["portions","revenue"], "sort_by": "portions", "limit": 5, "date_from": "2026-10-01", "date_to": "2026-10-31" }` → 5 món bán chạy nhất vào các ngày thứ Sáu của tháng.

### 6.1 `get_sales_summary`
- Dùng khi: hỏi doanh thu, số đơn, số suất bán.
- Lối tắt của `query_orders` (chỉ số `revenue`, `orders`, `portions`, `avg_order_value`, `cancelled_orders`, `in_progress_orders`), cộng thêm phần so sánh kỳ trước. **Phải gọi engine**, không viết lại công thức.
- Input: bộ lọc mục 5A, và `group_by?: 'none' | 'day' | 'week' | 'category'` (mặc định `none`).
- Tính: doanh thu = tổng `total` của đơn `COMPLETED`; số đơn = đếm đơn `COMPLETED`; số suất = tổng `items[].quantity`; giá trị đơn trung bình = doanh thu / số đơn. So với **kỳ liền trước có cùng số ngày** (đọc thêm dữ liệu kỳ trước), tính `% thay đổi` doanh thu (null nếu kỳ trước bằng 0). `group_by` chia nhỏ theo ngày, tuần (Thứ 2 đầu tuần) hoặc `category` của món (dùng `items[].category`, thiếu thì "Không rõ"). Đếm riêng đơn đang xử lý và đơn hủy.
- Output JSON mẫu:
```json
{ "range": {"from": "2026-10-24", "to": "2026-10-30"}, "revenue": 5120000, "orders": 150, "portions": 218,
  "avgOrderValue": 34133, "changePctVsPrevious": 6.4, "inProgressOrders": 0, "cancelledOrders": 9,
  "groups": [ {"key": "2026-10-24", "revenue": 790000, "orders": 23, "portions": 33} ] }
```

### 6.2 `get_top_items`
- Dùng khi: món bán chạy hoặc bán kém.
- Lối tắt của `query_orders` (`group_by: ['dish']`, chỉ số `portions`, `revenue`).
- Input: bộ lọc mục 5A, `limit?: number` (mặc định 5, tối đa 20), `order?: 'best' | 'worst'` (mặc định `best`).
- Tính: gom theo `dishKey`, tổng `quantity` và doanh thu (`price × quantity`) trên đơn `COMPLETED`. `best` sắp giảm dần theo số suất; `worst` sắp tăng dần và **bao gồm món có trong menu của khoảng ngày nhưng không có đơn nào** (số suất 0).
- Output: `items: [{ dishKey, name, category, portions, revenue, sharePct }]`.

### 6.3 `get_orders_by_slot`
- Dùng khi: hỏi khung giờ đông, khung hay đầy.
- Lối tắt của `query_orders` (`group_by: ['slot']`) cộng phần tỷ lệ lấp đầy và khung hay đầy.
- Input: bộ lọc mục 5A.
- Tính: theo từng khung (`slotLabel`): số đơn **không** `CANCELLED`, tỷ lệ lấp đầy = số đơn / (`maxOrders` × số ngày có đơn trong khoảng), số ngày khung đó đạt `maxOrders` (đầy). Khung đông nhất = tỷ lệ lấp đầy cao nhất. `maxOrders` lấy từ `getTimeSlots()` (khớp theo `slotId`, nếu không khớp thì theo `label`).
- Output: `slots: [{ slotLabel, orders, fillRate, fullDays, days }]`, `busiest`, `oftenFull`.

### 6.4 `get_cancellations`
- Dùng khi: hỏi đơn bị hủy, lý do, ai hủy, hủy món nào.
- Lối tắt của `query_orders` (`group_by: ['cancel_reason']`, `['dish']` trong đơn hủy).
- Input: bộ lọc mục 5A.
- Tính: tổng đơn `CANCELLED`, tỷ lệ hủy = hủy / (hủy + hoàn thành). Đếm theo `cancelReason` (thiếu thì "UNKNOWN"), theo `cancelledBy`. Top món xuất hiện trong đơn hủy theo `dishKey`. Riêng số `NO_SHOW`.
- Output: `{ total, ratePct, byReason: {...}, byCancelledBy: {...}, topDishesInCancelled: [...], noShow }`.

### 6.5 `get_prep_time_stats`
- Dùng khi: hỏi bếp chuẩn bị mất bao lâu, đơn chờ lâu, ngày nào chậm.
- Dùng các chỉ số thời gian của `query_orders`, cộng phần phát hiện ngày chậm.
- Input: bộ lọc mục 5A.
- Tính trên đơn `COMPLETED` có đủ mốc thời gian: `prep = readyAt − preparingAt`; `waitToStart = preparingAt − createdAt`; `pickupDelay = completedAt − readyAt` (phút). Mỗi chỉ số cho trung bình và **P90** (nội suy gần nhất). Tách theo khung giờ. **Ngày chậm bất thường**: trung bình `prep` của ngày > (trung bình toàn kỳ + 1,5 × độ lệch chuẩn của các trung bình theo ngày). Đơn thiếu mốc thời gian đếm vào `skipped`.
- Output: `{ prep: {avg, p90}, waitToStart: {...}, pickupDelay: {...}, bySlot: [...], slowDays: [{date, avgPrep}], skipped }`.

### 6.6 `get_unsold_items`
- Dùng khi: hỏi món ế, món bán hết sớm, nên giảm hay tăng suất.
- Dựa trên `menuItems` (không phải đơn hàng), nên chỉ hỗ trợ một phần bộ lọc (mục 5A).
- Input: `date_from?`, `date_to?`, `weekdays?`, `categories?`, `dish_keys?`, `threshold?: number` (0..1, mặc định 0.5).
- Tính từ `menuItems` của khoảng ngày (món có `dailyQuota > 0`): tỷ lệ bán mỗi ngày = `soldQty / dailyQuota`; gom theo `dishKey`: tỷ lệ bán trung bình, số ngày dưới `threshold`, số ngày hết suất (`soldQty >= dailyQuota`). Đánh dấu `status`: `"ế"` nếu tỷ lệ trung bình < `threshold`, `"bán hết sớm"` nếu hết suất ≥ 30% số ngày, ngược lại `"ổn"`.
- Output: `items: [{ dishKey, name, avgSellThrough, daysBelowThreshold, soldOutDays, days, status }]` sắp theo `avgSellThrough` tăng dần.

### 6.7 `forecast_tomorrow`
- Dùng khi: hỏi mai nên chuẩn bị bao nhiêu suất mỗi món.
- Input: `buffer_pct?: number` (mặc định 10, 0 đến 50), `date?` (ngày dự báo, mặc định ngày mai theo giờ VN), `categories?`, `dish_keys?` (chỉ dự báo các món này).
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
| "Tuần này doanh thu so với tuần trước thế nào?" | `get_sales_summary` (có `% thay đổi` so kỳ liền trước) |
| "Ngày nào trong tháng doanh thu cao nhất?" | `get_sales_summary` (`group_by: day`) |
| "Doanh thu theo nhóm món (cơm, bún, đồ uống) tuần này?" | `get_sales_summary` (`group_by: category`) |
| "Khung chiều tối có đông không, có đáng mở không?" | `get_orders_by_slot` (so `fillRate` của các khung 17:00 - 18:00 với khung trưa) |
| "Khung giờ nào hay bị đầy nhất?" | `get_orders_by_slot` (danh sách khung hay đầy) |
| "Món nào bán chạy nhất hôm nay, bán được mấy suất?" | `get_top_items` (best, `date_from = date_to` = hôm nay) |
| "Hôm qua món nào hết suất sớm?" | `get_unsold_items` (cột "bán hết sớm") |
| "Vì sao hôm 15 nhiều đơn bị hủy?" | `get_cancellations` (lọc `date_from = date_to = 15`) |
| "Có bao nhiêu khách không đến lấy tuần này?" | `get_cancellations` (số `NO_SHOW`) |
| "Món nào hay bị hủy vì hết món?" | `get_cancellations` (top `dishKey` trong đơn hủy) |
| "Bếp chậm ở khung giờ nào?" | `get_prep_time_stats` (thống kê theo khung giờ) |
| "Mai là thứ Hai, nên làm thêm món nào?" | `forecast_tomorrow` (so dự báo với `dailyQuota` hiện tại) |
| "Có món nào dưới 25k còn nhiều suất không?" | `list_menu` (`max_price`, `only_available`) |
| "Hôm qua có món nào chay không?" | `list_menu` (`date` = hôm qua, `tags: ["vegetarian"]`) |

### Câu hỏi nhiều chiều (dùng `query_orders`)
Các câu này **không có tool lối tắt**, AI phải ghép bộ lọc và nhóm. Dùng để kiểm tra tính tổng quát:

| Câu hỏi | Gọi `query_orders` với |
|---|---|
| "Món nào bán chạy nhất vào thứ Sáu?" | `weekdays: [5]`, `group_by: ['dish']`, `metrics: ['portions','revenue']`, `sort_by: 'portions'` |
| "Doanh thu từng thứ trong tuần khác nhau thế nào?" | `group_by: ['weekday']`, `metrics: ['revenue','orders']` |
| "Khung chiều tối bán gì nhiều nhất?" | `daypart: 'evening'`, `group_by: ['dish']`, `sort_by: 'portions'` |
| "Tỷ lệ hủy theo từng khung giờ?" | `group_by: ['slot']`, `metrics: ['orders','cancelled_orders','cancel_rate']` |
| "Trà chanh bán thế nào từng ngày tuần này?" | `dish_keys: ['tra-chanh']`, `group_by: ['day']`, `metrics: ['portions','revenue']` |
| "Đồ uống đóng góp bao nhiêu doanh thu mỗi khung giờ?" | `categories: ['Đồ uống']`, `group_by: ['slot']`, `metrics: ['revenue']` |
| "Thứ nào bếp chuẩn bị chậm nhất, chậm ở khung nào?" | `group_by: ['weekday','slot']`, `metrics: ['avg_prep_minutes']`, `sort_by: 'avg_prep_minutes'` |
| "Đơn hủy vì quá tải chủ yếu vào thứ mấy?" | `group_by: ['weekday','cancel_reason']`, `metrics: ['cancelled_orders']` |

### Câu hỏi nằm ngoài khả năng (server phải nói rõ là không trả lời được, không bịa số)
- Hỏi về **một khách cụ thể** ("em Minh Anh hay mua gì?"): `MASK_PII=true` nên không có tên, chỉ có mã khách đã băm.
- Hỏi về **lợi nhuận, giá vốn, chi phí nguyên liệu**: dữ liệu chỉ có doanh thu, không có giá vốn.
- Hỏi về **đánh giá hay mức hài lòng** của học sinh: app không lưu.
- Hỏi về ngày **chưa có dữ liệu** hoặc quá 92 ngày trong một lần gọi: trả thông báo "chưa đủ dữ liệu" hoặc yêu cầu thu hẹp khoảng ngày.
- Yêu cầu **sửa dữ liệu** (đổi giá, hủy đơn, tắt món): server chỉ đọc.

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
- [ ] `query_orders` không tham số trả đúng một dòng `totals` bằng `get_sales_summary` cùng khoảng ngày (cùng `revenue`, `orders`, `portions`).
- [ ] **Nhất quán giữa các tool**: với cùng bộ lọc, `get_top_items` khớp `query_orders` (`group_by: ['dish']`), `get_cancellations.total` khớp `cancelled_orders`, `get_prep_time_stats.prep.avg` khớp `avg_prep_minutes`.
- [ ] Bộ lọc: `weekdays: [5]` chỉ trả dữ liệu của các ngày thứ Sáu (kiểm bằng cách tính lại từ JSON); `daypart: 'evening'` chỉ có đơn của khung từ 17:00; `daypart` cộng `weekdays` là giao của hai điều kiện; tổng `lunch` cộng `evening` bằng kết quả không lọc.
- [ ] `categories: ['Đồ uống']` tính doanh thu theo dòng món (tổng `price × quantity` của đồ uống), không dùng `total` của đơn.
- [ ] `group_by` có hai chiều trả đủ tổ hợp có dữ liệu; `rowCount` đúng; `limit` nhỏ hơn số dòng thì `truncated = true` nhưng `totals` vẫn tính trên toàn bộ.
- [ ] Giá trị `group_by` hoặc `metrics` sai, `weekdays` ngoài 1 đến 7, `group_by` quá 2 chiều: trả `isError` kèm danh sách giá trị hợp lệ.
- [ ] `get_unsold_items` và `forecast_tomorrow` gọi kèm `daypart` thì không lỗi, kết quả có `ignoredFilters`.
- [ ] Mọi kết quả đọc đơn hàng có `appliedFilters` và `skipped`.
- [ ] Khi `MASK_PII=true`, không có kết quả tool nào chứa `userName` hoặc `userId` (kiểm bằng cách tìm chuỗi `demo_student_` và tên mẫu trong toàn bộ output).
- [ ] Không có `console.log` ra stdout trong mã nguồn (tìm bằng `grep`); không có `set`/`update`/`delete`/`add` Firestore trong mã nguồn.
- [ ] `npm run build` và `npm test` thành công.
- [ ] Kiểm giao thức: `npx @modelcontextprotocol/inspector node dist/index.js` (với `DATA_SOURCE=fixture`) liệt kê đủ **9 tool** (gồm `query_orders`) và gọi thử từng tool không lỗi.
- [ ] Nghiệm thu bằng Claude Code hoặc Claude Desktop: hỏi ít nhất 5 câu ở mục 8, câu trả lời có số liệu và đúng tool.

## 10. Thứ tự làm

1. **Khung dự án**: `package.json`, `tsconfig.json`, `config.ts`, `index.ts` chạy được với một tool tạm `ping` → build, kiểm tra bằng Inspector.
2. **Dữ liệu**: `types.ts`, `dataSource.ts`, `fixtureSource.ts`, `util/dates.ts` + test đọc dữ liệu và lọc theo ngày.
3. **Bộ máy chung và tool tổng quát**: `util/filters.ts` (schema bộ lọc dùng chung, mục 5A), `metrics/stats.ts`, `metrics/engine.ts` (lọc, nhóm, tính mọi chỉ số ở mục 6.0), tool `query_orders` + test kỹ (đây là nền của các tool sau).
4. **Tool lối tắt dựa trên engine** (6.1 đến 6.5: `get_sales_summary`, `get_top_items`, `get_orders_by_slot`, `get_cancellations`, `get_prep_time_stats`): mỗi tool là lớp mỏng gọi engine rồi thêm phần riêng (so kỳ trước, khung hay đầy, ngày chậm) + test nhất quán với `query_orders`.
5. **Tool dựa trên menu**: `get_unsold_items` (`metrics/unsold.ts`), `forecast_tomorrow` (`metrics/forecast.ts`), `list_menu` + test.
6. **`firestoreSource.ts`**, `util/privacy.ts` áp dụng cho mọi tool, `README.md` (cách chạy, đăng ký MCP), chạy Inspector và nghiệm thu mục 9.

Sau mỗi bước chạy `npm run build` và (từ bước 2) `npm test`; chỉ sang bước sau khi xanh.
