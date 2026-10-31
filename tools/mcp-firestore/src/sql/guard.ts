const FORBIDDEN_KEYWORDS = [
  'INSERT',
  'UPDATE',
  'DELETE',
  'DROP',
  'ALTER',
  'CREATE',
  'REPLACE',
  'ATTACH',
  'DETACH',
  'PRAGMA',
  'VACUUM',
  'REINDEX',
  'ANALYZE',
  'BEGIN',
  'COMMIT',
  'ROLLBACK',
  'SAVEPOINT',
  'RELEASE',
  'LOAD_EXTENSION',
];

export interface GuardValidationResult {
  valid: boolean;
  cleanSql?: string;
  error?: string;
}

/**
 * Loại bỏ chú thích (-- và /* *\/) nhưng giữ nguyên nội dung trong nháy đơn/kép.
 */
export function stripComments(sql: string): string {
  let result = '';
  let i = 0;
  const n = sql.length;
  let inSingle = false;
  let inDouble = false;

  while (i < n) {
    const ch = sql[i];
    const next = i + 1 < n ? sql[i + 1] : '';

    if (!inSingle && !inDouble) {
      if (ch === '-' && next === '-') {
        // Bỏ qua đến hết dòng
        i += 2;
        while (i < n && sql[i] !== '\n' && sql[i] !== '\r') {
          i++;
        }
        continue;
      }
      if (ch === '/' && next === '*') {
        // Bỏ qua đến hết */
        i += 2;
        while (i + 1 < n && !(sql[i] === '*' && sql[i + 1] === '/')) {
          i++;
        }
        i += 2; // bỏ qua */
        continue;
      }
    }

    if (ch === "'" && !inDouble) {
      inSingle = !inSingle;
    } else if (ch === '"' && !inSingle) {
      inDouble = !inDouble;
    }

    result += ch;
    i++;
  }

  return result.trim();
}

/**
 * Kiểm tra tính an toàn của câu SQL chỉ-đọc
 */
export function validateSql(rawSql: string): GuardValidationResult {
  if (!rawSql || !rawSql.trim()) {
    return { valid: false, error: 'Câu truy vấn SQL không được để trống.' };
  }

  if (rawSql.length > 4000) {
    return { valid: false, error: 'Độ dài câu truy vấn SQL vượt quá giới hạn 4000 ký tự.' };
  }

  const clean = stripComments(rawSql);
  if (!clean) {
    return { valid: false, error: 'Câu truy vấn không có nội dung sau khi bỏ chú thích.' };
  }

  // Tách các ký tự để kiểm tra dấu chấm phẩy và từ khóa ngoài chuỗi
  let inSingle = false;
  let inDouble = false;
  let currentWord = '';
  const wordsOutsideQuotes: string[] = [];
  let semicolonCount = 0;

  // Loại bỏ dấu ; ở cuối cùng nếu có
  let trimmed = clean;
  while (trimmed.endsWith(';')) {
    trimmed = trimmed.slice(0, -1).trim();
  }

  for (let i = 0; i < trimmed.length; i++) {
    const ch = trimmed[i];

    if (ch === "'" && !inDouble) {
      inSingle = !inSingle;
      if (inSingle && currentWord) {
        wordsOutsideQuotes.push(currentWord.toUpperCase());
        currentWord = '';
      }
      continue;
    }

    if (ch === '"' && !inSingle) {
      inDouble = !inDouble;
      if (inDouble && currentWord) {
        wordsOutsideQuotes.push(currentWord.toUpperCase());
        currentWord = '';
      }
      continue;
    }

    if (!inSingle && !inDouble) {
      if (ch === ';') {
        semicolonCount++;
      }

      if (/[a-zA-Z0-9_]/.test(ch)) {
        currentWord += ch;
      } else {
        if (currentWord) {
          wordsOutsideQuotes.push(currentWord.toUpperCase());
          currentWord = '';
        }
      }
    }
  }

  if (currentWord) {
    wordsOutsideQuotes.push(currentWord.toUpperCase());
  }

  if (semicolonCount > 0) {
    return {
      valid: false,
      error: 'Chỉ cho phép chạy một câu lệnh SQL duy nhất (không được chứa nhiều câu lệnh ngăn cách bằng dấu chấm phẩy).',
    };
  }

  if (wordsOutsideQuotes.length === 0) {
    return { valid: false, error: 'Câu truy vấn không có từ khóa hợp lệ.' };
  }

  const firstWord = wordsOutsideQuotes[0];
  if (firstWord !== 'SELECT' && firstWord !== 'WITH') {
    return {
      valid: false,
      error: 'Câu truy vấn phải bắt đầu bằng SELECT hoặc WITH.',
    };
  }

  // Kiểm tra từ khóa bị cấm
  for (const word of wordsOutsideQuotes) {
    if (FORBIDDEN_KEYWORDS.includes(word)) {
      return {
        valid: false,
        error: `Từ khóa "${word}" không được phép trong câu truy vấn chỉ-đọc.`,
      };
    }
  }

  return {
    valid: true,
    cleanSql: trimmed,
  };
}

