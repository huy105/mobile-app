import crypto from 'node:crypto';

export function maskUserId(userId: string): string {
  if (!userId) return 'khach_an_danh';
  const hash = crypto.createHash('md5').update(userId).digest('hex').slice(0, 3);
  return `khach_${hash}`;
}

export function sanitizeText(text: string): string {
  // Loại bỏ các định danh nhạy cảm nếu có lọt vào chuỗi
  return text.replace(/demo_student_\w+/g, 'khach_***');
}
