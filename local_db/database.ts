import * as SQLite from 'expo-sqlite';

export const dbPromise = SQLite.openDatabaseAsync('estampmo.db');

export async function getDatabase() {
  return dbPromise;
}