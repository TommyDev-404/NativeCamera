import { getDatabase } from './database';

export async function initializeDatabase() {
  const db = await getDatabase();

  await db.execAsync(`
    PRAGMA journal_mode = WAL;

    CREATE TABLE IF NOT EXISTS events (
      id INTEGER PRIMARY KEY,
      name TEXT NOT NULL,
      start_date TEXT NOT NULL,
      end_date TEXT NOT NULL,
      start_time TEXT NOT NULL,
      end_time TEXT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS stamping_records (
      id TEXT PRIMARY KEY,
      student_id TEXT NOT NULL,
      event_id INTEGER NOT NULL,
      stamp_date TEXT NOT NULL,
      morning_in TEXT,
      morning_out TEXT,
      afternoon_in TEXT,
      afternoon_out TEXT,
      sync_status TEXT NOT NULL DEFAULT 'PENDING',
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL,
    
      UNIQUE(student_id, event_id, stamp_date)
    );

    CREATE TABLE IF NOT EXISTS sync_metadata (
      key TEXT PRIMARY KEY,
      last_synced_at TEXT
    );
  `);

  console.log('SQLite database initialized successfully');
}