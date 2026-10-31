# Spec bổ sung: dữ liệu phân tích cho MCP báo cáo

Tài liệu này dành cho AI agent (Antigravity). Đọc cùng `docs/description.md` (spec chính) và `docs/design/design.html` (giao diện). Mục tiêu: sửa app để **ghi đủ dữ liệu từ bây giờ**, nhờ đó MCP báo cáo (làm sau, xem mục 8) trả lời được các câu hỏi của chủ căng tin. **Không code MCP server trong đợt này.**

Quy ước:
- Giữ nguyên kiến trúc hiện có (MVVM, Hilt, Repository, StateFlow). Không thêm màn hình mới.
- Mọi model là `data class` có giá trị mặc định (Firestore cần). Trường thời gian mới dùng `Timestamp?` mặc định `null`.
- Sau mỗi bước chạy `./gradlew assembleDebug` và sửa hết lỗi mới sang bước tiếp.
- Không sửa nội dung `docs/` trừ khi được yêu cầu.

## 1. Câu hỏi cần trả lời được và dữ liệu cần có

| Câu hỏi của chủ căng tin / khách | Trường dùng | Việc cần làm |
|---|---|---|
| Doanh thu, số suất hôm nay | `orders.total`, `items[].quantity`, `status`, `date` | Đã đủ |
| Khung giờ đông nhất | `orders.slotLabel`, `date` | Đã đủ |
| Món bán chạy (nhiều ngày) | `items[].dishKey`, `quantity` | Thêm `dishKey` |
| Món ế | `menuItems.dailyQuota`, `soldQty` | Thêm |
| Dự báo nguyên liệu ngày mai | `dishKey`, `date`, `soldQty` | Thêm |
| Đơn hủy: bao nhiêu, ai hủy, lý do, món nào | `cancelledBy`, `cancelReason`, `cancelledAt`, `items[]` | Thêm |
| Thời gian chuẩn bị, đơn chờ lâu | `preparingAt`, `readyAt`, `completedAt` | Thêm |
| Đơn không đến lấy | `cancelReason = NO_SHOW` | Thêm |
| Thống kê theo nhóm món | `items[].category` | Thêm |
| Hỏi đáp thực đơn: món chay dưới 30k, còn mấy suất | `menuItems.tags`, `price`, `dailyQuota - soldQty` | Thêm |

## 2. Thay đổi model (`data/model/`)

### 2.1 `Order.kt`: thêm các trường
```kotlin
val updatedAt: Timestamp? = null,
val preparingAt: Timestamp? = null,
val readyAt: Timestamp? = null,
val completedAt: Timestamp? = null,
val cancelledAt: Timestamp? = null,
val cancelledBy: String = "",     // "STUDENT" | "CANTEEN" | "" (chưa hủy)
val cancelReason: String = ""     // tên của CancelReason | "" (chưa hủy)
```
`createdAt` giữ nguyên.

### 2.2 Tạo mới `CancelReason.kt`
```kotlin
enum class CancelReason(val label: String) {
    SOLD_OUT("Hết món"),
    OVERLOAD("Quá tải"),
    NO_SHOW("Không đến lấy"),
    CHANGED_MIND("Học sinh đổi ý"),   // chỉ dùng khi học sinh tự hủy
    OTHER("Khác")
}
```
Lưu `name` (String) vào Firestore, tương tự `OrderStatus`.

### 2.3 `OrderItem.kt`: thêm
```kotlin
val category: String = "",
val dishKey: String = ""
```
Khi tạo đơn từ giỏ hàng phải điền hai trường này từ `MenuItem` (tìm nơi dựng `OrderItem`, thường là `CartRepository` / `CartViewModel` / `SlotPickerViewModel`).

### 2.4 `MenuItem.kt`: thêm
```kotlin
val dishKey: String = "",                 // slug ổn định theo món, vd "com-suon-nuong"
val tags: List<String> = emptyList(),     // "vegetarian","filling","quick","healthy","drink"
val dailyQuota: Int = 0,                  // 0 = không giới hạn
val soldQty: Int = 0
```
Thêm thuộc tính suy ra:
```kotlin
val remainingQty: Int?
    get() = if (dailyQuota > 0) (dailyQuota - soldQty).coerceAtLeast(0) else null
```
Lưu ý: `remainingQty` là thuộc tính tính toán, không ghi lên Firestore (đảm bảo Firestore không coi nó là trường; nếu cần, đánh dấu `@get:Exclude`).

## 3. Thay đổi logic (`data/repository/`)

Tái dùng các hàm hiện có, không viết lại từ đầu: `OrderRepositoryImpl.createOrder` (transaction), `cancelOrder`, `updateOrderStatus`, `verifyAndCompletePickupCode`, `MenuRepositoryImpl.updateStock`, và `slotCounterId` trong `TimeSlot.kt`.

### 3.1 `updateOrderStatus(orderId, newStatus)`
Ngoài `status`, luôn ghi `updatedAt = Timestamp.now()`. Theo trạng thái mới:
- `PREPARING` → ghi thêm `preparingAt`
- `READY` → ghi thêm `readyAt`
- `COMPLETED` → ghi thêm `completedAt`
Dùng một lần `update(mapOf(...))`.

### 3.2 `verifyAndCompletePickupCode`
Khi chuyển `COMPLETED`, ghi `status`, `completedAt`, `updatedAt` trong cùng một `update`.

### 3.3 `cancelOrder(orderId, cancelledBy, reason)`
Đổi chữ ký trong interface `OrderRepository` và `OrderRepositoryImpl`:
```kotlin
suspend fun cancelOrder(orderId: String, cancelledBy: String, reason: CancelReason): Result<Unit>
```
Trong transaction hiện có (giữ trả lại suất `timeSlotCounters`), bổ sung:
1. Đọc `menuItems/{menuItemId}` của **từng món trong đơn trước khi ghi** (Firestore yêu cầu mọi lệnh đọc trước lệnh ghi).
2. Nếu `reason != SOLD_OUT` và `dailyQuota > 0`: ghi `soldQty = max(0, soldQty - quantity)`.
3. Ghi vào đơn: `status = CANCELLED`, `cancelledAt`, `updatedAt`, `cancelledBy`, `cancelReason = reason.name`.
Cập nhật hai nơi gọi:
- `OrderStatusViewModel.cancelOrder` (học sinh): truyền `"STUDENT"` và `CancelReason.CHANGED_MIND`.
- `CanteenOrdersViewModel.cancelOrder` (căng tin): truyền `"CANTEEN"` và lý do người dùng chọn ở hộp thoại (mục 4.1).

### 3.4 `createOrder` (transaction hiện có)
Đã có đoạn kiểm tra `inStock`. Mở rộng, **đọc hết các document trước khi ghi**:
1. Với mỗi món trong đơn đọc `menuItems/{menuItemId}`.
2. Nếu `inStock == false` → lỗi như hiện tại ("Món X đã hết hàng").
3. Nếu `dailyQuota > 0` và `soldQty + quantity > dailyQuota` → lỗi `FirebaseFirestoreException("Món X chỉ còn N suất", ABORTED)` với `N = dailyQuota - soldQty`.
4. Sau khi mọi kiểm tra qua, ghi `soldQty = soldQty + quantity` cho từng món. Nếu `dailyQuota > 0` và `soldQty` mới `>= dailyQuota` thì ghi thêm `inStock = false`.

### 3.5 `MenuRepository`: thêm hàm
```kotlin
suspend fun updateQuota(menuItemId: String, dailyQuota: Int): Result<Unit>
```
Ghi `dailyQuota` (không âm). Nếu `dailyQuota > 0` và `soldQty >= dailyQuota` thì ghi `inStock = false`. Nếu quota mới lớn hơn `soldQty`, **không tự bật lại** `inStock` (căng tin bật bằng công tắc).

### 3.6 `SeedRepositoryImpl`
Đọc thêm từ `seed-menu.json`: `dishKey`, `tags` (mảng chuỗi), `dailyQuota` (mặc định 0). Gán `soldQty = 0`.

## 4. Thay đổi giao diện (theo token trong `docs/design/design.html`)

### 4.1 Căng tin, `CanteenOrdersScreen`: hộp thoại hủy đơn
- Nút "Hủy" (ở thẻ đơn) mở `AlertDialog` Material 3.
- Nội dung: tiêu đề "Hủy đơn #mã?", danh sách radio gồm các lý do **Hết món, Quá tải, Không đến lấy, Khác** (không hiện `CHANGED_MIND`).
- Chọn sẵn "Không đến lấy" nếu đơn đang `READY`, ngược lại chọn sẵn "Hết món".
- Hai nút: "Xác nhận hủy" (màu `error`) và "Giữ đơn" (text button). Bấm xác nhận mới gọi `cancelOrder(..., "CANTEEN", reason)`.

### 4.2 Căng tin, `CanteenMenuScreen`: số suất
- Dưới tên món hiện "Đã bán X/Y suất" nếu `dailyQuota > 0`, ngược lại "Không giới hạn".
- Thêm nút nhỏ "Sửa suất" mở `AlertDialog` có ô nhập số (bàn phím số). Xác nhận gọi `updateQuota`.
- Công tắc `inStock` giữ nguyên.

### 4.3 Học sinh, `MenuScreen`
- Món có `remainingQty != null && remainingQty in 1..5` hiện tag amber "Còn X suất" (dùng `StatusTag`/`RawTag` hiện có, màu secondaryContainer).
- `remainingQty == 0` hoặc `inStock == false` → "Hết món" như hiện tại.
- Thêm vào giỏ vượt số suất còn lại → chặn và hiện Snackbar "Chỉ còn N suất".

### 4.4 Mockup
Cập nhật `docs/design/design.html`: thêm hộp thoại hủy đơn và dòng "Đã bán X/Y suất" ở màn quản lý món (đã được đồng bộ trong repo; agent chỉ cần bám theo).

## 5. Seed và dữ liệu cũ
- `docs/data/seed-menu.json` đã có `dishKey`, `tags`, `dailyQuota` cho 10 món.
- Menu hôm nay đã seed bằng bản cũ **không có** trường mới (`seedIfEmpty` thấy đã có menu hôm nay nên không seed lại). Để áp dụng: xóa các document `menuItems` của hôm nay trong Firestore Console rồi đăng nhập lại để app seed.
- Đơn cũ thiếu trường mới là bình thường; code phải chịu được giá trị mặc định.

## 6. Thứ tự làm
1. Model (mục 2) → build.
2. Repository: `updateOrderStatus`, `verifyAndCompletePickupCode` (mục 3.1, 3.2) → build.
3. `cancelOrder` mới + 2 nơi gọi + hộp thoại hủy (mục 3.3, 4.1) → build.
4. `createOrder` kiểm tra số suất, `updateQuota`, seed (mục 3.4 đến 3.6) → build.
5. Giao diện số suất (mục 4.2, 4.3) → build.

## 7. Tiêu chí hoàn thành
- [ ] Đơn mới có `createdAt`; mỗi lần đổi trạng thái ghi đúng `preparingAt`, `readyAt`, `completedAt` và `updatedAt`.
- [ ] Căng tin hủy đơn: lưu `cancelledBy = CANTEEN` và lý do đã chọn; học sinh hủy: `STUDENT` + `CHANGED_MIND`; cả hai có `cancelledAt`.
- [ ] Hủy đơn trả lại suất khung giờ (`timeSlotCounters`) và `soldQty` của món (trừ lý do `SOLD_OUT`).
- [ ] `OrderItem` lưu `category`, `dishKey`; `MenuItem` có `dishKey`, `tags`, `dailyQuota`, `soldQty`.
- [ ] Đặt vượt số suất còn lại bị chặn kèm thông báo; món chạm hạn mức tự chuyển "Hết món".
- [ ] Màn quản lý món hiện "Đã bán X/Y" và sửa được hạn mức; hộp thoại hủy có đủ 4 lý do.
- [ ] Kiểm tra trên Firestore Console: document `orders` và `menuItems` có đủ trường mới, giá trị đúng.
- [ ] `./gradlew assembleDebug` thành công.

## 8. Hợp đồng đọc cho MCP (làm sau, KHÔNG code bây giờ)
MCP server chỉ-đọc (Firebase Admin SDK, chạy local, service account key ngoài repo). Đọc `orders` theo khoảng `date` (truy vấn một điều kiện nên không cần composite index) rồi tổng hợp trong server.

| Chỉ số | Cách tính |
|---|---|
| Doanh thu | tổng `total` của đơn `COMPLETED` |
| Số suất bán | tổng `items[].quantity` của đơn `COMPLETED` |
| Khung giờ đông | đếm đơn không `CANCELLED` theo `slotLabel` |
| Bán chạy | tổng `quantity` theo `dishKey` (không tính `CANCELLED`) |
| Món ế | cuối ngày `soldQty / dailyQuota < 0.5`, hoặc không có đơn nào |
| Thời gian chuẩn bị | `readyAt - preparingAt` |
| Chờ bắt đầu làm | `preparingAt - createdAt` |
| Chờ khách nhận | `completedAt - readyAt` |
| Hủy | đếm theo `cancelReason`, `cancelledBy`, và theo `dishKey` của món trong đơn hủy |
| Không đến lấy | `cancelReason = NO_SHOW` |
| Dự báo ngày mai | trung bình `soldQty` của cùng thứ trong 4 tuần gần nhất, theo `dishKey`, cộng 10% dự phòng; dưới 2 tuần dữ liệu thì trả "chưa đủ dữ liệu" |
| Hỏi đáp thực đơn | `menuItems` hôm nay có `inStock`, `remainingQty != 0`, lọc theo `tags` và `price` |

Công cụ dự kiến: `get_sales_summary`, `get_top_items`, `get_orders_by_slot`, `get_cancellations`, `get_prep_time_stats`, `get_unsold_items`, `forecast_tomorrow`, `list_menu`. Che `userName`/email trong kết quả. Đặt tại `tools/mcp-firestore/`.

## 9. Trường bổ sung của v2 (voucher, điểm, giá vốn)

Các trường dưới đây thêm bởi kế hoạch v2 (`docs/features-v2-plan.md`, mục 2). Chúng không thay đổi các công thức ở mục 8, chỉ thêm chỉ số về giảm giá và lãi gộp.

| Trường | Ở đâu | Dùng để tính |
|---|---|---|
| `subtotal` | `orders` | tạm tính trước giảm giá (đơn cũ thiếu thì coi bằng `total`) |
| `discountAmount` | `orders` | tổng giảm giá (voucher + điểm); `total = subtotal − discountAmount` |
| `voucherCode` | `orders` | thống kê mã nào được dùng, giảm bao nhiêu |
| `pointsUsed` | `orders` | điểm rèn luyện đã dùng (1 điểm = 500đ) |
| `costPrice` | `menuItems` | giá vốn một suất, dùng cho chi phí và lãi gộp |

Công thức mới cho MCP (`docs/mcp-server-design.md`, view `v_pnl_daily`), tính trên các đơn **không bị `CANCELLED`**:

| Chỉ số | Công thức |
|---|---|
| Doanh thu gộp | tổng `price × quantity` của các dòng món |
| Giảm giá | tổng `discountAmount` |
| Doanh thu thuần | doanh thu gộp − giảm giá |
| Chi phí | tổng `costPrice × quantity` (món chưa có `costPrice` tính 0, phải ghi chú) |
| Lãi gộp | doanh thu thuần − chi phí |

Lưu ý: `orders.total` đã là số tiền sau giảm giá, nên doanh thu ở mục 8 (đơn `COMPLETED`) là **doanh thu thuần**. Số "Doanh thu thuần" ở tab Nhà bếp lớn hơn vì tính cả đơn chưa giao.
