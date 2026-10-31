import { Database } from 'sql.js';
import { DataSource } from '../data/dataSource.js';
import { DateRange } from '../data/types.js';
import { loadDatabase } from './loader.js';
import { Config } from '../config.js';

interface CacheEntry {
  key: string;
  db: Database;
  createdAt: number;
}

export class DatabaseCache {
  private entries: Map<string, CacheEntry> = new Map();
  private maxEntries: number = 3;

  private makeKey(source: string, range: DateRange): string {
    return `${source}|${range.from}|${range.to}`;
  }

  async getDatabase(
    dataSource: DataSource,
    range: DateRange,
    config: Config
  ): Promise<Database> {
    const key = this.makeKey(config.dataSource, range);
    const now = Date.now();
    const ttlMs = config.sqlCacheSeconds * 1000;

    // Nếu cache bật (ttlMs > 0) và còn hạn
    if (ttlMs > 0 && this.entries.has(key)) {
      const entry = this.entries.get(key)!;
      if (now - entry.createdAt < ttlMs) {
        return entry.db;
      } else {
        // Hết hạn
        entry.db.close();
        this.entries.delete(key);
      }
    }

    // Nạp mới
    const { db } = await loadDatabase(dataSource, range, config.timezone);

    if (ttlMs > 0) {
      // Đảm bảo tối đa 3 mục
      if (this.entries.size >= this.maxEntries) {
        const oldestKey = this.entries.keys().next().value;
        if (oldestKey) {
          const oldEntry = this.entries.get(oldestKey);
          oldEntry?.db.close();
          this.entries.delete(oldestKey);
        }
      }

      this.entries.set(key, {
        key,
        db,
        createdAt: now,
      });
    }

    return db;
  }

  clear(): void {
    for (const entry of this.entries.values()) {
      try {
        entry.db.close();
      } catch {
        // ignore
      }
    }
    this.entries.clear();
  }
}

export const sharedDatabaseCache = new DatabaseCache();

