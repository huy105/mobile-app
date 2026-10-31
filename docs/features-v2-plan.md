# Kế hoạch tính năng v2 (cho Antigravity code)

Tài liệu dành cho AI agent (Antigravity). Đọc cùng `docs/description.md` (đặc tả gốc, mục 4 đến 6), `docs/design/design.html` (mockup S2, S3, C5, C6) và `docs/data/seed-menu.json` (dữ liệu mẫu đã cập nhật). Code **thêm vào app hiện có**, không viết lại phần đã chạy. Đây là dự án học tập, không kinh doanh thật; thanh toán vẫn là giả lập ("trả tại quầy").

## 0. Phạm vi

**Làm trong v2:**
1. Gợi ý món được đặt nhiều hôm nay (từ 10 suất trở lên).
2. 20 món có ảnh thật (dữ liệu đã có sẵn trong `seed-menu.json`, chỉ cần app đọc đúng).
3. Voucher và quy đổi điểm rèn luyện (giả lập) khi đặt đơn.
4. Tab **Nhà bếp** cho vai trò CANTEEN (số suất cần làm, doanh thu, chi phí, lãi).
5. Hoạt ảnh ở các bước chính.

**KHÔNG làm (đã cân nhắc và bỏ vì quá phức tạp cho bản demo):** giá thay đổi theo số người đặt (giảm hoặc tăng giá động), giờ chốt đơn 9h, hạn thanh toán 30 phút tự hủy, "không cho đặt lại", công thức nguyên liệu, tính khối lượng nguyên liệu (kg), nguyên liệu dùng chung giữa các món, app nhà bếp riêng, thanh toán thật. Giá món **cố định**.

## 1. Giai đoạn làm (tuần tự)

Sau **mỗi** giai đoạn: `./gradlew assembleDebug` phải xanh, và thử màn mới ở **cả hai nhánh bề rộng**: điện thoại (< 600dp) và tablet (≥ 600dp). Chỉ sang giai đoạn sau khi cả hai đều ổn.

1. **Dữ liệu và mô hình** + **gợi ý món đặt nhiều** (mục 2, 3, 4).
2. **Voucher và điểm** (mục 5).
3. **Tab Nhà bếp** (mục 6).
4. **Hoạt ảnh** (mục 7), làm cuối vì chạm nhiều màn.

## 2. Mô hình dữ liệu (chỉ thêm, tương thích ngược)

Mọi trường mới có giá trị mặc định để đơn và món cũ vẫn đọc được.

| Nơi | Trường mới | Ý nghĩa |
|---|---|---|
| `MenuItem` | `costPrice: Long = 0` | giá vốn một suất (VND); 0 = chưa có giá vốn |
| `Order` | `subtotal: Long = 0` | tạm tính (tổng `price × quantity` trước giảm giá) |
| `Order` | `discountAmount: Long = 0` | tổng giảm giá (voucher + điểm) |
| `Order` | `voucherCode: String = ""` | mã voucher đã áp, rỗng nếu không dùng |
| `Order` | `pointsUsed: Int = 0` | số điểm đã dùng |
| `User` | `points: Int = 0` | điểm rèn luyện (giả lập) |

Quy tắc:
- `total = subtotal − discountAmount` và không bao giờ âm. Đơn cũ có `subtotal == 0` thì coi `subtotal = total`.
- Các giá trị suy ra (viết thành thuộc tính tính toán, không lưu): `pointsDiscount = pointsUsed × 500`, `voucherDiscount = discountAmount − pointsDiscount`.
- Giá bán tham chiếu: `price ≈ costPrice × 1,5` (cộng 50% công chế biến), làm tròn nghìn đồng. Dữ liệu mẫu đã tuân theo; app không tự tính giá bán.

**Collection `vouchers/{CODE}`** (id là mã viết hoa):

| Trường | Kiểu | Ý nghĩa |
|---|---|---|
| `title` | String | mô tả hiển thị |
| `type` | String | `PERCENT` hoặc `AMOUNT` |
| `value` | Long | phần trăm (PERCENT) hoặc số tiền giảm (AMOUNT) |
| `minOrder` | Long | tạm tính tối thiểu, 0 = không yêu cầu |
| `maxDiscount` | Long | trần giảm cho PERCENT, 0 = không giới hạn |
| `validUntil` | String | `yyyy-MM-dd`, dùng được đến hết ngày này |
| `maxUses` | Int | tổng số lượt dùng tối đa, 0 = không giới hạn |
| `usedCount` | Int | số lượt đã dùng |
| `active` | Boolean | còn bật |

**Collection `voucherRedemptions/{CODE}_{uid}`**: `code`, `userId`, `orderId`, `date`. Sự tồn tại của document này nghĩa là học sinh đó đã dùng mã (mỗi học sinh một lần mỗi mã).

## 3. Dữ liệu mẫu (seed)

`docs/data/seed-menu.json` đã được cập nhật (bản sao nằm ở `app/src/main/assets/seed-menu.json`, **hai file phải giống nhau**). Agent chỉ cần sửa `SeedRepositoryImpl` để đọc đủ:

- **20 món** (10 món cũ + 10 món mới), mỗi món có `costPrice`. Đọc và lưu `costPrice` vào `menuItems`.
- Mảng `vouchers` (4 mã: `WELCOME10`, `GIAM5K`, `SINHVIEN`, `HETHAN`). Mỗi mã trong file có `validDays` (số ngày hiệu lực kể từ lúc seed, **âm nghĩa là đã hết hạn**, dùng cho mã `HETHAN` để thử lỗi). Khi seed, đổi thành `validUntil = ngày hôm nay + validDays` (định dạng `yyyy-MM-dd`), rồi ghi vào `vouchers/{code}` với `usedCount = 0`. Seed vouchers khi collection `vouchers` trống (độc lập với việc `menuItems` có trống hay không, vì Firestore của người dùng đã có menu từ trước).
- **Menu cũ đã có sẵn trong Firestore** thì `seedIfEmpty()` không tự thêm 10 món mới và không thêm `costPrice` cho món cũ. Cần một cách nạp thêm: thêm hàm `SeedRepository.seedMissingMenuItems()`, so khớp theo `dishKey` của **hôm nay**: món nào chưa có thì thêm, món có rồi mà thiếu `costPrice` thì cập nhật `costPrice` (không đụng `soldQty`, `inStock`). Gọi hàm này cùng chỗ với `seedIfEmpty()` sau khi đăng nhập.
- **Điểm khởi đầu**: học sinh đăng nhập mà `users/{uid}` **chưa có trường `points`** thì cấp 100 điểm một lần (`SeedRepository.grantStartingPointsIfMissing(uid)`). Không ghi đè nếu trường đã tồn tại (kể cả bằng 0).
- Quy tắc Firestore demo giữ nguyên (`request.auth != null`).

## 4. Gợi ý món được đặt nhiều

- Hằng số `POPULAR_THRESHOLD = 10`. Một món "được đặt nhiều" khi `soldQty >= 10` (số **suất** đã bán trong ngày, trường đã có sẵn và được cập nhật trong transaction đặt đơn).
- `MenuScreen`: thêm hàng ngang cuộn "🔥 Gợi ý hôm nay" ở **đầu** danh sách. Gồm tối đa 5 món `inStock` có `soldQty >= 10`, sắp theo `soldQty` giảm dần. Thẻ trong hàng: ảnh, tên, giá, dòng "Đã đặt N suất", nút thêm vào giỏ. Không có món nào đủ điều kiện thì **ẩn cả hàng** (không để khoảng trống).
- `MenuItemCard`: tag "🔥 Được đặt nhiều" cạnh tên khi `soldQty >= 10`. Cập nhật realtime vì danh sách món đã lắng nghe Firestore.
- Kiểm thử bằng cách đặt đủ 10 suất một món (nhiều đơn hoặc nhiều suất trong một đơn), nên có thể kiểm bằng tài khoản học sinh demo.
- Bố cục xem mockup S2 trong `docs/design/design.html`.

## 5. Voucher và điểm rèn luyện

### 5.1 Quy tắc tính

1. `subtotal` = tổng `price × quantity` của giỏ.
2. **Voucher** (nếu có): hợp lệ khi `active`, `validUntil >= hôm nay`, `subtotal >= minOrder`, học sinh chưa dùng (không có `voucherRedemptions/{CODE}_{uid}`), và `maxUses == 0` hoặc `usedCount < maxUses`.
   - `PERCENT`: `voucherDiscount = floor(subtotal × value / 100)`, rồi `min(…, maxDiscount)` nếu `maxDiscount > 0`.
   - `AMOUNT`: `voucherDiscount = min(value, subtotal)`.
3. **Điểm** (khi bật công tắc): 1 điểm = 500đ. Giảm tối đa 20% `subtotal` và không quá phần còn lại sau voucher.
   - `capVnd = min(subtotal − voucherDiscount, floor(subtotal × 20 / 100))`
   - `pointsUsed = min(user.points, floor(capVnd / 500))` (dùng nhiều điểm nhất có thể, không có ô chọn số điểm).
   - `pointsDiscount = pointsUsed × 500`.
4. `discountAmount = voucherDiscount + pointsDiscount`; `total = subtotal − discountAmount` (≥ 0).

Đặt phép tính này trong một object thuần (ví dụ `PricingCalculator`) dùng chung cho màn giỏ (xem trước) và `createOrder` (tính lại thật), để hai nơi luôn ra cùng một số, và test được.

### 5.2 Màn giỏ hàng (`CartScreen`)

Thêm khối **"Ưu đãi"** phía trên phần tóm tắt tiền (xem mockup S3):
- Ô nhập mã voucher và nút "Áp dụng". Nhập không phân biệt hoa thường (đổi thành chữ hoa).
- Hàng chip các voucher còn dùng được (đang `active`, chưa hết hạn, chưa dùng bởi học sinh này); bấm chip là áp mã.
- Dòng điểm: "Dùng điểm rèn luyện (còn N điểm ≈ X đ)" với công tắc, mặc định **tắt**. Khi bật hiện "Dùng M điểm, giảm Y đ" theo công thức 5.1. Không đủ điều kiện (không có điểm, hoặc giảm bằng 0) thì công tắc bị vô hiệu và ghi lý do ngắn.
- Phần tóm tắt: **Tạm tính**, **Voucher** (số âm, chỉ hiện khi có), **Điểm** (số âm, chỉ hiện khi có), **Tổng**. Đổi giỏ hoặc mã thì tính lại ngay.
- Lỗi voucher hiện dưới ô nhập, tiếng Việt cụ thể:
  - không tìm thấy mã hoặc `active = false`: "Mã không tồn tại"
  - hết hạn: "Mã đã hết hạn"
  - `subtotal < minOrder`: "Đơn tối thiểu {minOrder}đ để dùng mã này"
  - đã dùng: "Bạn đã dùng mã này rồi"
  - hết lượt: "Mã đã hết lượt sử dụng"
- Truyền `voucherCode` và `usePoints` sang bước chọn giờ và `createOrder`; **không** truyền tổng tiền đã giảm như một giá trị đáng tin.

### 5.3 `OrderRepository.createOrder` (tính lại phía server trong transaction)

Thêm tham số `voucherCode: String?` và `usePoints: Boolean`. Trong **cùng** Firestore transaction đang có (khung giờ, bộ đếm, `soldQty`):
1. **Đọc hết trước khi ghi** (quy tắc của Firestore transaction): `timeSlots`, `timeSlotCounters`, các `menuItems`, và thêm `vouchers/{CODE}`, `voucherRedemptions/{CODE}_{uid}`, `users/{uid}`.
2. Tính lại `subtotal` từ `price` của các `menuItems` vừa đọc (không tin giá do client gửi), rồi `voucherDiscount`, `pointsUsed` theo mục 5.1. Vi phạm điều kiện nào thì ném lỗi tiếng Việt (cùng bộ thông báo ở 5.2; điểm không đủ thì "Không đủ điểm rèn luyện").
3. Ghi: tăng `usedCount` của voucher, tạo `voucherRedemptions/{CODE}_{uid}` (`code`, `userId`, `orderId`, `date`), trừ `pointsUsed` khỏi `users/{uid}.points`; ghi order với `subtotal`, `discountAmount`, `voucherCode`, `pointsUsed`, `total`.
4. Đơn không dùng voucher và điểm thì hành vi như cũ (`subtotal = total`, `discountAmount = 0`).

### 5.4 `cancelOrder` hoàn trả

Trong transaction hủy (học sinh hoặc căng tin hủy), nếu đơn có `voucherCode` thì: xóa `voucherRedemptions/{CODE}_{userId của đơn}` và giảm `usedCount` (không xuống dưới 0). Nếu `pointsUsed > 0` thì cộng lại vào `users/{userId của đơn}.points`. Vẫn giữ nguyên các bước hoàn trả suất khung giờ và `soldQty` hiện có. Đọc hết trước khi ghi.

### 5.5 Hiển thị

- `OrderStatusScreen` và `HistoryScreen` (chi tiết đơn): thêm dòng Tạm tính, Voucher (kèm mã), Điểm (kèm số điểm) khi có giảm giá, rồi Tổng. Đơn cũ (không có giảm giá) hiển thị như trước.
- Số điểm của học sinh hiện ở khối "Ưu đãi" trong giỏ (chip "⭐ N điểm"); không cần màn riêng cho điểm.
- Vì đây là demo, không có điểm cộng thêm sau khi mua; điểm chỉ tiêu và được hoàn khi hủy.

## 6. Tab Nhà bếp (vai trò CANTEEN)

- Tab thứ 5 trong `CanteenRootScreen`, tên "Nhà bếp" (icon 🧑‍🍳). Thứ tự rail/bottom bar: **Tổng quan, Đơn hàng, Thực đơn, Trả đồ, Nhà bếp**. `KitchenScreen` + `KitchenViewModel` (state qua `StateFlow`, realtime).
- **Chỉ đọc.** Dùng lại thanh chọn ngày của Dashboard: tách `DashboardDateBar` thành component dùng chung (ví dụ `ui/common/components/DateBar.kt`), giữ nguyên hành vi (‹ › nút Hôm nay, `DatePickerDialog`, chặn ngày tương lai).
- Dữ liệu: các **đơn không bị `CANCELLED`** của ngày chọn và `menuItems` của ngày đó (để lấy `costPrice`).
- Công thức (đặt trong object thuần, ví dụ `KitchenCalculator`, để test được):
  - Nhóm theo món (`dishKey`, thiếu thì `name`): `portions = Σ quantity`; `revenue = Σ price × quantity`; `cost = costPrice × portions` (`costPrice` lấy từ `menuItems` cùng ngày, khớp theo `menuItemId`, không có thì theo `dishKey`).
  - **Doanh thu thuần** = Σ `revenue` các món − Σ `discountAmount` của các đơn không hủy.
  - **Chi phí** = Σ `cost`.
  - **Lãi gộp** = Doanh thu thuần − Chi phí.
  - **Giảm giá đã cho** = Σ `discountAmount`.
  - Món có `costPrice == 0` hoặc không tìm thấy: tính chi phí 0 và hiện cảnh báo "N món chưa có giá vốn" (không crash, không bịa số).
- UI (xem mockup C6): 4 thẻ số (Doanh thu thuần, Chi phí, Lãi gộp, Giảm giá đã cho); bảng "Cần làm" gồm Món, Số suất, Giá vốn, Doanh thu, sắp theo `portions` giảm dần, dòng cuối là tổng. Số tiền định dạng VND như các màn khác.
- Trạng thái rỗng (ngày không có đơn): các số hiện 0, bảng hiện "Chưa có đơn nào trong ngày này".
- Màn hẹp (< 600dp): 4 thẻ xếp lưới 2×2, bảng cuộn **theo trang** (không đặt vùng cuộn dọc trong vùng cuộn dọc); bảng thu thành danh sách thẻ nếu cần.
- Lưu ý: tab Tổng quan (Dashboard) tính doanh thu từ đơn `COMPLETED`; tab Nhà bếp tính từ mọi đơn không hủy (vì bếp phải làm cả đơn chưa giao). Hai số khác nhau là đúng, ghi chú ngắn "tính cả đơn chưa giao" trên thẻ Doanh thu của tab Nhà bếp.

## 7. Hoạt ảnh (Jetpack Compose)

Mục tiêu là app sinh động nhưng nhẹ. Mỗi hoạt ảnh 200 đến 400ms, dùng API có sẵn trong Compose BOM hiện tại (2024.06.00, tức foundation 1.6.x và navigation-compose 2.7.7). **Lưu ý phiên bản:** dùng `Modifier.animateItemPlacement()` (cần `@OptIn(ExperimentalFoundationApi::class)`), **không** dùng `animateItem()` vì nó chỉ có từ foundation 1.7.

| Nơi | Hoạt ảnh |
|---|---|
| Danh sách món (`MenuScreen`) | Mỗi thẻ xuất hiện lần lượt: fade + trượt lên nhẹ, trễ `index × 40ms`, chỉ áp cho 8 thẻ đầu (phần sau hiện ngay). Danh sách đổi thứ tự hoặc thêm bớt thì `animateItemPlacement()`. |
| Nút "+" thêm giỏ | Nảy nhẹ (scale 1 → 1,15 → 1) khi bấm. |
| Badge giỏ | `AnimatedContent` đổi số có trượt dọc ngắn. |
| Tag 🔥 | Nhấp nháy rất nhẹ (alpha 0,7 ↔ 1, chu kỳ chậm khoảng 1,6 giây). Đây là hoạt ảnh lặp duy nhất ở danh sách. |
| Giỏ hàng | Xóa món: `AnimatedVisibility` với `shrinkVertically + fadeOut`. Số tiền (Tạm tính, Tổng): đếm mượt từ giá trị cũ sang mới (`animateIntAsState` hoặc `Animatable`, 300ms). Khối "Ưu đãi" mở rộng bằng `AnimatedVisibility`. |
| Chọn giờ (`SlotPickerScreen`) | Viền và nền khung đổi bằng `animateColorAsState` (200ms); radio co giãn khi chọn. |
| Trạng thái đơn (`OrderStatusScreen`) | Thanh nối của stepper tô dần theo tiến độ (animate phần trăm); bước hiện tại phát sáng nhẹ; thẻ mã nhận đồ vào bằng scale (0,9 → 1) + fade; khi `COMPLETED` dấu ✓ hiện bằng scale. |
| Điều hướng (`NavGraph`) | `NavHost` dùng `enterTransition` / `exitTransition` / `popEnterTransition` / `popExitTransition`: trượt ngang một phần tư bề rộng + mờ, 300ms. |
| Dashboard, Nhà bếp | Số trên 4 thẻ đếm từ 0 lên giá trị khi nạp dữ liệu (400ms); đổi ngày thì đếm từ giá trị cũ sang mới. |

Quy tắc:
- Không có hoạt ảnh lặp vô hạn nào khác ngoài tag 🔥 và bước hiện tại của stepper.
- Hoạt ảnh không được chặn thao tác (bấm được ngay), không làm giật khi cuộn danh sách dài.
- Không dùng thư viện hoạt ảnh mới; chỉ dùng `androidx.compose.animation`. Hệ thống đã tắt animation (Developer options) thì Compose tự chạy tức thì, không cần xử lý thêm.

## 8. Ràng buộc chung (bài học từ lỗi crash trước đây)

- Mọi màn mới hoặc đã sửa phải thử ở **cả hai nhánh bề rộng** (< 600dp và ≥ 600dp). Lỗi trước đây của Dashboard chỉ xảy ra trên điện thoại.
- **Không đặt `verticalScroll` hoặc `LazyColumn` bên trong một vùng cuộn dọc khác.** Layout điện thoại cuộn cả trang thì thẻ bên trong không tự cuộn; layout tablet có chiều cao cố định thì thẻ được phép cuộn riêng.
- Mỗi màn có `ViewModel` riêng, state qua `StateFlow`; không gọi Firebase từ Composable. Phép tính (giá, bếp) đặt trong object thuần, không import Firebase, có test đơn vị.
- Chuỗi hiển thị bằng tiếng Việt, đặt trong `strings.xml`.
- Giữ đúng phong cách giao diện của `docs/design/design.html` (màu, font, bo góc).
- Khi thêm trường mới vào model Firestore, luôn có giá trị mặc định (để đọc dữ liệu cũ không lỗi) và dùng `@get:Exclude` cho thuộc tính tính toán.

## 9. Tiêu chí hoàn thành

**Dữ liệu và gợi ý**
- [ ] Sau khi seed (hoặc `seedMissingMenuItems()`), hôm nay có đủ 20 món, mọi món có `costPrice > 0` và ảnh hiển thị; có 4 voucher trong `vouchers`.
- [ ] Học sinh mới đăng nhập có `points = 100` đúng một lần; đăng nhập lại không cấp thêm.
- [ ] Đặt đủ 10 suất một món thì hàng "🔥 Gợi ý hôm nay" xuất hiện và món đó có tag "🔥 Được đặt nhiều"; chưa đủ thì không có hàng nào.

**Voucher và điểm**
- [ ] `WELCOME10` với tạm tính 60.000đ giảm 6.000đ; với tạm tính 200.000đ giảm tối đa 15.000đ; với tạm tính 25.000đ báo "Đơn tối thiểu 30.000đ".
- [ ] `GIAM5K` giảm 5.000đ (tạm tính ≥ 20.000đ); `SINHVIEN` giảm 3.000đ; `HETHAN` báo "Mã đã hết hạn"; mã lạ báo "Mã không tồn tại".
- [ ] Dùng một mã xong, đặt lại đơn khác với cùng mã (cùng học sinh) báo "Bạn đã dùng mã này rồi"; học sinh khác vẫn dùng được.
- [ ] Bật điểm với tạm tính 100.000đ và 100 điểm: giảm 20.000đ (dùng 40 điểm); tổng không âm, không vượt 20% tạm tính.
- [ ] `total = subtotal − discountAmount` trên đơn trong Firestore; số tiền hiển thị ở giỏ, trạng thái đơn, lịch sử và Nhà bếp khớp nhau.
- [ ] Hủy đơn đã dùng voucher và điểm (cả học sinh hủy lẫn căng tin hủy) thì mã dùng lại được và điểm được cộng lại; không hoàn thành thì không đổi.
- [ ] Tính lại phía transaction: sửa tay giá trên client (nếu thử) vẫn không làm đơn rẻ hơn giá đúng.

**Nhà bếp**
- [ ] Tab Nhà bếp là tab thứ 5; Doanh thu thuần, Chi phí, Lãi gộp, Giảm giá đã cho khớp tính tay từ các đơn không hủy trong Firestore.
- [ ] Tổng số suất trong bảng "Cần làm" bằng tổng `quantity` của các đơn không hủy; món thiếu `costPrice` có cảnh báo.
- [ ] Chọn ngày cũ (đã nạp lịch sử giả) thì số đổi đúng; ngày không có đơn hiện trạng thái rỗng, không crash.

**Giao diện và hoạt ảnh**
- [ ] Các màn S2, S3, C1, C5, C6 khớp `docs/design/design.html` trên điện thoại và tablet; không có vùng cuộn lồng nhau.
- [ ] Các hoạt ảnh ở mục 7 chạy mượt, không chặn thao tác; chỉ có hai hoạt ảnh lặp (tag 🔥, bước hiện tại).
- [ ] `./gradlew assembleDebug` thành công; thử trên cả điện thoại và tablet.
