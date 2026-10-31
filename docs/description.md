# Canteen Order App (Demo) - Spec cho Antigravity

Tài liệu này là đặc tả để AI agent (Antigravity) tự code ứng dụng bằng **Kotlin**. Làm tuần tự theo mục 8, mỗi bước phải build được.

## 1. Mục tiêu và phạm vi

Ứng dụng đặt đồ ăn trưa cho căng tin trường học. Giải quyết: xếp hàng đông giờ ra chơi, hết món bất ngờ, thời gian nghỉ ngắn.

Mục tiêu demo: chạy mượt luồng **Chọn món → Chọn giờ nhận → Nhận mã → Lấy đồ**.

### Có trong demo
- 1 app Android, 2 vai trò: **STUDENT** (học sinh/sinh viên) và **CANTEEN** (căng tin).
- Học sinh: xem menu theo ngày, giỏ hàng, chọn khung giờ nhận, xác nhận đặt (thanh toán giả lập), mã nhận đồ, theo dõi trạng thái đơn realtime, lịch sử đơn.
- Căng tin: màn tổng quan số liệu hôm nay (doanh thu, số đơn, khung giờ, món sắp hết), danh sách đơn theo khung giờ realtime, đổi trạng thái đơn, bật/tắt món hết hàng, nhập mã 4 số để hoàn thành đơn.

### Không làm
Thanh toán thật (VietQR/ví), Canteen Pass, tích điểm, Group Order, Combo Builder, bảng xếp hạng, "Hôm nay ăn gì", báo cáo doanh thu nhiều ngày (app chỉ có tab Tổng quan của riêng hôm nay, báo cáo nhiều ngày để MCP lo), admin portal, push notification, quét camera QR.

## 2. Tech stack (bắt buộc)

- Ngôn ngữ: **Kotlin**. UI: **Jetpack Compose + Material 3**. Không dùng XML layout.
- Kiến trúc: MVVM, `ViewModel` + `StateFlow`, Repository pattern.
- DI: **Hilt**. Bất đồng bộ: Coroutines + Flow.
- Điều hướng: Navigation Compose.
- Backend: **Firebase Authentication (Email/Password)** + **Cloud Firestore** (dùng Firebase BoM, bản Kotlin/KTX).
- Tải ảnh: Coil (ảnh món lấy từ `imageUrl`, có placeholder).
- minSdk 24, compileSdk/targetSdk mới nhất. Một module `app`. Gradle Kotlin DSL + version catalog.

## 3. Cấu trúc project

### Thư mục repo (mở thư mục gốc `mobile-app/` trong Antigravity)

```
mobile-app/                       (gốc repo, cũng là gốc project Android)
├─ docs/                          (tài liệu đầu vào, đã có sẵn, agent chỉ đọc, không sửa)
│  ├─ description.md              (file spec này)
│  ├─ design/
│  │  └─ design.html              (mockup giao diện, xem mục 5A)
│  └─ data/
│     └─ seed-menu.json           (món, ảnh, khung giờ mẫu, xem mục 7)
├─ app/                           (module Android, agent tạo)
│  ├─ google-services.json        (người dùng tự đặt vào, xem mục 8)
│  ├─ build.gradle.kts
│  └─ src/main/...                (code Kotlin theo cây package bên dưới)
├─ tools/seed-history/            (script Node nạp dữ liệu lịch sử giả vào Firestore, có sẵn, agent không sửa)
├─ tools/mcp-firestore/           (MCP server báo cáo, làm sau demo theo docs/mcp-server-design.md)
├─ gradle/libs.versions.toml      (version catalog)
├─ build.gradle.kts
├─ settings.gradle.kts
└─ .gitignore                     (phải chứa google-services.json, local.properties, build/, .gradle/)
```

Quy tắc: không đổi tên hoặc di chuyển thư mục `docs/`. Project Android tạo ngay tại gốc `mobile-app/`, không tạo thư mục con bao ngoài.

### Cây package trong module `app`

Package gốc: `com.example.canteen`

```
com.example.canteen
├─ CanteenApp.kt            (@HiltAndroidApp)
├─ MainActivity.kt
├─ di/                      (FirebaseModule, RepositoryModule)
├─ data/
│  ├─ model/                (User, MenuItem, TimeSlot, Order, OrderItem, enums)
│  └─ repository/           (AuthRepository, MenuRepository, SlotRepository, OrderRepository, SeedRepository)
├─ navigation/              (NavGraph, Routes)
└─ ui/
   ├─ common/               (theme, components dùng chung, LoginScreen + LoginViewModel)
   ├─ student/              (MenuScreen, CartScreen, SlotPickerScreen, OrderStatusScreen, HistoryScreen + ViewModel)
   └─ canteen/              (DashboardScreen, OrdersScreen, MenuManageScreen, VerifyCodeScreen + ViewModel)
```

## 4. Data model (Firestore)

Tất cả model là Kotlin `data class` có giá trị mặc định (để Firestore deserialize).

| Collection | Trường |
|---|---|
| `users/{uid}` | `name: String`, `email: String`, `role: "STUDENT" \| "CANTEEN"` |
| `menuItems/{id}` | `name`, `description`, `price: Long` (VND), `imageUrl`, `category`, `inStock: Boolean`, `date: String` (yyyy-MM-dd), `dishKey`, `tags: List<String>`, `dailyQuota: Int` (0 = không giới hạn), `soldQty: Int` |
| `timeSlots/{id}` | `label` (vd "11:15 - 11:30"), `startMinute: Int`, `endMinute: Int`, `maxOrders: Int` |
| `timeSlotCounters/{slotId}_{yyyy-MM-dd}` | `slotId`, `date`, `count: Int` (số đơn đã đặt của khung giờ trong ngày đó; sang ngày mới là document mới nên tự về 0) |
| `orders/{id}` | `userId`, `userName`, `items: List<OrderItem>`, `slotId`, `slotLabel`, `pickupCode: String` (4 số), `status`, `total: Long`, `date: String`, `createdAt: Timestamp`, và các trường theo dõi: `updatedAt`, `preparingAt`, `readyAt`, `completedAt`, `cancelledAt` (Timestamp?), `cancelledBy` (STUDENT/CANTEEN), `cancelReason` (tên `CancelReason`) |

`OrderItem`: `menuItemId`, `name`, `price`, `quantity`, `category`, `dishKey`.

Chi tiết các trường theo dõi, enum `CancelReason` và cách ghi từng trường nằm trong **`docs/analytics-data-plan.md`** (bắt buộc làm theo).

`OrderStatus` enum: `RECEIVED`, `PREPARING`, `READY`, `COMPLETED`, `CANCELLED`. Lưu dưới dạng String.

## 5. Màn hình và luồng

### Chung
- **LoginScreen**: email + mật khẩu. Sau khi đăng nhập đọc `users/{uid}.role` rồi điều hướng: STUDENT vào luồng học sinh, CANTEEN vào luồng căng tin. Có nút đăng xuất ở mỗi luồng: **nút dạng pill có chữ "Đăng xuất" kèm icon ⎋**, cao 40dp, nền `errorContainer`, viền 1.5dp và chữ màu `error`, đặt ở góc phải thanh trên cùng (component dùng chung `LogoutButton` trong `ui/common/components/`). Không dùng icon trơn không có chữ. Ghi nhớ phiên đăng nhập.

### Học sinh (STUDENT)
1. **MenuScreen**: danh sách món của ngày hôm nay (lọc theo `date`), nhóm theo `category`. Món `inStock = false` làm mờ, gắn nhãn "Hết món", không thêm được. Nút thêm vào giỏ, badge số lượng trên icon giỏ.
2. **CartScreen**: chỉnh số lượng, xóa món, hiện tổng tiền. Nút "Chọn giờ nhận".
3. **SlotPickerScreen**: danh sách khung giờ, **chia hai nhóm tiêu đề "Buổi trưa" (`startMinute` < 840, tức trước 14:00) và "Chiều tối" (từ 14:00 trở đi)**, mỗi nhóm sắp theo giờ tăng dần, hiện số suất còn lại (`maxOrders` trừ `count` của `timeSlotCounters` hôm nay). Khung đã đầy thì disable. Khung đã qua giờ thì disable. Nút "Xác nhận đặt" (thanh toán giả lập, hiển thị dòng "Thanh toán tại quầy / giả lập").
4. **OrderStatusScreen**: hiện mã nhận đồ 4 số cỡ lớn, khung giờ, danh sách món, trạng thái dạng stepper (Đã nhận → Đang chuẩn bị → Sẵn sàng → Hoàn thành), cập nhật **realtime** bằng Firestore snapshot listener. Nút "Hủy đơn" chỉ hiện khi `RECEIVED`.
5. **HistoryScreen**: danh sách đơn của user, mới nhất trước, bấm vào xem chi tiết.

Bottom navigation cho học sinh: Menu, Giỏ hàng, Đơn của tôi.

### Căng tin (CANTEEN)
0. **DashboardScreen** (tab "Tổng quan", mở sẵn sau khi đăng nhập, chỉ đọc, realtime). Mặc định xem **hôm nay**, có thể **chọn ngày khác** (xem bên dưới). Layout xem mục C5 trong `docs/design/design.html`. Không tạo collection hay trường mới, mọi số liệu tính từ `orders`, `menuItems` và `timeSlotCounters` của **ngày đang chọn** (`date` = ngày đó, mặc định hôm nay) mà app đã có. Công thức (đặt trong `DashboardViewModel`, hàm thuần dễ test):
   - **Doanh thu** = tổng `total` của đơn `COMPLETED`; phụ đề "từ N đơn đã hoàn thành".
   - **Tổng số đơn** = số đơn hôm nay ở mọi trạng thái; phụ đề "X đang xử lý (RECEIVED, PREPARING, READY) · Y đã hủy (CANCELLED)".
   - **Suất đã bán** = tổng `quantity` của các món trong đơn không phải `CANCELLED`.
   - **Chuẩn bị trung bình** = trung bình `readyAt - preparingAt` (phút, 1 chữ số thập phân, dấu phẩy) của đơn của ngày chọn có đủ hai mốc; không có đơn nào đủ thì hiện "—". Không bịa số.
   - **Khung giờ nhận**: mỗi `timeSlots` một dòng `count/maxOrders` với `count` lấy từ `timeSlotCounters/{slotId}_{ngày chọn}` (thiếu thì 0). Đầy khi `count >= maxOrders` (đỏ, tag "Đầy"), từ 80% trở lên là "Gần đầy" (cam), còn lại "Còn chỗ" (xanh).
   - **Món sắp hết suất**: món của ngày chọn có `dailyQuota > 0` và còn lại (`dailyQuota - soldQty`) từ 0 đến 3 suất; hết suất ghi "Hết suất (X/X)", còn lại ghi "Còn N suất". Món `inStock = false` mà không phải do hết suất thì ghi "Đã tắt (hết món)". Sắp theo số suất còn lại tăng dần, tối đa 5 dòng; rỗng thì "Chưa có món nào sắp hết".
   - Thiếu các trường của `docs/analytics-data-plan.md` (đơn cũ không có `preparingAt`/`readyAt`, món `dailyQuota = 0`) thì bỏ qua phần tính tương ứng, không crash.
   - **Chọn ngày**: thanh trên cùng có nút ‹ (lùi 1 ngày), nút › (tiến 1 ngày), ô ngày bấm mở `DatePickerDialog` Material 3, và nút "Hôm nay" (chỉ hiện khi đang xem ngày khác). Không cho chọn ngày sau hôm nay (nút › mờ và disable khi đang ở hôm nay, `DatePicker` dùng `SelectableDates` chặn ngày tương lai). Ngày chọn lưu trong `DashboardViewModel` (`MutableStateFlow<LocalDate>`, theo múi giờ máy, định dạng `yyyy-MM-dd` khi truy vấn); khi ngày đổi thì hủy listener cũ và nghe lại theo ngày mới (`flatMapLatest`). Mỗi lần đăng nhập mở lại thì về hôm nay.
   - Truy vấn theo ngày: `orders.whereEqualTo("date", d)`, `menuItems.whereEqualTo("date", d)`, `timeSlotCounters` đọc theo id `{slotId}_{d}`. Chỉ lọc một trường nên không cần composite index.
   - Ngày đã qua: `timeSlots` lấy từ định nghĩa hiện tại; tiêu đề khối món đổi thành "Món hết hoặc gần hết suất"; tag trạng thái ngày là "Ngày đã qua" (xám) thay vì "Hôm nay" (xanh); dòng "Cập nhật theo thời gian thực" ẩn. Không có đơn nào trong ngày thì các số về 0 và hiện dòng "Không có đơn nào trong ngày này". Dữ liệu lịch sử giả (`docs/data/seed-history.json`, nạp bằng `tools/seed-history`) dùng để thử tính năng này.
   - Bấm thẻ "Tổng số đơn" chuyển sang tab Đơn hàng; bấm khối "Món sắp hết suất" chuyển sang tab Thực đơn (hai tab đó luôn là dữ liệu hôm nay, nên khi đang xem ngày khác thì không điều hướng, hai vùng này chỉ để xem).
1. **OrdersScreen**: đơn của hôm nay, realtime, hiển thị dạng 3 cột theo trạng thái (Đã nhận, Đang chuẩn bị, Sẵn sàng lấy) và có chip lọc theo khung giờ. Mỗi đơn hiện mã, tên khách, món. Nút chuyển trạng thái tiếp theo (RECEIVED → PREPARING → READY). Có thể hủy đơn.
2. **MenuManageScreen**: danh sách món hôm nay, Switch bật/tắt `inStock`.
3. **VerifyCodeScreen**: ô nhập mã 4 số. Khớp đơn `READY` của hôm nay thì chuyển `COMPLETED` và báo thành công; sai mã hoặc đơn chưa READY thì báo lỗi rõ ràng.

Điều hướng cho căng tin: Tổng quan, Đơn hàng, Thực đơn, Trả đồ (NavigationRail bên trái khi màn rộng, NavigationBar dưới cùng khi màn nhỏ).

## 5A. Thiết kế giao diện (bắt buộc làm theo)

Mockup đầy đủ nằm ở **`docs/design/design.html`** (mở bằng trình duyệt, xem được cả khi Antigravity mở bằng browser tool). Agent phải **mở file này và vẽ lại giao diện giống mockup** bằng Jetpack Compose: cùng bố cục, màu, font, bo góc, khoảng cách. Đơn vị trong mockup: 1px = 1dp.

Nội dung file mockup:
1. **Design tokens**: bảng màu (primary cam `#EA580C`, nền ấm `#FFFBF5`, success/error/info), typography (Plus Jakarta Sans), shape (card 16dp, button 12dp, chip pill), spacing (4/8/12/16/24dp).
2. **11 màn hình** có ghi chú hành vi bên dưới mỗi màn: S1 Login, S2 Menu, S3 Cart, S4 SlotPicker, S5 OrderStatus, S6 History (học sinh, phone 360x740dp) và C1 Orders, C2 MenuManage, C3 VerifyCode, C4 hộp thoại hủy đơn, C5 Dashboard (căng tin, tablet landscape).
3. **Quy tắc chung cho Compose**: cấu trúc theme (`Color.kt`, `Type.kt`, `Shape.kt`, `Theme.kt`), component dùng chung, bản đồ màu `StatusTag`, trạng thái loading/rỗng/lỗi.

Quy tắc ưu tiên:
- Mockup HTML quyết định **giao diện**; phần mục 4 và mục 6 của file này quyết định **dữ liệu và nghiệp vụ**. Nếu mâu thuẫn về hành vi thì theo mục 6.
- Emoji trong mockup chỉ là ảnh giữ chỗ. App thật dùng `imageUrl` qua Coil, có placeholder.
- Chỉ dùng light theme, không làm dark mode.
- **Thanh trên cùng của căng tin phải responsive**: màn hẹp (< 600dp) dùng tiêu đề 1 dòng (maxLines 1, ellipsis), dòng trạng thái "● Đang nhận đơn" đặt dưới tiêu đề, nút Đăng xuất luôn hiện đủ ở bên phải; không để chữ bị ép xuống dòng hay nút bị đẩy ra ngoài màn hình. Màn rộng (tablet) dùng tag trạng thái cạnh tiêu đề như mockup. Mọi thanh trên cùng khác cũng phải kiểm tra trên màn 360dp.
- Cần tải font Plus Jakarta Sans bằng `androidx.compose.ui:ui-text-google-fonts` (hoặc để font file trong `res/font`); nếu lỗi thì fallback sang font mặc định, không để app crash.

## 6. Quy tắc nghiệp vụ

- **Giới hạn khung giờ**: khi đặt đơn dùng **Firestore transaction**: đọc `timeSlotCounters/{slotId}_{date}` (chưa có thì coi `count = 0`), nếu `count >= maxOrders` của khung giờ thì lỗi "Khung giờ đã đầy"; ngược lại tăng `count` và tạo order trong cùng transaction. Bộ đếm tính theo ngày nên không bao giờ tích lũy qua ngày.
- **Mã nhận đồ**: 4 chữ số, duy nhất trong cùng ngày (kiểm tra trùng khi tạo, random lại nếu trùng).
- **Kiểm tra tồn kho khi đặt**: nếu có món vừa chuyển `inStock = false` thì chặn đặt và báo món nào hết.
- **Số suất mỗi món**: `dailyQuota > 0` thì đặt hàng trừ `soldQty`; vượt số suất còn lại bị chặn; chạm hạn mức thì tự `inStock = false`. Chi tiết ở `docs/analytics-data-plan.md`.
- **Hết món sau khi đặt**: căng tin hủy đơn (status `CANCELLED`, lý do `SOLD_OUT`) và trả lại suất khung giờ.
- **Hủy đơn**: học sinh chỉ hủy được khi `RECEIVED`; hủy thì trả lại suất khung giờ (giảm `count` của ngày đặt) và ghi `cancelledBy`, `cancelReason`, `cancelledAt`. Căng tin hủy phải chọn lý do trong hộp thoại.
- **Đơn không đến lấy**: demo chỉ cần căng tin có thể hủy đơn `READY` thủ công.
- Giá hiển thị theo định dạng VND (vd "25.000đ").

## 7. Dữ liệu mẫu (seed)

`SeedRepository.seedIfEmpty()` chạy một lần khi `menuItems` và `timeSlots` trống (có nút ẩn hoặc gọi khi khởi động bản debug):

- **Tài khoản** (tạo thủ công trên Firebase Console, hoặc seed): `student@demo.com` / `123456` (STUDENT), `canteen@demo.com` / `123456` (CANTEEN). Document `users/{uid}` tương ứng.
- **Nguồn dữ liệu**: đọc từ **`docs/data/seed-menu.json`** (copy vào `app/src/main/assets/seed-menu.json` để app đọc lúc chạy). Gồm 10 món (4 category: Cơm, Bún/Mì, Ăn nhanh, Đồ uống; giá 15.000 - 40.000đ; 1 món `inStock = false`) và **8 khung giờ** rộng 15 phút, `maxOrders = 10`: 4 khung **trưa** (11:15 đến 12:15) và 4 khung **chiều tối** (17:00 đến 18:00). Cả hai buổi dùng chung thực đơn của ngày. Hạn mức mỗi món là 14 suất (đồ uống 25) để đủ cho cả hai buổi. `timeSlots` đã có sẵn trong Firestore thì `seedIfEmpty()` không tự thêm: xóa collection `timeSlots` rồi đăng nhập lại để nạp đủ 8 khung.
- `date` của món không có trong JSON: `SeedRepository` gán bằng ngày hiện tại (yyyy-MM-dd) khi seed.
- **Ảnh món**: `imageUrl` trong JSON là ảnh công khai trên Wikimedia Commons, chỉ dùng cho demo. Hiển thị bằng Coil (`AsyncImage`) với `crossfade`, có placeholder. Nếu `imageUrl` trống hoặc tải lỗi thì hiện ô nền `primaryContainer` kèm icon theo category (Cơm 🍚, Bún/Mì 🍜, Ăn nhanh 🥖, Đồ uống 🥤). Cần quyền `INTERNET` trong manifest.

## 8. Hướng dẫn cho agent

### Thứ tự thực hiện
1. **Setup**: tạo project, thêm dependency (Compose, Hilt, Navigation, Firebase BoM, Coil), `@HiltAndroidApp`. Mở `docs/design/design.html` và dựng theme Material 3 đúng token (`Color.kt`, `Type.kt`, `Shape.kt`, `Theme.kt`) cùng các component dùng chung theo mục 5A.
2. **Data layer**: model, các repository, module DI, `SeedRepository`.
3. **Auth + điều hướng theo role**.
4. **Luồng học sinh** (menu → giỏ → slot → trạng thái → lịch sử).
5. **Luồng căng tin** (tổng quan → đơn → menu → trả đồ).
6. **Polish**: loading/empty/error state, định dạng tiền, xử lý mất mạng cơ bản.

### Quy ước
- Mỗi màn hình có `ViewModel` riêng, state là `data class UiState` expose qua `StateFlow`; không gọi Firebase trực tiếp từ Composable.
- Repository trả `Flow` cho dữ liệu realtime, `suspend` cho thao tác ghi.
- Chuỗi hiển thị bằng tiếng Việt, đặt trong `strings.xml`.
- Không thêm tính năng ngoài mục 1. Gặp điểm chưa rõ thì chọn phương án đơn giản nhất và ghi chú trong code.
- Sau mỗi bước chạy `./gradlew assembleDebug` và sửa hết lỗi trước khi sang bước tiếp.

### Việc người dùng phải tự làm (agent không làm được, hãy nhắc rõ)
1. Tạo Firebase project, thêm app Android với đúng `applicationId`.
2. Tải `google-services.json` đặt vào `app/` (chi tiết ở phần "Cấu hình Firebase" bên dưới).
3. Bật Authentication → Email/Password; tạo 2 tài khoản mẫu.
4. Tạo Firestore database và dán rules demo (cho phép user đã đăng nhập đọc/ghi; chỉ cần đủ chạy demo):
   ```
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       match /{document=**} {
         allow read, write: if request.auth != null;
       }
     }
   }
   ```

### Cấu hình Firebase (`google-services.json`)
- File `google-services.json` do người dùng tải từ Firebase Console (Project settings → Your apps → app Android `com.example.canteen`) và đặt tại **`app/google-services.json`** (cùng cấp với `app/build.gradle.kts`).
- Agent **không được tự tạo hoặc bịa nội dung file này**. Ở bước 1 (Setup), nếu `app/google-services.json` chưa tồn tại thì **dừng lại và báo người dùng** đặt file vào, rồi mới tiếp tục.
- Agent phải cấu hình sẵn:
  - Plugin `com.google.gms.google-services` trong `build.gradle.kts` cấp project (khai báo `apply false`) và trong `app/build.gradle.kts`.
  - `applicationId` và `namespace` của `app` đặt đúng `com.example.canteen` để khớp với file.
- Thêm `google-services.json` vào `.gitignore` để không đẩy lên repo công khai.
- Kiểm tra sau khi đặt file: `./gradlew assembleDebug` build qua, mở app thấy đăng nhập được bằng `student@demo.com`.

## 9. Tiêu chí hoàn thành

- [ ] `./gradlew assembleDebug` thành công, ra file APK debug.
- [ ] Đăng nhập `student@demo.com` vào luồng học sinh, `canteen@demo.com` vào luồng căng tin; đăng xuất và phiên được giữ khi mở lại app.
- [ ] Menu chỉ hiện món của hôm nay; món hết hàng bị làm mờ và không thêm được vào giỏ.
- [ ] Đặt đơn thành công tạo order với mã 4 số, bộ đếm `timeSlotCounters` của khung giờ hôm nay tăng 1.
- [ ] Khung giờ đủ `maxOrders` thì không đặt được (kể cả khi 2 máy đặt cùng lúc).
- [ ] Căng tin đổi trạng thái thì màn hình học sinh cập nhật ngay, không cần refresh.
- [ ] Căng tin tắt món thì menu học sinh cập nhật ngay.
- [ ] Nhập đúng mã ở VerifyCodeScreen thì đơn `READY` chuyển `COMPLETED`; sai mã hoặc đơn chưa READY thì báo lỗi.
- [ ] Học sinh hủy đơn `RECEIVED` thì suất khung giờ được trả lại.
- [ ] Có trạng thái loading, rỗng và lỗi ở các màn hình danh sách.
- [ ] Mọi tiêu chí trong mục 7 của `docs/analytics-data-plan.md` đều đạt (dữ liệu phân tích, hộp thoại hủy, số suất mỗi món).
- [ ] Tab Tổng quan (C5) là tab đầu tiên, mở sẵn sau khi căng tin đăng nhập; bốn số liệu, khung giờ và món sắp hết khớp công thức ở mục 5 và cập nhật realtime khi có đơn mới (khi xem hôm nay). Chọn được ngày đã qua (không chọn được ngày tương lai); số liệu đổi đúng theo ngày chọn, thử được với dữ liệu lịch sử giả.
- [ ] Giao diện của 11 màn hình khớp `docs/design/design.html` (màu, font, bo góc, bố cục); luồng căng tin dùng NavigationRail trên tablet.

## 10. Hướng mở rộng (ngoài demo)

Thanh toán VietQR tự động, quét QR bằng camera, push notification (FCM), Group Order, Combo Builder, Canteen Pass và tích điểm, "Hôm nay ăn gì", báo cáo doanh thu, admin portal và phân quyền, siết Firestore rules theo role.

## 11. Ý tưởng MCP (sau demo, KHÔNG làm trong demo)

Mục này chỉ ghi lại ý tưởng. Agent không được triển khai khi làm demo.

### 11.1 MCP báo cáo cho chủ căng tin (ưu tiên)
- Một MCP server **chỉ đọc**, đọc Firestore bằng Firebase Admin SDK (service account key đặt ngoài repo, thêm vào `.gitignore`). Chạy local (stdio) trên máy chủ quán, dùng từ Claude Desktop hoặc Claude Code.
- Chủ căng tin hỏi bằng tiếng Việt, ví dụ: doanh thu và số suất hôm nay; khung giờ đông nhất tuần này; món bán chạy và món ế; số đơn bị hủy; gợi ý số suất cần chuẩn bị cho ngày mai; tóm tắt tuần thành báo cáo.
- Công cụ dự kiến: `get_sales_summary(date_range)`, `get_top_items(range)`, `get_orders_by_slot(date)`, `get_cancellations(range)`, `list_menu(date)`. Không có công cụ ghi hoặc xóa. Che bớt thông tin cá nhân của học sinh (tên, email) trong kết quả.
- Đặt ở `tools/mcp-firestore/` (ngoài module `app`). Thiết kế chi tiết để code: **`docs/mcp-server-design.md`** (TypeScript, 1 công cụ tổng quát `query_orders` + 8 lối tắt, chạy local).

### 11.2 Hỏi đáp thực đơn cho khách (không dùng MCP)
- MCP phục vụ AI client, không phải app Android. Học sinh hỏi món hôm nay thì làm **chatbot trong app**: ô chat gọi Claude API qua một Cloud Function (cần gói Blaze, giữ API key trên server), đưa thực đơn hôm nay (khoảng 10 món) vào prompt.
- Cách rẻ hơn: bộ lọc món theo giá và loại ngay trong app.

### 11.3 Dữ liệu để MCP trả lời đúng

Đã được thiết kế đầy đủ trong **`docs/analytics-data-plan.md`**: các trường theo dõi thời gian và lý do hủy trên `orders`, `category` và `dishKey` trên `OrderItem`, `dishKey`/`tags`/`dailyQuota`/`soldQty` trên `menuItems`, bộ đếm khung giờ theo ngày (`timeSlotCounters`), và hợp đồng đọc cho MCP. Phần này được làm trong app **trước** để dữ liệu được ghi từ sớm; MCP server vẫn làm sau demo.
