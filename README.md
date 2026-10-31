# Canteen – Ứng dụng đặt cơm căng tin trường học (demo)

Ứng dụng Android demo cho căng tin trường học: học sinh xem thực đơn, chọn khung giờ và đặt cơm trưa; căng tin nhận đơn, chuẩn bị và giao. Không có thanh toán online (thanh toán khi nhận).

- **Ngôn ngữ / UI:** Kotlin, Jetpack Compose, Material 3
- **Kiến trúc:** MVVM, StateFlow, Hilt, Navigation Compose, Coil
- **Backend:** Firebase Authentication (Email/Password) và Cloud Firestore
- **Package:** `com.example.canteen` (minSdk 24, targetSdk 34, JDK 17)
- **Một app, hai vai trò:** `STUDENT` (học sinh) và `CANTEEN` (căng tin). Máy tính bảng từ 600dp trở lên dùng NavigationRail, điện thoại dùng bottom bar.

## Cấu trúc repo

```
app/                   Ứng dụng Android (module duy nhất)
docs/
  description.md       Đặc tả chính của app (đọc file này đầu tiên)
  analytics-data-plan.md  Các trường dữ liệu phân tích cần bổ sung
  features-v2-plan.md     Kế hoạch tính năng v2: voucher, điểm, tab Nhà bếp, hoạt ảnh
  mcp-server-design.md    Thiết kế MCP server báo cáo
  design/design.html      Mockup giao diện (mở bằng trình duyệt)
  data/                   seed-menu.json (thực đơn), seed-history.json (lịch sử giả)
tools/
  seed-history/        Script Node nạp lịch sử đơn giả vào Firestore
  mcp-firestore/       MCP server chỉ-đọc cho báo cáo của chủ căng tin
```

## Chạy ứng dụng

1. Cài Android Studio (bản mới) với JDK 17.
2. Tạo project Firebase, bật **Authentication → Email/Password** và **Firestore Database**.
3. Thêm app Android với package `com.example.canteen`, tải `google-services.json` và đặt vào `app/`. File này đã nằm trong `.gitignore`, không commit.
4. Quy tắc Firestore cho demo:
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
   Quy tắc này chỉ dùng cho demo, không dùng cho môi trường thật.
5. Mở project bằng Android Studio, chờ Gradle sync, chọn thiết bị và Run. Hoặc dùng dòng lệnh:
   ```
   ./gradlew assembleDebug
   ```
6. Đăng ký tài khoản và chọn vai trò. Lần đầu đăng nhập, app tự nạp thực đơn và khung giờ từ `app/src/main/assets/seed-menu.json` khi Firestore còn trống.

## Dữ liệu mẫu và báo cáo (tùy chọn)

- **Lịch sử giả để demo báo cáo:** xem [tools/seed-history/README.md](tools/seed-history/README.md). Cần service account key đặt ngoài repo hoặc tên `serviceAccount.json`.
- **MCP server báo cáo:** xem [tools/mcp-firestore/README.md](tools/mcp-firestore/README.md). Chạy được bằng dữ liệu mẫu (`DATA_SOURCE=fixture`) mà không cần key.

## Bảo mật

Không commit `google-services.json`, `serviceAccount*.json` hay bất kỳ file key nào. Các file này đã được `.gitignore` chặn. Xóa key trên Firebase Console khi xong demo.

## Tài liệu

Đặc tả đầy đủ (màn hình, mô hình dữ liệu, quy tắc nghiệp vụ, tiêu chí nghiệm thu) nằm trong [docs/description.md](docs/description.md).
