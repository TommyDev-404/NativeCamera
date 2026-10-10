
import NetInfo from '@react-native-community/netinfo';
import {
  getPendingRecords,
  markStampingRecordSynced,
} from '../local_db/repositories/stampingRepository';
import { API_URL } from '../lib/apiURL';

let syncInProgress = false;

export async function syncStampingRecords(): Promise<void> {
  if (syncInProgress) {
    console.log('[Stamping Sync] Already in progress. Skipping.');
    return;
  }

  syncInProgress = true;

  console.log('[Stamping Sync] Starting sync...');

  try {
    console.log('[Stamping Sync] Checking internet connection...');

    const network = await NetInfo.fetch();

    if (!network.isConnected || network.isInternetReachable === false) {
      console.log('[Stamping Sync] No internet connection. Sync postponed.');
      return;
    }

    console.log('[Stamping Sync] Internet connection available.');
    console.log('[Stamping Sync] Fetching pending stamping records...');

    const records = await getPendingRecords();

    if (records.length === 0) {
      console.log('[Stamping Sync] No pending records to sync.');
      return;
    }

    console.log(
      `[Stamping Sync] Found ${records.length} pending record(s).`,
    );

    console.log('[Stamping Sync] Uploading records to the server...');

    const response = await fetch(`${API_URL}/sync/stamping-records`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(records),
    });

    if (!response.ok) {
      throw new Error(`Sync failed with HTTP ${response.status}`);
    }

    console.log('[Stamping Sync] Server response received.');

    const result = await response.json();

    if (result.success !== true || !Array.isArray(result.synced_records)) {
      throw new Error('Invalid sync confirmation from server.');
    }

    console.log(
      `[Stamping Sync] Server confirmed ${result.synced_records.length} record(s).`,
    );

    let markedSynced = 0;

    for (const synced of result.synced_records) {
      const record = records.find(
        item =>
          item.student_id === synced.student_id &&
          item.event_id === synced.event_id &&
          item.stamp_date === synced.stamp_date,
      );

      if (!record) {
        console.warn(
          '[Stamping Sync] Could not match confirmed record:',
          synced,
        );
        continue;
      }

      await markStampingRecordSynced(
        record.id,
        record.updated_at,
      );

      markedSynced++;

      console.log(
        `[Stamping Sync] Record marked SYNCED: student=${record.student_id}, event=${record.event_id}, date=${record.stamp_date}`,
      );
    }

    console.log(
      `[Stamping Sync] Sync completed. ${markedSynced}/${records.length} record(s) marked SYNCED.`,
    );
  } catch (error) {
    console.error('[Stamping Sync] Sync failed:', error);
  } finally {
    syncInProgress = false;
    console.log('[Stamping Sync] Sync process ended.');
  }
}
