import { parentPort, workerData } from 'node:worker_threads';
import initSqlJs from 'sql.js';

interface WorkerInput {
  dbBinary: Uint8Array;
  sql: string;
  maxRows: number;
}

interface WorkerOutput {
  success: boolean;
  columns?: string[];
  rows?: any[][];
  rowCount?: number;
  truncated?: boolean;
  error?: string;
}

async function runQuery() {
  if (!parentPort) return;

  const { dbBinary, sql, maxRows } = workerData as WorkerInput;

  try {
    const SQL = await initSqlJs();
    const db = new SQL.Database(dbBinary);

    // Kích hoạt chế độ chỉ đọc ở tầng SQLite
    db.run('PRAGMA query_only = ON;');

    const stmt = db.prepare(sql);
    const columns = stmt.getColumnNames();
    const rows: any[][] = [];
    let count = 0;

    while (stmt.step()) {
      if (count < maxRows) {
        rows.push(stmt.get());
      }
      count++;
    }

    stmt.free();
    db.close();

    const output: WorkerOutput = {
      success: true,
      columns,
      rows,
      rowCount: count,
      truncated: count > maxRows,
    };

    parentPort.postMessage(output);
  } catch (err: unknown) {
    const errMsg = err instanceof Error ? err.message : String(err);
    const output: WorkerOutput = {
      success: false,
      error: errMsg,
    };
    parentPort.postMessage(output);
  }
}

runQuery();
