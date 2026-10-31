#!/usr/bin/env node
/**
 * Sinh và nạp dữ liệu lịch sử giả (mặc định 4 tuần) vào Firestore để demo báo cáo / MCP.
 *
 * Ghi 3 collection: menuItems (các ngày cũ), orders, timeSlotCounters.
 * Mọi document sinh ra có `demoHistory: true` và id bắt đầu bằng `hist_`,
 * nên chạy lại là ghi đè (idempotent) và `--clean` xóa sạch được.
 *
 * Cách dùng (xem README.md):
 *   node seed-history.js --dry-run                 # chỉ sinh docs/data/seed-history.json, không cần key
 *   node seed-history.js --key ./serviceAccount.json
 *   node seed-history.js --key ./serviceAccount.json --clean
 *
 * Tùy chọn: --weeks 4  --seed 20260101
 */
const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..', '..');
const MENU_FILE = path.join(ROOT, 'docs', 'data', 'seed-menu.json');
const OUT_FILE = path.join(ROOT, 'docs', 'data', 'seed-history.json');

// ---------- CLI ----------
const argv = process.argv.slice(2);
const flag = (n) => argv.includes(`--${n}`);
const opt = (n, d) => {
  const i = argv.indexOf(`--${n}`);
  return i >= 0 && argv[i + 1] ? argv[i + 1] : d;
};
const WEEKS = parseInt(opt('weeks', '4'), 10);
const SEED = parseInt(opt('seed', '20260101'), 10);
const DRY = flag('dry-run');
const CLEAN = flag('clean');
const KEY = opt('key', process.env.GOOGLE_APPLICATION_CREDENTIALS || '');

// ---------- RNG (cố định seed để mỗi lần ra cùng kết quả) ----------
function mulberry32(a) {
  return () => {
    a |= 0; a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
const rnd = mulberry32(SEED);
const rint = (lo, hi) => lo + Math.floor(rnd() * (hi - lo + 1));
const pickWeighted = (items, weightOf) => {
  const total = items.reduce((s, it) => s + weightOf(it), 0);
  let r = rnd() * total;
  for (const it of items) { r -= weightOf(it); if (r <= 0) return it; }
  return items[items.length - 1];
};
const pad = (n, w = 2) => String(n).padStart(w, '0');

// ---------- Dữ liệu nền ----------
const menu = JSON.parse(fs.readFileSync(MENU_FILE, 'utf8'));
const CATALOG = menu.menuItems;

// Độ phổ biến cơ bản từng món (món thấp = "ế") và hệ số theo thứ (1 = Thứ 2 ... 6 = Thứ 7)
const BASE_WEIGHT = {
  'com-suon-nuong': 1.0, 'com-thit-kho-trung': 0.8, 'ga-ran-gion': 0.9, 'bun-bo-hue': 0.8,
  'pho-bo': 0.7, 'mi-xao': 0.2, 'banh-mi-thap-cam': 0.6, 'xoi-thap-cam': 0.3,
  'ca-phe-sua-da': 1.0, 'tra-chanh': 0.9,
  'com-ga-tam-ky': 0.9, 'com-chien-duong-chau': 0.6, 'hu-tieu-nam-vang': 0.6, 'bun-rieu-cua': 0.5,
  'bun-cha': 0.7, 'banh-cuon': 0.4, 'goi-cuon-chay': 0.45, 'cha-gio': 0.5,
  'nuoc-cam-ep': 0.6, 'sinh-to-bo': 0.5,
};
const WEEKDAY_BOOST = {
  1: { 'com-suon-nuong': 1.3 },
  4: { 'ga-ran-gion': 1.4 },
  5: { 'bun-bo-hue': 1.5, 'pho-bo': 1.5 },
  6: { 'banh-mi-thap-cam': 1.5, 'xoi-thap-cam': 1.5 },
};
const DAY_FACTOR = { 1: 0.9, 2: 1.0, 3: 1.0, 4: 1.0, 5: 1.1, 6: 0.55 };
const SLOT_FILL = [0.55, 1.0, 0.85, 0.4, 0.35, 0.6, 0.5, 0.25]; // theo thứ tự khung giờ trong ngày (4 khung trưa, 4 khung chiều tối)
const DRINK_ATTACH = 0.45;

// Hạn mức để tỷ lệ bán ra có ý nghĩa với 50-70 đơn/ngày (xem README)
const quotaOf = (d) => d.dailyQuota || (d.category === 'Đồ uống' ? 25 : 14);

const STUDENTS = [
  'Nguyễn Minh Anh', 'Trần Quốc Bảo', 'Lê Thu Hà', 'Phạm Gia Huy', 'Hoàng Ngọc Lan',
  'Vũ Đức Anh', 'Đặng Khánh Linh', 'Bùi Tuấn Kiệt', 'Đỗ Thảo Nhi', 'Ngô Hải Đăng',
  'Dương Mai Chi', 'Lý Anh Khoa', 'Phan Bảo Ngọc', 'Võ Thanh Tùng', 'Trương Hà My',
  'Đinh Công Minh', 'Mai Phương Thảo', 'Tạ Quang Vinh', 'Hồ Yến Nhi', 'Lâm Nhật Nam',
  'Cao Diệu Linh', 'Tô Gia Bảo', 'Châu Khải Hưng', 'Nguyễn Hoài An', 'Trịnh Bích Ngọc',
  'Lương Đình Phúc', 'Kiều Thùy Dương', 'La Minh Quân', 'Thái Hồng Nhung', 'Quách Duy Khang',
].map((name, i) => ({ id: `demo_student_${pad(i + 1)}`, name }));

// ---------- Ngày ----------
function vnDate(d) { // yyyy-MM-dd theo giờ VN
  const v = new Date(d.getTime() + 7 * 3600 * 1000);
  return `${v.getUTCFullYear()}-${pad(v.getUTCMonth() + 1)}-${pad(v.getUTCDate())}`;
}
const toDate = (date, minuteOfDay) =>
  new Date(`${date}T${pad(Math.floor(minuteOfDay / 60))}:${pad(minuteOfDay % 60)}:${pad(rint(0, 59))}+07:00`);
const addMin = (dt, m) => new Date(dt.getTime() + Math.round(m * 60000));

function historyDays() {
  const days = [];
  const today = new Date();
  for (let back = WEEKS * 7; back >= 1; back--) {
    const d = new Date(today.getTime() - back * 86400000);
    const date = vnDate(d);
    const dow = new Date(`${date}T12:00:00+07:00`).getUTCDay(); // 0 = CN
    if (dow === 0) continue;
    days.push({ date, weekday: dow, week: Math.floor((WEEKS * 7 - back) / 7) });
  }
  return days;
}

// ---------- Sinh dữ liệu ----------
// Hàm băm cố định theo chuỗi -> số 0..1, KHÔNG dùng bộ sinh ngẫu nhiên chính để không làm đổi dãy số của dữ liệu cũ
const frac = (str) => { let h = 7; for (const ch of str) h = (h * 31 + ch.charCodeAt(0)) >>> 0; return (h % 1000) / 1000; };

function generate(slots) {
  const redeemed = new Set(); // mỗi học sinh dùng mỗi mã voucher một lần
  const days = historyDays();
  const slowIdx = days.length - 9;   // ngày bếp chậm, nhiều đơn quá tải
  const spikeIdx = days.length - 4;  // ngày nhiều đơn không đến lấy
  const out = { menuItems: [], orders: [], timeSlotCounters: [] };

  days.forEach((day, di) => {
    const slow = di === slowIdx;
    const spike = di === spikeIdx;
    const sold = Object.fromEntries(CATALOG.map((d) => [d.dishKey, 0]));
    const dayOrders = [];
    const usedCodes = new Set();
    const dayFactor = (DAY_FACTOR[day.weekday] || 1) * (1 + 0.06 * day.week);

    slots.forEach((slot, si) => {
      const fill = SLOT_FILL[si] ?? 0.6;
      const n = Math.max(0, Math.min(slot.maxOrders,
        Math.round(slot.maxOrders * fill * dayFactor * (0.85 + 0.3 * rnd()))));
      for (let k = 0; k < n; k++) {
        const available = (extra = 0) => CATALOG.filter((d) => sold[d.dishKey] + 1 + extra <= quotaOf(d));
        const weight = (d) => (BASE_WEIGHT[d.dishKey] ?? 0.5) * ((WEEKDAY_BOOST[day.weekday] || {})[d.dishKey] || 1);
        const mains = available().filter((d) => d.category !== 'Đồ uống');
        if (!mains.length) continue;
        const lines = [];
        const addLine = (dish, qty) => { sold[dish.dishKey] += qty; lines.push({ dish, qty }); };
        addLine(pickWeighted(mains, weight), rnd() < 0.1 ? 2 : 1);
        if (rnd() < DRINK_ATTACH) {
          const drinks = available().filter((d) => d.category === 'Đồ uống');
          if (drinks.length) addLine(pickWeighted(drinks, weight), 1);
        }
        let code;
        do { code = pad(rint(0, 9999), 4); } while (usedCodes.has(code));
        usedCodes.add(code);

        const student = STUDENTS[rint(0, STUDENTS.length - 1)];
        const lead = rint(20, 180);
        const createdAt = toDate(day.date, Math.max(390, slot.startMinute - lead));
        const slotStart = toDate(day.date, slot.startMinute);
        const slotPos = fill >= 0.85 ? 2 : 0;
        const prepMin = (5 + 0.7 * lines.length + rnd() * 5 + slotPos) * (slow ? 1.8 : 1);
        let preparingAt = addMin(slotStart, -rint(6, 14));
        if (preparingAt <= createdAt) preparingAt = addMin(createdAt, rint(1, 3));
        const readyAt = addMin(preparingAt, prepMin);
        const completedAt = addMin(readyAt, Math.min(20, 1 + -Math.log(1 - rnd()) * 4));

        const items = lines.map(({ dish, qty }) => ({
          menuItemId: `hist_${day.date}_${dish.dishKey}`,
          name: dish.name, price: dish.price, quantity: qty,
          category: dish.category, dishKey: dish.dishKey,
        }));
        const order = {
          id: `hist_${day.date}_${pad(dayOrders.length + 1, 3)}`,
          userId: student.id, userName: student.name, items,
          slotId: slot.id, slotLabel: slot.label, pickupCode: code,
          total: items.reduce((s, it) => s + it.price * it.quantity, 0),
          date: day.date, createdAt, demoHistory: true,
        };

        // Ưu đãi (v2): voucher và điểm rèn luyện, tính theo quy tắc ở docs/features-v2-plan.md mục 5.1
        const subtotal = order.total;
        const f = frac(order.id);
        let voucherCode = '', discount = 0, pointsUsed = 0;
        if (f < 0.08 && subtotal >= 30000) { voucherCode = 'WELCOME10'; discount = Math.min(Math.floor(subtotal * 0.1), 15000); }
        else if (f < 0.14 && subtotal >= 20000) { voucherCode = 'GIAM5K'; discount = 5000; }
        else if (f < 0.18) { voucherCode = 'SINHVIEN'; discount = 3000; }
        if (voucherCode && redeemed.has(`${student.id}|${voucherCode}`)) { voucherCode = ''; discount = 0; }
        if (voucherCode) redeemed.add(`${student.id}|${voucherCode}`);
        if (frac(order.id + 'p') < 0.06) {
          const cap = Math.min(subtotal - discount, Math.floor(subtotal * 0.2));
          pointsUsed = Math.min(20, Math.floor(cap / 500));
          discount += pointsUsed * 500;
        }
        order.subtotal = subtotal; order.discountAmount = discount; order.voucherCode = voucherCode;
        order.pointsUsed = pointsUsed; order.total = subtotal - discount;

        const cancelP = slow ? 0.12 : 0.07;
        if (rnd() < cancelP) {
          const reason = pickWeighted(
            [['SOLD_OUT', 0.2], ['OVERLOAD', slow ? 0.5 : 0.15], ['NO_SHOW', spike ? 0.6 : 0.3], ['CHANGED_MIND', 0.35]],
            (r) => r[1])[0];
          order.status = 'CANCELLED';
          order.cancelReason = reason;
          order.cancelledBy = reason === 'CHANGED_MIND' ? 'STUDENT' : 'CANTEEN';
          if (reason === 'NO_SHOW') {
            order.preparingAt = preparingAt; order.readyAt = readyAt;
            order.cancelledAt = addMin(readyAt, rint(15, 25));
          } else if (reason === 'OVERLOAD') {
            order.cancelledAt = addMin(slotStart, -rint(2, 8));
          } else {
            order.cancelledAt = addMin(createdAt, rint(2, 20));
          }
          order.updatedAt = order.cancelledAt;
          if (reason !== 'SOLD_OUT') lines.forEach(({ dish, qty }) => { sold[dish.dishKey] -= qty; });
        } else {
          order.status = 'COMPLETED';
          order.preparingAt = preparingAt; order.readyAt = readyAt;
          order.completedAt = completedAt; order.updatedAt = completedAt;
        }
        dayOrders.push(order);
      }
    });

    out.orders.push(...dayOrders);

    // Bộ đếm khung giờ = số đơn không bị hủy (khớp cách app tăng/giảm count)
    slots.forEach((slot) => {
      const count = dayOrders.filter((o) => o.slotId === slot.id && o.status !== 'CANCELLED').length;
      out.timeSlotCounters.push({
        id: `${slot.id}_${day.date}`, slotId: slot.id, date: day.date, count, demoHistory: true,
      });
    });

    // Menu của ngày đó với số suất đã bán cuối ngày
    CATALOG.forEach((d) => {
      const quota = quotaOf(d);
      out.menuItems.push({
        id: `hist_${day.date}_${d.dishKey}`,
        name: d.name, description: d.description, price: d.price, imageUrl: d.imageUrl,
        costPrice: d.costPrice || 0,
        category: d.category, date: day.date, dishKey: d.dishKey, tags: d.tags,
        dailyQuota: quota, soldQty: sold[d.dishKey],
        inStock: sold[d.dishKey] < quota, demoHistory: true,
      });
    });
  });
  return out;
}

function summarize(data) {
  const orders = data.orders;
  const done = orders.filter((o) => o.status === 'COMPLETED');
  const cancelled = orders.filter((o) => o.status === 'CANCELLED');
  const byReason = {};
  cancelled.forEach((o) => { byReason[o.cancelReason] = (byReason[o.cancelReason] || 0) + 1; });
  const qty = {};
  done.forEach((o) => o.items.forEach((it) => { qty[it.name] = (qty[it.name] || 0) + it.quantity; }));
  const top = Object.entries(qty).sort((a, b) => b[1] - a[1]);
  const avg = (arr) => (arr.length ? (arr.reduce((s, v) => s + v, 0) / arr.length).toFixed(1) : '-');
  const prep = done.map((o) => (o.readyAt - o.preparingAt) / 60000);
  console.log(`Số ngày        : ${new Set(orders.map((o) => o.date)).size}`);
  console.log(`Tổng đơn       : ${orders.length} (hoàn thành ${done.length}, hủy ${cancelled.length})`);
  console.log(`Doanh thu      : ${done.reduce((s, o) => s + o.total, 0).toLocaleString('vi-VN')}đ`);
  console.log(`Hủy theo lý do : ${JSON.stringify(byReason)}`);
  console.log(`Chuẩn bị TB    : ${avg(prep)} phút`);
  console.log(`Bán chạy nhất  : ${top.slice(0, 3).map((t) => `${t[0]} (${t[1]})`).join(', ')}`);
  console.log(`Ế nhất         : ${top.slice(-2).map((t) => `${t[0]} (${t[1]})`).join(', ')}`);
}

// ---------- Firestore ----------
function loadAdmin() {
  let admin;
  try { admin = require('firebase-admin'); } catch (e) {
    console.error('Thiếu firebase-admin. Chạy: npm install (trong thư mục tools/seed-history)');
    process.exit(1);
  }
  if (!KEY || !fs.existsSync(KEY)) {
    console.error('Cần --key <đường dẫn serviceAccount.json> (hoặc biến GOOGLE_APPLICATION_CREDENTIALS).');
    process.exit(1);
  }
  admin.initializeApp({ credential: admin.credential.cert(JSON.parse(fs.readFileSync(KEY, 'utf8'))) });
  return admin;
}

const toFirestore = (admin, obj) => {
  const out = {};
  for (const [k, v] of Object.entries(obj)) {
    if (k === 'id') continue;
    out[k] = v instanceof Date ? admin.firestore.Timestamp.fromDate(v) : v;
  }
  return out;
};

async function commitInChunks(db, writes) {
  for (let i = 0; i < writes.length; i += 400) {
    const batch = db.batch();
    writes.slice(i, i + 400).forEach((w) => w(batch));
    await batch.commit();
  }
}

async function main() {
  if (DRY) {
    const slots = menu.timeSlots.map((s, i) => ({ ...s, id: `slot${i + 1}` }))
      .sort((a, b) => a.startMinute - b.startMinute);
    const data = generate(slots);
    fs.writeFileSync(OUT_FILE, JSON.stringify(data, null, 2) + '\n');
    console.log(`Đã ghi ${path.relative(ROOT, OUT_FILE)} (dry-run, slotId giả slot1..slotN)\n`);
    summarize(data);
    return;
  }

  const admin = loadAdmin();
  const db = admin.firestore();

  if (CLEAN) {
    for (const col of ['orders', 'menuItems', 'timeSlotCounters']) {
      const snap = await db.collection(col).where('demoHistory', '==', true).get();
      await commitInChunks(db, snap.docs.map((d) => (b) => b.delete(d.ref)));
      console.log(`Đã xóa ${snap.size} document trong ${col}`);
    }
    return;
  }

  const slotSnap = await db.collection('timeSlots').get();
  if (slotSnap.empty) {
    console.error('Chưa có timeSlots trong Firestore. Đăng nhập app một lần để seed khung giờ rồi chạy lại.');
    process.exit(1);
  }
  const slots = slotSnap.docs.map((d) => ({ id: d.id, ...d.data() }))
    .sort((a, b) => a.startMinute - b.startMinute);
  const data = generate(slots);

  const writes = [];
  for (const col of ['menuItems', 'orders', 'timeSlotCounters']) {
    data[col].forEach((doc) =>
      writes.push((b) => b.set(db.collection(col).doc(doc.id), toFirestore(admin, doc))));
  }
  await commitInChunks(db, writes);
  console.log(`Đã nạp ${data.orders.length} đơn, ${data.menuItems.length} dòng menu, ${data.timeSlotCounters.length} bộ đếm.\n`);
  summarize(data);
}

main().catch((e) => { console.error(e); process.exit(1); });
