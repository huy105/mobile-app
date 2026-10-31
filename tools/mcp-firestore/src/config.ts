import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

export interface Config {
  dataSource: 'firestore' | 'fixture';
  serviceAccountPath?: string;
  timezone: string;
  maskPii: boolean;
  fixturePath: string;
  sqlCacheSeconds: number;
  sqlMaxRows: number;
  sqlTimeoutMs: number;
}

export function loadConfig(): Config {
  const dataSource =
    (process.env.DATA_SOURCE || 'firestore').toLowerCase() === 'fixture'
      ? 'fixture'
      : 'firestore';
  const serviceAccountPath = process.env.SERVICE_ACCOUNT_PATH || undefined;
  const timezone = process.env.TIMEZONE || 'Asia/Ho_Chi_Minh';
  const maskPii = process.env.MASK_PII ? process.env.MASK_PII.toLowerCase() === 'true' : true;

  let fixturePath: string;
  if (process.env.FIXTURE_PATH) {
    fixturePath = path.resolve(process.env.FIXTURE_PATH);
  } else {
    const repoFixturePath = path.resolve(__dirname, '../../../docs/data/seed-history.json');
    const localFixturePath = path.resolve(__dirname, '../fixtures/seed-history.json');
    if (fs.existsSync(repoFixturePath)) {
      fixturePath = repoFixturePath;
    } else if (fs.existsSync(localFixturePath)) {
      fixturePath = localFixturePath;
    } else {
      fixturePath = repoFixturePath;
    }
  }

  const sqlCacheSeconds = process.env.SQL_CACHE_SECONDS !== undefined
    ? parseInt(process.env.SQL_CACHE_SECONDS, 10)
    : 60;
  const sqlMaxRows = process.env.SQL_MAX_ROWS !== undefined
    ? parseInt(process.env.SQL_MAX_ROWS, 10)
    : 200;
  const sqlTimeoutMs = process.env.SQL_TIMEOUT_MS !== undefined
    ? parseInt(process.env.SQL_TIMEOUT_MS, 10)
    : 5000;

  return {
    dataSource,
    serviceAccountPath,
    timezone,
    maskPii,
    fixturePath,
    sqlCacheSeconds: isNaN(sqlCacheSeconds) ? 60 : sqlCacheSeconds,
    sqlMaxRows: isNaN(sqlMaxRows) ? 200 : sqlMaxRows,
    sqlTimeoutMs: isNaN(sqlTimeoutMs) ? 5000 : sqlTimeoutMs,
  };
}
