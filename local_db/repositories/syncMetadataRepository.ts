
import { getDatabase } from '../database';

export async function getLastSyncedAt(
  key: string
): Promise<string | null> {
  const db = await getDatabase();

  const result = await db.getFirstAsync<{ last_synced_at: string | null }>(
    `SELECT last_synced_at
     FROM sync_metadata
     WHERE key = ?`,
    key
  );

  return result?.last_synced_at ?? null;
}

export async function updateLastSyncedAt(
  key: string,
  timestamp: string
): Promise<void> {
  const db = await getDatabase();

  await db.runAsync(
    `INSERT INTO sync_metadata (key, last_synced_at)
     VALUES (?, ?)
     ON CONFLICT(key)
     DO UPDATE SET last_synced_at = excluded.last_synced_at`,
    key,
    timestamp
  );
}