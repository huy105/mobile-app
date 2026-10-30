# Canteen Order App (Demo) - Spec cho Antigravity

Tài liệu này là đặc tả để AI agent (Antigravity) tự code ứng dụng bằng **Kotlin**. Làm tuần tự theo mục 8, mỗi bước phải build được.

## 1. Mục tiêu và phạm vi

Ứng dụng đặt đồ ăn trưa cho căng tin trường học. Giải quyết: xếp hàng đông giờ ra chơi, hết món bất ngờ, thời gian nghỉ ngắn.

Mục tiêu demo: chạy mượt luồng **Chọn món → Chọn giờ nhận → Nhận mã → Lấy đồ**.

### Có trong demo
- 1 app Android, 2 vai trò: **STUDENT** (học sinh/sinh viên) và **CANTEEN** (căng tin).
- Học sinh: xem menu theo ngày, giỏ hàng, chọn khung giờ nhận, xác nhận đặt (thanh toán giả lập), mã nhận đồ, theo dõi trạng thái đơn realtime, lịch sử đơn.
- Căng tin: danh sách đơn theo khung giờ realtime, đổi trạng thái đơn, bật/tắt món hết hàng, nhập mã 4 số để hoàn thành đơn.

### Không làm
Thanh toán thật (VietQR/ví), Canteen Pass, tích điểm, Group Order, Combo Builder, bảng xếp hạng, "Hôm nay ăn gì", báo cáo doanh thu, admin portal, push notification, quét camera QR.

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
   └─ canteen/              (OrdersScreen, MenuManageScreen, VerifyCodeScreen + ViewModel)
```

## 4. Data model (Firestore)

Tất cả model là Kotlin `data class` có giá trị mặc định (để Firestore deserialize).

| Collection | Trường |
|---|---|
| `users/{uid}` | `name: String`, `email: String`, `role: "STUDENT" \| "CANTEEN"` |
| `menuItems/{id}` | `name`, `description`, `price: Long` (VND), `imageUrl`, `category`, `inStock: Boolean`, `date: String` (yyyy-MM-dd) |
| `timeSlots/{id}` | `label` (vd "11:15 - 11:30"), `startMinute: Int`, `endMinute: Int`, `maxOrders: Int`, `currentOrders: Int` |
| `orders/{id}` | `userId`, `userName`, `items: List<OrderItem>`, `slotId`, `slotLabel`, `pickupCode: String` (4 số), `status`, `total: Long`, `date: String`, `createdAt: Timestamp` |

`OrderItem`: `menuItemId`, `name`, `price`, `quantity`.

`OrderStatus` enum: `RECEIVED`, `PREPARING`, `READY`, `COMPLETED`, `CANCELLED`. Lưu dưới dạng String.

## 5. Màn hình và luồng

### Chung
- **LoginScreen**: email + mật khẩu. Sau khi đăng nhập đọc `users/{uid}.role` rồi điều hướng: STUDENT vào luồng học sinh, CANTEEN vào luồng căng tin. Có nút đăng xuất ở mỗi luồng: **nút dạng pill có chữ "Đăng xuất" kèm icon ⎋**, cao 40dp, nền `errorContainer`, viền 1.5dp và chữ màu `error`, đặt ở góc phải thanh trên cùng (component dùng chung `LogoutButton` trong `ui/common/components/`). Không dùng icon trơn không có chữ. Ghi nhớ phiên đăng nhập.

### Học sinh (STUDENT)
1. **MenuScreen**: danh sách món của ngày hôm nay (lọc theo `date`), nhóm theo `category`. Món `inStock = false` làm mờ, gắn nhãn "Hết món", không thêm được. Nút thêm vào giỏ, badge số lượng trên icon giỏ.
2. **CartScreen**: chỉnh số lượng, xóa món, hiện tổng tiền. Nút "Chọn giờ nhận".
3. **SlotPickerScreen**: danh sách khung giờ, hiện số suất còn lại (`maxOrders - currentOrders`). Khung đã đầy thì disable. Khung đã qua giờ thì disable. Nút "Xác nhận đặt" (thanh toán giả lập, hiển thị dòng "Thanh toán tại quầy / giả lập").
4. **OrderStatusScreen**: hiện mã nhận đồ 4 số cỡ lớn, khung giờ, danh sách món, trạng thái dạng stepper (Đã nhận → Đang chuẩn bị → Sẵn sàng → Hoàn thành), cập nhật **realtime** bằng Firestore snapshot listener. Nút "Hủy đơn" chỉ hiện khi `RECEIVED`.
5. **HistoryScreen**: danh sách đơn của user, mới nhất trước, bấm vào xem chi tiết.

Bottom navigation cho học sinh: Menu, Giỏ hàng, Đơn của tôi.

### Căng tin (CANTEEN)
1. **OrdersScreen**: đơn của hôm nay, realtime, hiển thị dạng 3 cột theo trạng thái (Đã nhận, Đang chuẩn bị, Sẵn sàng lấy) và có chip lọc theo khung giờ. Mỗi đơn hiện mã, tên khách, món. Nút chuyển trạng thái tiếp theo (RECEIVED → PREPARING → READY). Có thể hủy đơn.
2. **MenuManageScreen**: danh sách món hôm nay, Switch bật/tắt `inStock`.
3. **VerifyCodeScreen**: ô nhập mã 4 số. Khớp đơn `READY` của hôm nay thì chuyển `COMPLETED` và báo thành công; sai mã hoặc đơn chưa READY thì báo lỗi rõ ràng.

Điều hướng cho căng tin: Đơn hàng, Thực đơn, Trả đồ (NavigationRail bên trái khi màn rộng, NavigationBar dưới cùng khi màn nhỏ).

## 5A. Thiết kế giao diện (bắt buộc làm theo)

Mockup đầy đủ nằm ở **`docs/design/design.html`** (mở bằng trình duyệt, xem được cả khi Antigravity mở bằng browser tool). Agent phải **mở file này và vẽ lại giao diện giống mockup** bằng Jetpack Compose: cùng bố cục, màu, font, bo góc, khoảng cách. Đơn vị trong mockup: 1px = 1dp.

Nội dung file mockup:
1. **Design tokens**: bảng màu (primary cam `#EA580C`, nền ấm `#FFFBF5`, success/error/info), typography (Plus Jakarta Sans), shape (card 16dp, button 12dp, chip pill), spacing (4/8/12/16/24dp).
2. **9 màn hình** có ghi chú hành vi bên dưới mỗi màn: S1 Login, S2 Menu, S3 Cart, S4 SlotPicker, S5 OrderStatus, S6 History (học sinh, phone 360x740dp) và C1 Orders, C2 MenuManage, C3 VerifyCode (căng tin, tablet landscape).
3. **Quy tắc chung cho Compose**: cấu trúc theme (`Color.kt`, `Type.kt`, `Shape.kt`, `Theme.kt`), component dùng chung, bản đồ màu `StatusTag`, trạng thái loading/rỗng/lỗi.

Quy tắc ưu tiên:
- Mockup HTML quyết định **giao diện**; phần mục 4 và mục 6 của file này quyết định **dữ liệu và nghiệp vụ**. Nếu mâu thuẫn về hành vi thì theo mục 6.
- Emoji trong mockup chỉ là ảnh giữ chỗ. App thật dùng `imageUrl` qua Coil, có placeholder.
- Chỉ dùng light theme, không làm dark mode.
- **Thanh trên cùng của căng tin phải responsive**: màn hẹp (< 600dp) dùng tiêu đề 1 dòng (maxLines 1, ellipsis), dòng trạng thái "● Đang nhận đơn" đặt dưới tiêu đề, nút Đăng xuất luôn hiện đủ ở bên phải; không để chữ bị ép xuống dòng hay nút bị đẩy ra ngoài màn hình. Màn rộng (tablet) dùng tag trạng thái cạnh tiêu đề như mockup. Mọi thanh trên cùng khác cũng phải kiểm tra trên màn 360dp.
- Cần tải font Plus Jakarta Sans bằng `androidx.compose.ui:ui-text-google-fonts` (hoặc để font file trong `res/font`); nếu lỗi thì fallback sang font mặc định, không để app crash.

## 6. Quy tắc nghiệp vụ

- **Giới hạn khung giờ**: khi đặt đơn dùng **Firestore transaction**: đọc `timeSlots/{id}`, nếu `currentOrders >= maxOrders` thì lỗi "Khung giờ đã đầy"; ngược lại tăng `currentOrders` và tạo order trong cùng transaction.
- **Mã nhận đồ**: 4 chữ số, duy nhất trong cùng ngày (kiểm tra trùng khi tạo, random lại nếu trùng).
- **Kiểm tra tồn kho khi đặt**: nếu có món vừa chuyển `inStock = false` thì chặn đặt và báo món nào hết.
- **Hết món sau khi đặt**: căng tin hủy đơn (status `CANCELLED`) và giảm `currentOrders` của khung giờ.
- **Hủy đơn**: học sinh chỉ hủy được khi `RECEIVED`; hủy thì giảm `currentOrders`.
- **Đơn không đến lấy**: demo chỉ cần căng tin có thể hủy đơn `READY` thủ công.
- Giá hiển thị theo định dạng VND (vd "25.000đ").

## 7. Dữ liệu mẫu (seed)

`SeedRepository.seedIfEmpty()` chạy một lần khi `menuItems` và `timeSlots` trống (có nút ẩn hoặc gọi khi khởi động bản debug):

- **Tài khoản** (tạo thủ công trên Firebase Console, hoặc seed): `student@demo.com` / `123456` (STUDENT), `canteen@demo.com` / `123456` (CANTEEN). Document `users/{uid}` tương ứng.
- **Nguồn dữ liệu**: đọc từ **`docs/data/seed-menu.json`** (copy vào `app/src/main/assets/seed-menu.json` để app đọc lúc chạy). Gồm 10 món (4 category: Cơm, Bún/Mì, Ăn nhanh, Đồ uống; giá 15.000 - 40.000đ; 1 món `inStock = false`) và 4 khung giờ (11:15 đến 12:15, `maxOrders = 10`).
- `date` của món không có trong JSON: `SeedRepository` gán bằng ngày hiện tại (yyyy-MM-dd) khi seed.
- **Ảnh món**: `imageUrl` trong JSON là ảnh công khai trên Wikimedia Commons, chỉ dùng cho demo. Hiển thị bằng Coil (`AsyncImage`) với `crossfade`, có placeholder. Nếu `imageUrl` trống hoặc tải lỗi thì hiện ô nền `primaryContainer` kèm icon theo category (Cơm 🍚, Bún/Mì 🍜, Ăn nhanh 🥖, Đồ uống 🥤). Cần quyền `INTERNET` trong manifest.

## 8. Hướng dẫn cho agent

### Thứ tự thực hiện
1. **Setup**: tạo project, thêm dependency (Compose, Hilt, Navigation, Firebase BoM, Coil), `@HiltAndroidApp`. Mở `docs/design/design.html` và dựng theme Material 3 đúng token (`Color.kt`, `Type.kt`, `Shape.kt`, `Theme.kt`) cùng các component dùng chung theo mục 5A.
2. **Data layer**: model, các repository, module DI, `SeedRepository`.
3. **Auth + điều hướng theo role**.
4. **Luồng học sinh** (menu → giỏ → slot → trạng thái → lịch sử).
5. **Luồng căng tin** (đơn → menu → trả đồ).
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
- [ ] Đặt đơn thành công tạo order với mã 4 số, `currentOrders` của khung giờ tăng 1.
- [ ] Khung giờ đủ `maxOrders` thì không đặt được (kể cả khi 2 máy đặt cùng lúc).
- [ ] Căng tin đổi trạng thái thì màn hình học sinh cập nhật ngay, không cần refresh.
- [ ] Căng tin tắt món thì menu học sinh cập nhật ngay.
- [ ] Nhập đúng mã ở VerifyCodeScreen thì đơn `READY` chuyển `COMPLETED`; sai mã hoặc đơn chưa READY thì báo lỗi.
- [ ] Học sinh hủy đơn `RECEIVED` thì suất khung giờ được trả lại.
- [ ] Có trạng thái loading, rỗng và lỗi ở các màn hình danh sách.
- [ ] Giao diện của 9 màn hình khớp `docs/design/design.html` (màu, font, bo góc, bố cục); luồng căng tin dùng NavigationRail trên tablet.

## 10. Hướng mở rộng (ngoài demo)

Thanh toán VietQR tự động, quét QR bằng camera, push notification (FCM), Group Order, Combo Builder, Canteen Pass và tích điểm, "Hôm nay ăn gì", báo cáo doanh thu, admin portal và phân quyền, siết Firestore rules theo role.
