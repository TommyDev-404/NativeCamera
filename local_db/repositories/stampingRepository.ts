import { randomUUID } from "expo-crypto";
import { getDatabase } from "../database";

export type StampingRecord = {
  id: string;
  student_id: string;
  event_id: number;
  stamp_date: string;
  morning_in: string | null;
  morning_out: string | null;
  afternoon_in: string | null;
  afternoon_out: string | null;
  created_at: string;
  updated_at: string;
};

export async function getStampingRecord(
  studentId: string,
  eventId: number,
  stampDate: string
): Promise<StampingRecord | null> {
  const db = await getDatabase();

  return db.getFirstAsync<StampingRecord>(
    `
      SELECT *
      FROM stamping_records
      WHERE student_id = ?
        AND event_id = ?
        AND stamp_date = ?
    `,
    studentId,
    eventId,
    stampDate
  );
}

export async function createStampingRecord(
  studentId: string,
  eventId: number,
  stampDate: string
) {
  const db = await getDatabase();
  const now = new Date().toISOString();
  const id = randomUUID();

  await db.runAsync(
    `
      INSERT OR IGNORE INTO stamping_records (
        id,
        student_id,
        event_id,
        stamp_date,
        created_at,
        updated_at
      )
      VALUES (?, ?, ?, ?, ?, ?)
    `,
    id,
    studentId,
    eventId,
    stampDate,
    now,
    now
  );
}

export async function updateMorningIn(
  studentId: string,
  eventId: number,
  stampDate: string,
  timestamp: string
) {
  await updateStamp(
    studentId,
    eventId,
    stampDate,
    "morning_in",
    timestamp
  );
}

export async function updateMorningOut(
  studentId: string,
  eventId: number,
  stampDate: string,
  timestamp: string
) {
  await updateStamp(
    studentId,
    eventId,
    stampDate,
    "morning_out",
    timestamp
  );
}

export async function updateAfternoonIn(
  studentId: string,
  eventId: number,
  stampDate: string,
  timestamp: string
) {
  await updateStamp(
    studentId,
    eventId,
    stampDate,
    "afternoon_in",
    timestamp
  );
}

export async function updateAfternoonOut(
  studentId: string,
  eventId: number,
  stampDate: string,
  timestamp: string
) {
  await updateStamp(
    studentId,
    eventId,
    stampDate,
    "afternoon_out",
    timestamp
  );
}

async function updateStamp(
  studentId: string,
  eventId: number,
  stampDate: string,
  column: "morning_in" | "morning_out" | "afternoon_in" | "afternoon_out",
  timestamp: string
) {
  const db = await getDatabase();
  const now = new Date().toISOString();

  await db.runAsync(
    `
      INSERT INTO stamping_records (
        id,
        student_id,
        event_id,
        stamp_date,
        ${column},
        created_at,
        updated_at
      )
      VALUES (?, ?, ?, ?, ?, ?, ?)

      ON CONFLICT(student_id, event_id, stamp_date)
      DO UPDATE SET
        ${column} = excluded.${column},
        updated_at = excluded.updated_at
    `,
    randomUUID(),
    studentId,
    eventId,
    stampDate,
    timestamp,
    now,
    now
  );
}

export async function getPendingRecords(): Promise<StampingRecord[]> {
  const db = await getDatabase();

  return db.getAllAsync<StampingRecord>(
    `
      SELECT *
      FROM stamping_records
      ORDER BY created_at
    `
  );
}

export async function deleteStampingRecord(id: string) {
  const db = await getDatabase();

  await db.runAsync(
    `
      DELETE FROM stamping_records
      WHERE id = ?
    `,
    id
  );
}
