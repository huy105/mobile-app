import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { Worker } from 'node:worker_threads';
import { Database } from 'sql.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

export interface QueryResult {
  columns: string[];
  rows: any[][];
  rowCount: number;
  truncated: boolean;
}

/**
 * Chạy câu SQL trực tiếp trên database connection với PRAGMA query_only = ON
 */
export function executeQueryDirect(
  db: Database,
  sql: string,
  maxRows: number = 200
): QueryResult {
  // Lớp bảo vệ PRAGMA query_only
  db.run('PRAGMA query_only = ON;');

  const stmt = db.prepare(sql);
  const columns = stmt.getColumnNames();
  const rows: any[][] = [];
  let count = 0;

  try {
    while (stmt.step()) {
      if (count < maxRows) {
        rows.push(stmt.get());
      }
      count++;
    }
  } finally {
    stmt.free();
  }

  return {
    columns,
    rows,
    rowCount: count,
    truncated: count > maxRows,
  };
}

/**
 * Chạy câu SQL trong Worker thread để đảm bảo timeout và cô lập tài nguyên
 */
export async function executeQueryInWorker(
  db: Database,
  sql: string,
  maxRows: number = 200,
  timeoutMs: number = 5000
): Promise<QueryResult> {
  const binary = db.export();

  // Tìm đường dẫn file worker.js (khi build tsc nằm tại dist/sql/worker.js)
  const candidatePaths = [
    path.resolve(__dirname, './worker.js'),
    path.resolve(__dirname, '../../dist/sql/worker.js'),
  ];
  const workerFile = candidatePaths.find((p) => fs.existsSync(p));

  // Nếu không tìm thấy file worker.js (ví dụ chạy ts-node chưa build), fallback chạy direct
  if (!workerFile) {
    return executeQueryDirect(db, sql, maxRows);
  }

  return new Promise<QueryResult>((resolve, reject) => {
    let timer: NodeJS.Timeout | null = null;
    let finished = false;

    const worker = new Worker(workerFile, {
      workerData: {
        dbBinary: binary,
        sql,
        maxRows,
      },
    });

    const cleanup = () => {
      finished = true;
      if (timer) {
        clearTimeout(timer);
        timer = null;
      }
    };

    timer = setTimeout(() => {
      if (!finished) {
        cleanup();
        worker.terminate().catch(() => {});
        reject(new Error('Câu truy vấn quá lâu, hãy thu hẹp điều kiện.'));
      }
    }, timeoutMs);

    worker.on('message', (msg) => {
      cleanup();
      if (msg.success) {
        resolve({
          columns: msg.columns,
          rows: msg.rows,
          rowCount: msg.rowCount,
          truncated: msg.truncated,
        });
      } else {
        reject(new Error(msg.error || 'Lỗi thực thi câu truy vấn SQL.'));
      }
    });

    worker.on('error', (err) => {
      cleanup();
      reject(err);
    });

    worker.on('exit', (code) => {
      cleanup();
      if (!finished && code !== 0) {
        reject(new Error(`Worker thread kết thúc với mã lỗi ${code}`));
      }
    });
  });
}

