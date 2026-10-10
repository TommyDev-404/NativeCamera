import { API_URL } from "../lib/apiURL";

export type SyncStudent = {
  student_id: string;
  face_id: number | null;
  full_name: string;
  year: number | null;
  section: string | null;
  course: string | null;
};

export type SyncEvent = {
  id: number;
  name: string;
  start_date: string;
  end_date: string;
  start_time: string;
  end_time: string;
  morning_in: string | null;
  morning_out: string | null;
  afternoon_in: string | null;
  afternoon_out: string | null;
};

export type SyncFaceEmbedding = {
  id: number;
  embedding: number[];
};

export type SyncEventsResponse = {
  events: SyncEvent[];
  synced_at: string;
};

export type SyncScannerResponse = {
  students: SyncStudent[];
  face_embeddings: SyncFaceEmbedding[];
  synced_at: string;
};

async function fetchSyncEndpoint<T>(
  endpoint: string,
  lastSyncedAt?: string | null,
): Promise<T> {
  const url = new URL(`${API_URL}/sync/${endpoint}`);

  if (lastSyncedAt) {
    url.searchParams.set('last_synced_at', lastSyncedAt);
  }

  const response = await fetch(url.toString());

  if (!response.ok) {
    throw new Error(
      `Sync failed for ${endpoint}: ${response.status}`,
    );
  }

  return response.json() as Promise<T>;
}

export function fetchSyncEvents(
  lastSyncedAt: string | null,
): Promise<SyncEventsResponse> {
  return fetchSyncEndpoint<SyncEventsResponse>(
    'events',
    lastSyncedAt,
  );
}

export function fetchSyncScannerData(
  lastSyncedAt: string | null,
): Promise<SyncScannerResponse> {
  return fetchSyncEndpoint<SyncScannerResponse>(
    'scanner-data',
    lastSyncedAt,
  );
}