# Seed lịch sử giả cho demo báo cáo

Sinh khoảng 4 tuần đơn hàng giả (24 ngày học, khoảng 1.000 đơn, tức 45 đơn mỗi ngày) và nạp vào Firestore, để demo báo cáo và MCP có dữ liệu thật để tính.

## Dữ liệu sinh ra
- `orders`: đơn hoàn thành và đơn bị hủy (đủ lý do SOLD_OUT, OVERLOAD, NO_SHOW, CHANGED_MIND), có `preparingAt`, `readyAt`, `completedAt`, `cancelledAt`, `cancelledBy`, `cancelReason`, và `category`/`dishKey` trong từng món.
- `menuItems`: menu của từng ngày cũ với `dailyQuota`, `soldQty`, `inStock` cuối ngày (id dạng `hist_<ngày>_<dishKey>`).
- `timeSlotCounters`: số đơn không bị hủy của từng khung giờ mỗi ngày, khớp cách app đếm.
- Có sẵn xu hướng để báo cáo có chuyện để kể: khung 11:30-11:45 hay đầy, doanh thu tăng dần theo tuần, thứ Sáu bún/phở bán chạy, Xôi và Mì xào ế, một ngày bếp chậm (chuẩn bị khoảng 18 phút) và một ngày nhiều đơn không đến lấy.
- Khoảng 18% đơn có ưu đãi (voucher `WELCOME10`, `GIAM5K`, `SINHVIEN`; một số đơn dùng thêm điểm rèn luyện), có `subtotal`, `discountAmount`, `voucherCode`, `pointsUsed`; menu mỗi ngày có `costPrice` để tính chi phí và lãi gộp.
- Mọi document có `demoHistory: true`, id bắt đầu bằng `hist_`. Học sinh là tên giả (`demo_student_01` đến `30`). Không đụng tới dữ liệu hôm nay và các đơn thật của bạn.

Giữ ý: số đơn mỗi khung giờ bị giới hạn bởi `maxOrders` (mặc định 10) và có 8 khung giờ (4 trưa, 4 chiều tối) nên một ngày tối đa 80 đơn; khung chiều tối vắng hơn khung trưa. Hạn mức trong `docs/data/seed-menu.json` đã đặt 14 suất món ăn và 25 suất đồ uống cho khớp.

## Cách dùng
1. Làm xong `docs/analytics-data-plan.md` trong app (để model có đủ trường mới) và đăng nhập app ít nhất một lần (để có `timeSlots` trong Firestore).
2. Lấy service account key: Firebase Console, Project settings, Service accounts, **Generate new private key**. Lưu file **ngoài repo** hoặc tên `serviceAccount.json` (đã nằm trong `.gitignore`). Không commit file này.
3. Cài và chạy:
```
cd tools/seed-history
npm install
node seed-history.js --key ./serviceAccount.json
```
4. Chạy thử không cần key (chỉ sinh `docs/data/seed-history.json` để xem): `node seed-history.js --dry-run`.
5. Xóa sạch dữ liệu giả: `node seed-history.js --key ./serviceAccount.json --clean`.

Chạy lại nhiều lần là an toàn: id cố định nên ghi đè, không nhân đôi. Tùy chọn: `--weeks 2` (số tuần), `--seed 123` (đổi bộ số ngẫu nhiên).

Lưu ý bảo mật: key này có toàn quyền với database. Chỉ dùng cho project demo và xóa key trên Console khi xong.
