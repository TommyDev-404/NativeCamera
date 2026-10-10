
import { useEffect } from 'react';
import { AppState } from 'react-native';
import NetInfo from '@react-native-community/netinfo';
import { syncStampingRecords } from '../services/stampingSync';

export function useStampingSync(): void {
  useEffect(() => {
    void syncStampingRecords();

    const unsubscribeNetwork = NetInfo.addEventListener(state => {
      if (state.isConnected &&state.isInternetReachable !== false) {
        void syncStampingRecords();
      }
    });

    const appStateSubscription = AppState.addEventListener('change',
      nextState => {
        if (nextState === 'active') {
          void syncStampingRecords();
        }
      },
    );

    return () => {
      unsubscribeNetwork();
      appStateSubscription.remove();
    };
  }, []);
}
