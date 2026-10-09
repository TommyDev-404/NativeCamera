import { getDatabase } from "../database";

export type Event = {
  id: number;
  name: string;
  start_date: string;
  end_date: string;
  start_time: string;
  end_time: string;
};


export async function insertEvents(events: Event[]) {
  const db = await getDatabase();

  await db.withTransactionAsync(async () => {
    for (const event of events) {
      await db.runAsync(
        `INSERT INTO events (
          id, name, start_date, end_date, start_time, end_time
        )
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT(id) DO UPDATE SET
          name = excluded.name,
          start_date = excluded.start_date,
          end_date = excluded.end_date,
          start_time = excluded.start_time,
          end_time = excluded.end_time`,
        event.id,
        event.name,
        event.start_date,
        event.end_date,
        event.start_time,
        event.end_time
      );
    }
  });
}

export async function getEventById(
  eventId: number
): Promise<Event | null> {
  const db = await getDatabase();

  return db.getFirstAsync<Event>(
    `
      SELECT *
      FROM events
      WHERE id = ?
    `,
    eventId
  );
}

export async function getAllEvents(): Promise<Event[]> {
  const db = await getDatabase();

  return db.getAllAsync<Event>(
    `
      SELECT *
      FROM events
      ORDER BY start_date, start_time
    `
  );
}

export async function getActiveEvents(
  date: string,
  time: string
): Promise<Event[]> {
  const db = await getDatabase();

  return db.getAllAsync<Event>(
    `
      SELECT *
      FROM events
      WHERE start_date <= ?
        AND end_date >= ?
    `,
    date,
    date
  );
}

/*
?
        AND start_time <= ?
        AND end_time >= ?
*/

export async function deleteAllEvents() {
  const db = await getDatabase();

  await db.runAsync(`
    DELETE FROM events
  `);
}
