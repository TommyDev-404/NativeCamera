const API_URL = 'http://10.231.147.22:3000';

export type SyncStudent = {
  student_id: string;
  face_id: number;
  full_name: string;
  year: number;
  section: string;
  course: string;
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
  student_id: string;
  face_id: number;
  embedding: number[];
};

export type SyncResponse = {
  students: SyncStudent[];
  events: SyncEvent[];
  face_embeddings: SyncFaceEmbedding[];
  synced_at: string;
};

export async function fetchSyncData(lastSyncedAt: string | null): Promise<SyncResponse> {
  const url = new URL(`${API_URL}/sync`);

  if (lastSyncedAt) {
    url.searchParams.set('last_synced_at', lastSyncedAt);
  }

  const response = await fetch(url.toString());

  if (!response.ok) {
    throw new Error(`Sync failed: ${response.status}`);
  }

  const data: SyncResponse = await response.json();

  console.log('Sync response:', data);

  return data;
}