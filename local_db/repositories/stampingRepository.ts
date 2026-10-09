
import { randomUUID } from 'expo-crypto';
import { getDatabase } from '../database';

export type StampColumn =
  | 'morning_in'
  | 'morning_out'
  | 'afternoon_in'
  | 'afternoon_out';

export type StampingRecord = {
  id: string;
  student_id: string;
  event_id: number;
  stamp_date: string;
  morning_in: string | null;
  morning_out: string | null;
  afternoon_in: string | null;
  afternoon_out: string | null;
  sync_status?: 'PENDING' | 'SYNCED';
  created_at: string;
  updated_at: string;
};

export async function getStampingRecord(
  studentId: string,
  eventId: number,
  stampDate: string,
): Promise<StampingRecord | null> {
  const db = await getDatabase();

  return db.getFirstAsync<StampingRecord>(
    `SELECT *
     FROM stamping_records
     WHERE student_id = ?
       AND event_id = ?
       AND stamp_date = ?`,
    studentId,
    eventId,
    stampDate,
  );
}

export async function saveStamp(
  studentId: string,
  eventId: number,
  stampDate: string,
  column: StampColumn,
  timestamp: string,
): Promise<void> {
  const db = await getDatabase();
  const now = new Date().toISOString();

  await db.runAsync(
    `INSERT INTO stamping_records (
       id,
       student_id,
       event_id,
       stamp_date,
       ${column},
       sync_status,
       created_at,
       updated_at
     )
     VALUES (?, ?, ?, ?, ?, 'PENDING', ?, ?)
     ON CONFLICT(student_id, event_id, stamp_date)
     DO UPDATE SET
       ${column} = excluded.${column},
       sync_status = 'PENDING',
       updated_at = excluded.updated_at
     WHERE stamping_records.${column} IS NULL`,
    randomUUID(),
    studentId,
    eventId,
    stampDate,
    timestamp,
    now,
    now,
  );
}

export async function getPendingRecords(): Promise<StampingRecord[]> {
  const db = await getDatabase();

  return db.getAllAsync<StampingRecord>(
    `SELECT *
     FROM stamping_records
     WHERE sync_status = 'PENDING'
     ORDER BY created_at`,
  );
}

export async function markStampingRecordSynced(id: string): Promise<void> {
  const db = await getDatabase();

  await db.runAsync(
    `UPDATE stamping_records
     SET sync_status = 'SYNCED',
         updated_at = ?
     WHERE id = ?`,
    new Date().toISOString(),
    id,
  );
}

export async function deleteStampingRecord(id: string): Promise<void> {
  const db = await getDatabase();

  await db.runAsync(
    `DELETE FROM stamping_records WHERE id = ?`,
    id,
  );
}