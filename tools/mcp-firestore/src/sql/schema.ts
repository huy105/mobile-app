export const SCHEMA_SQL = `
CREATE TABLE orders (
  order_id TEXT PRIMARY KEY,
  date TEXT NOT NULL,
  weekday INTEGER NOT NULL,
  week_start TEXT NOT NULL,
  slot_id TEXT,
  slot_label TEXT,
  daypart TEXT NOT NULL,
  status TEXT NOT NULL,
  total INTEGER NOT NULL,
  subtotal INTEGER NOT NULL,
  discount_amount INTEGER NOT NULL,
  voucher_code TEXT,
  points_used INTEGER NOT NULL,
  customer TEXT NOT NULL,
  created_at TEXT NOT NULL,
  preparing_at TEXT,
  ready_at TEXT,
  completed_at TEXT,
  cancelled_at TEXT,
  cancelled_by TEXT,
  cancel_reason TEXT,
  prep_minutes REAL,
  wait_minutes REAL,
  pickup_delay_minutes REAL
);

CREATE TABLE order_items (
  order_id TEXT NOT NULL,
  date TEXT NOT NULL,
  weekday INTEGER NOT NULL,
  week_start TEXT NOT NULL,
  slot_label TEXT,
  daypart TEXT NOT NULL,
  status TEXT NOT NULL,
  dish_key TEXT NOT NULL,
  name TEXT NOT NULL,
  category TEXT NOT NULL,
  price INTEGER NOT NULL,
  quantity INTEGER NOT NULL,
  line_revenue INTEGER NOT NULL,
  cost_price INTEGER NOT NULL
);

CREATE TABLE menu_items (
  menu_item_id TEXT PRIMARY KEY,
  date TEXT NOT NULL,
  weekday INTEGER NOT NULL,
  dish_key TEXT NOT NULL,
  name TEXT NOT NULL,
  category TEXT NOT NULL,
  price INTEGER NOT NULL,
  cost_price INTEGER NOT NULL,
  tags TEXT,
  daily_quota INTEGER NOT NULL,
  sold_qty INTEGER NOT NULL,
  remaining_qty INTEGER,
  in_stock INTEGER NOT NULL,
  available INTEGER NOT NULL
);

CREATE TABLE time_slots (
  slot_id TEXT PRIMARY KEY,
  label TEXT NOT NULL,
  start_minute INTEGER NOT NULL,
  end_minute INTEGER NOT NULL,
  max_orders INTEGER NOT NULL,
  daypart TEXT NOT NULL
);

CREATE VIEW v_completed_orders AS SELECT * FROM orders WHERE status = 'COMPLETED';
CREATE VIEW v_completed_items  AS SELECT * FROM order_items WHERE status = 'COMPLETED';
CREATE VIEW v_cancelled_orders AS SELECT * FROM orders WHERE status = 'CANCELLED';

-- Mỗi ngày: doanh thu, đơn, suất, hủy, tỷ lệ hủy, thời gian chuẩn bị trung bình
CREATE VIEW v_daily AS
SELECT o.date, o.weekday, o.week_start,
  SUM(CASE WHEN o.status = 'COMPLETED' THEN o.total ELSE 0 END) AS revenue,
  SUM(o.status = 'COMPLETED') AS orders,
  (SELECT COALESCE(SUM(i.quantity), 0) FROM order_items i
     WHERE i.date = o.date AND i.status = 'COMPLETED') AS portions,
  SUM(o.status = 'CANCELLED') AS cancelled_orders,
  SUM(o.status IN ('RECEIVED','PREPARING','READY')) AS in_progress_orders,
  CASE WHEN SUM(o.status IN ('COMPLETED','CANCELLED')) = 0 THEN NULL
       ELSE 1.0 * SUM(o.status = 'CANCELLED') / SUM(o.status IN ('COMPLETED','CANCELLED')) END AS cancel_rate,
  SUM(CASE WHEN o.status = 'COMPLETED' THEN o.discount_amount ELSE 0 END) AS discount_total,
  AVG(CASE WHEN o.status = 'COMPLETED' THEN o.prep_minutes END) AS avg_prep_minutes
FROM orders o GROUP BY o.date, o.weekday, o.week_start;

-- Mỗi ngày, mỗi món: số suất và doanh thu (đơn COMPLETED)
CREATE VIEW v_item_daily AS
SELECT date, weekday, dish_key, name, category,
       SUM(quantity) AS portions, SUM(line_revenue) AS revenue
FROM order_items WHERE status = 'COMPLETED'
GROUP BY date, weekday, dish_key, name, category;

-- Mỗi ngày, mỗi khung: số đơn (không tính hủy), tỷ lệ lấp đầy, có đầy không
CREATE VIEW v_slot_daily AS
SELECT o.date, o.weekday, o.slot_id, o.slot_label, o.daypart, t.max_orders,
       COUNT(*) AS orders,
       CASE WHEN t.max_orders > 0 THEN 1.0 * COUNT(*) / t.max_orders END AS fill_rate,
       CASE WHEN t.max_orders > 0 AND COUNT(*) >= t.max_orders THEN 1 ELSE 0 END AS is_full
FROM orders o LEFT JOIN time_slots t ON t.slot_id = o.slot_id
WHERE o.status <> 'CANCELLED'
GROUP BY o.date, o.weekday, o.slot_id, o.slot_label, o.daypart, t.max_orders;

-- Mỗi ngày, mỗi món có hạn mức: tỷ lệ bán, có hết suất không
CREATE VIEW v_item_sellthrough AS
SELECT date, weekday, dish_key, name, category, daily_quota, sold_qty,
       1.0 * sold_qty / daily_quota AS sell_through,
       CASE WHEN sold_qty >= daily_quota THEN 1 ELSE 0 END AS sold_out
FROM menu_items WHERE daily_quota > 0;

-- Mỗi ngày: doanh thu gộp, giảm giá, doanh thu thuần, chi phí (giá vốn) và lãi gộp. Tính trên đơn KHÔNG bị hủy
-- (khớp tab Nhà bếp của app, vì bếp phải làm cả đơn chưa giao); khác v_daily.revenue (chỉ đơn COMPLETED).
CREATE VIEW v_pnl_daily AS
SELECT date, gross_revenue, discount_total,
       gross_revenue - discount_total AS net_revenue,
       cost,
       gross_revenue - discount_total - cost AS gross_profit
FROM (
  SELECT i.date,
         SUM(i.line_revenue) AS gross_revenue,
         SUM(i.cost_price * i.quantity) AS cost,
         (SELECT COALESCE(SUM(o.discount_amount), 0) FROM orders o
            WHERE o.date = i.date AND o.status <> 'CANCELLED') AS discount_total
  FROM order_items i WHERE i.status <> 'CANCELLED' GROUP BY i.date
);
`;

export function getSchemaDocumentation(today: string): string {
  return `# LƯỢC ĐỒ CƠ SỞ DỮ LIỆU CĂNG TIN (SQLITE CHỈ-ĐỌC)
Ngày hôm nay (giờ VN) là: ${today}.
Mặc định nạp dữ liệu: 30 ngày gần nhất (tối đa 92 ngày).

## 1. CÁC QUY ƯỚC QUAN TRỌNG
- Doanh thu của ĐƠN dùng: orders.total (hoặc v_daily.revenue) - đây là số tiền khách trả (đã trừ giảm giá).
- Doanh thu của MÓN dùng: order_items.line_revenue (hoặc v_item_daily.revenue) - doanh thu trước giảm giá.
- Doanh thu gộp, giảm giá, chi phí giá vốn và lãi gộp dùng: v_pnl_daily (tính trên các đơn KHÔNG bị hủy, khớp tab Nhà bếp).
- TUYỆT ĐỐI KHÔNG JOIN orders với order_items rồi SUM(orders.total) vì sẽ bị nhân trùng tiền.
- Mọi "doanh thu" và "suất bán" chỉ tính với đơn status = 'COMPLETED'.
- Tỷ lệ hủy đơn (cancel_rate) = cancelled_orders / (cancelled_orders + completed_orders).
- Giờ và ngày lưu theo chuỗi giờ Việt Nam (yyyy-MM-dd và yyyy-MM-dd HH:MM:SS).
- Cột weekday: 1 = Thứ Hai ... 7 = Chủ nhật.
- Cột daypart: 'lunch' (trước 14:00) hoặc 'evening' (từ 14:00).
- Quyền riêng tư: Không có tên khách hay userId, chỉ có mã băm customer (ví dụ khach_a3f).

## 2. DANH SÁCH BẢNG
- orders: Thông tin từng đơn hàng (order_id, date, weekday, week_start, slot_id, slot_label, daypart, status, total, subtotal, discount_amount, voucher_code, points_used, customer, created_at, preparing_at, ready_at, completed_at, cancelled_at, cancelled_by, cancel_reason, prep_minutes, wait_minutes, pickup_delay_minutes).
- order_items: Chi tiết món trong từng đơn (order_id, date, weekday, week_start, slot_label, daypart, status, dish_key, name, category, price, quantity, line_revenue, cost_price).
- menu_items: Thực đơn từng ngày (menu_item_id, date, weekday, dish_key, name, category, price, cost_price, tags, daily_quota, sold_qty, remaining_qty, in_stock, available).
  (tags có dấu phẩy hai đầu, lọc: tags LIKE '%,vegetarian,%').
- time_slots: Cấu hình khung giờ nhận (slot_id, label, start_minute, end_minute, max_orders, daypart).

## 3. DANH SÁCH VIEW ĐÃ TÍNH SẴN CÔNG THỨC (KHUYÊN DÙNG)
- v_completed_orders: Đơn COMPLETED.
- v_completed_items: Dòng món của đơn COMPLETED.
- v_cancelled_orders: Đơn CANCELLED.
- v_daily: Thống kê theo ngày (date, weekday, week_start, revenue, orders, portions, cancelled_orders, in_progress_orders, cancel_rate, discount_total, avg_prep_minutes).
- v_item_daily: Thống kê món theo ngày (date, weekday, dish_key, name, category, portions, revenue).
- v_slot_daily: Thống kê khung giờ theo ngày (date, weekday, slot_id, slot_label, daypart, max_orders, orders, fill_rate, is_full).
- v_item_sellthrough: Thống kê tỷ lệ bán các món có hạn mức (date, weekday, dish_key, name, category, daily_quota, sold_qty, sell_through, sold_out).
- v_pnl_daily: Doanh thu gộp, giảm giá, doanh thu thuần, chi phí và lãi gộp theo ngày (date, gross_revenue, discount_total, net_revenue, cost, gross_profit).

## 4. CÁC CÂU SQL MẪU
1. Hôm qua bán được bao nhiêu?
   SELECT revenue, orders, portions FROM v_daily WHERE date = date('now','+7 hours','-1 day');
2. Tuần này so với tuần trước?
   SELECT week_start, SUM(revenue) AS revenue, SUM(orders) AS orders FROM v_daily GROUP BY week_start ORDER BY week_start DESC LIMIT 2;
3. Món nào bán chạy nhất thứ Sáu?
   SELECT name, SUM(portions) AS portions, SUM(revenue) AS revenue FROM v_item_daily WHERE weekday = 5 GROUP BY dish_key, name ORDER BY portions DESC LIMIT 5;
4. Khung giờ nào hay đầy nhất?
   SELECT slot_label, SUM(is_full) AS full_days, COUNT(*) AS days, AVG(fill_rate) AS avg_fill FROM v_slot_daily GROUP BY slot_label ORDER BY avg_fill DESC;
5. Khung chiều tối có đáng mở không?
   SELECT daypart, AVG(fill_rate) AS avg_fill, SUM(orders) AS orders FROM v_slot_daily GROUP BY daypart;
6. Bao nhiêu đơn hủy, vì sao?
   SELECT cancel_reason, COUNT(*) AS orders FROM v_cancelled_orders GROUP BY cancel_reason ORDER BY orders DESC;
7. Đơn hủy vì quá tải chủ yếu thứ mấy?
   SELECT weekday, COUNT(*) AS orders FROM v_cancelled_orders WHERE cancel_reason = 'OVERLOAD' GROUP BY weekday ORDER BY orders DESC;
8. Bếp chuẩn bị mất bao lâu theo khung giờ?
   SELECT slot_label, AVG(prep_minutes) AS avg_prep, COUNT(prep_minutes) AS orders FROM v_completed_orders GROUP BY slot_label ORDER BY slot_label;
9. Món nào ế (tỷ lệ bán thấp)?
   SELECT name, AVG(sell_through) AS avg_sell_through, SUM(sold_out) AS sold_out_days, COUNT(*) AS days FROM v_item_sellthrough GROUP BY dish_key, name ORDER BY avg_sell_through ASC LIMIT 5;
10. Đồ uống đóng góp bao nhiêu doanh thu mỗi khung?
    SELECT slot_label, SUM(line_revenue) AS revenue FROM v_completed_items WHERE category = 'Đồ uống' GROUP BY slot_label ORDER BY slot_label;
11. Hôm nay có món chay dưới 30k còn suất không?
    SELECT name, price, remaining_qty FROM menu_items WHERE date = date('now','+7 hours') AND tags LIKE '%,vegetarian,%' AND price < 30000 AND available = 1;
12. Hôm qua doanh thu thuần, chi phí và lãi gộp bao nhiêu?
    SELECT gross_revenue, discount_total, net_revenue, cost, gross_profit FROM v_pnl_daily WHERE date = date('now','+7 hours','-1 day');
13. Voucher đã giảm tổng cộng bao nhiêu, mã nào được dùng nhiều nhất?
    SELECT voucher_code, COUNT(*) AS uses, SUM(discount_amount) AS discount FROM orders WHERE voucher_code IS NOT NULL AND status <> 'CANCELLED' GROUP BY voucher_code ORDER BY uses DESC;
`;
}
