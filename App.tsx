
import { requireNativeView } from 'expo';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import {
  Alert,
  PermissionsAndroid,
  Pressable,
  StyleSheet,
  Text,
  View,
  type ViewProps,
} from 'react-native';
import FaceRecognitionModule from '../NativeCamera/modules/face-recognition/src/FaceRecognitionModule';
import { initializeDatabase } from './local_db/migrations';
import {
  getActiveEvents,
  insertEvents,
  type Event,
} from './local_db/repositories/eventRepository';
import {
  fetchSyncEvents,
  fetchSyncScannerData,
} from './api/syncApi';
import {
  getLastSyncedAt,
  updateLastSyncedAt,
} from './local_db/repositories/syncMetadataRepository';
import { saveStamp, type StampColumn } from './local_db/repositories/stampingRepository';
import { API_URL } from './lib/apiURL';
import { useStampingSync } from './hook/useStampingSync';

type EmbeddingEvent = {
  nativeEvent: {
    embedding: number[];
  };
};

type StudentRecognizedEvent = {
  nativeEvent: {
    studentId: string;
  };
};

type FaceRegistrationViewProps = ViewProps & {
  onEmbedding?: (event: EmbeddingEvent) => void;
};

type FaceRecognitionViewProps = ViewProps & {
  onStudentRecognized?: (event: StudentRecognizedEvent) => void;
};

type RegisterStudentData = {
  id: string;
  password: string;
  first_name: string;
  last_name: string;
  email: string;
  section_id: number;
  embedding: number[];
};

type RegisterStudentResponse = {
  message: string;
  student: {
    id: string;
    first_name: string;
    last_name: string;
    email: string;
    section_id: number;
  };
};

type StampType =
  | 'morning_in'
  | 'morning_out'
  | 'afternoon_in'
  | 'afternoon_out'
  | 'morning_out_afternoon_in';

type ActiveEventStatus = 'loading' | 'active' | 'none' | 'multiple' | 'error';

const STAMPING_MODES: { label: string; value: StampType }[] = [
  { label: 'Morning In', value: 'morning_in' },
  { label: 'Morning Out', value: 'morning_out' },
  { label: 'Afternoon In', value: 'afternoon_in' },
  { label: 'Afternoon Out', value: 'afternoon_out' },
  {
    label: 'Morning Out + Afternoon In',
    value: 'morning_out_afternoon_in',
  },
];

const NativeFaceRecognitionView = requireNativeView<FaceRecognitionViewProps>('FaceRecognition');
const NativeFaceRegistrationView = requireNativeView<FaceRegistrationViewProps>('FaceRegistration');

const accountData = {
  id: '202425-1469',
  password: '123456789',
  first_name: 'Rustom',
  last_name: 'Galicia',
  email: 'galiciarustom14@gmail.com',
  section_id: 19,
};

async function registerStudent(data: RegisterStudentData): Promise<RegisterStudentResponse> {
  const response = await fetch(`${API_URL}/auth/create-student-account`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(data),
  });

  const result = await response.json();

  if (!response.ok) {
    throw new Error(
      Array.isArray(result?.message)
        ? result.message.join(', ')
        : result?.message || 'Registration failed',
    );
  }

  return result;
}

function getPhilippineDate(): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Manila',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date());
}

function getPhilippineDateTime() {
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Asia/Manila',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(new Date());

  const values = Object.fromEntries(
    parts.map(({ type, value }) => [type, value]),
  );

  return {
    date: `${values.year}-${values.month}-${values.day}`,
    time: `${values.hour}:${values.minute}`,
  };
}

function formatEventDate(value: string): string {
  return value.includes('T') ? value.slice(0, 10) : value;
}

function formatEventTime(value: string): string {
  if (value.includes('T')) {
    return value.slice(11, 16);
  }

  return value.slice(0, 5);
}

export default function App() {
  const [hasCameraPermission, setHasCameraPermission] = useState<boolean | null>(null);
  const [mode, setMode] = useState<'recognition' | 'registration'>('registration');
  const [status, setStatus] = useState<'idle' | 'registering-face' | 'complete'>('idle');
  const [ready, setReady] = useState(false);
  const [selectedStampType, setSelectedStampType] = useState<StampType>('morning_in');
  const [activeEvent, setActiveEvent] = useState<Event | null>(null);
  const [activeEventStatus, setActiveEventStatus] = useState<ActiveEventStatus>('loading');
  
  // sync pending records
  useStampingSync();

  useEffect(() => {
    const requestCameraPermission = async () => {
      try {
        const alreadyGranted = await PermissionsAndroid.check(
          PermissionsAndroid.PERMISSIONS.CAMERA,
        );

        if (alreadyGranted) {
          setHasCameraPermission(true);
          return;
        }

        const result = await PermissionsAndroid.request(
          PermissionsAndroid.PERMISSIONS.CAMERA,
          {
            title: 'Camera Permission',
            message: 'Camera access is required to use the camera preview.',
            buttonPositive: 'Allow',
            buttonNegative: 'Deny',
          },
        );

        if (result === PermissionsAndroid.RESULTS.GRANTED) {
          setHasCameraPermission(true);
          return;
        }

        setHasCameraPermission(false);
        Alert.alert(
          'Camera permission required',
          'Please allow camera access to use the preview.',
        );
      } catch {
        setHasCameraPermission(false);
        Alert.alert(
          'Permission error',
          'Unable to access the camera permission.',
        );
      }
    };

    requestCameraPermission();
  }, []);

  useEffect(() => {
    let cancelled = false;

    const initializeApp = async () => {
      try {
        const nativeDbInitialized =
          FaceRecognitionModule.initializeDatabase();

        if (!nativeDbInitialized) {
          throw new Error('Failed to initialize native scanner database');
        }

        await initializeDatabase();

        const lastEventSync = await getLastSyncedAt('events');
        const eventsData = await fetchSyncEvents(lastEventSync);

        await insertEvents(eventsData.events);
        await updateLastSyncedAt('events', eventsData.synced_at);

        const lastScannerSync = FaceRecognitionModule.getLastSyncAt();
        const scannerData = await fetchSyncScannerData(lastScannerSync);

        const synced = FaceRecognitionModule.syncDatabase(
          JSON.stringify(scannerData),
        );

        if (!synced) {
          throw new Error('Failed to sync native scanner data');
        }

        console.log('Native sync successful');

        const { date, time } = getPhilippineDateTime();
        const activeEvents = await getActiveEvents(date, time);

        if (cancelled) {
          return;
        }

        if (activeEvents.length === 1) {
          setActiveEvent(activeEvents[0]);
          setActiveEventStatus('active');
          console.log('Active event:', activeEvents[0].name);
        } else if (activeEvents.length > 1) {
          setActiveEvent(null);
          setActiveEventStatus('multiple');
          console.log('Multiple active events. SSG must select an event.');
        } else {
          setActiveEvent(null);
          setActiveEventStatus('none');
          console.log('No active event at this time.');
        }
      } catch (error) {
        console.error('App initialization failed:', error);

        if (!cancelled) {
          setActiveEvent(null);
          setActiveEventStatus('error');

          Alert.alert(
            'Initialization Warning',
            'Some local data may not be up to date. Check the connection and try again.',
          );
        }
      } finally {
        if (!cancelled) {
          setReady(true);
        }
      }
    };

    initializeApp();

    return () => {
      cancelled = true;
    };
  }, []);

  const handleEmbedding = async (event: EmbeddingEvent) => {
    const embedding = event.nativeEvent.embedding;

    if (embedding.length === 0) {
      Alert.alert(
        'Face Registration Failed',
        'No face embedding was generated.',
      );
      return;
    }

    try {
      setStatus('registering-face');

      const registrationData: RegisterStudentData = {
        ...accountData,
        embedding,
      };

      const result = await registerStudent(registrationData);

      setStatus('complete');

      Alert.alert(
        'Registration Complete',
        `Student ${result.student.id} was registered successfully.`,
      );
    } catch (error) {
      console.error('Registration error:', error);

      setStatus('idle');

      Alert.alert(
        'Registration Failed',
        error instanceof Error
          ? error.message
          : 'Unable to register student.',
      );
    }
  };

  const handleStudentRecognized = async (event: StudentRecognizedEvent,) => {
    if (!activeEvent) {
      Alert.alert(
        'No Active Event',
        'Face recognition is unavailable because there is no active event.',
      );
      return;
    }

    const { studentId } = event.nativeEvent;
    const stampDate = getPhilippineDate();
    const timestamp = new Date().toISOString();

    const stampColumns: Record<StampType, StampColumn[]> = {
      morning_in: ['morning_in'],
      morning_out: ['morning_out'],
      afternoon_in: ['afternoon_in'],
      afternoon_out: ['afternoon_out'],
      morning_out_afternoon_in: ['morning_out', 'afternoon_in'],
    };

    try {
      const columns = stampColumns[selectedStampType];

      for (const column of columns) {
        const result = await saveStamp(
          studentId,
          activeEvent.id,
          stampDate,
          column,
          timestamp,
        );

        if (result.status === 'ALREADY_STAMPED') {
          const columnLabels: Record<StampColumn, string> = {
            morning_in: 'Morning In',
            morning_out: 'Morning Out',
            afternoon_in: 'Afternoon In',
            afternoon_out: 'Afternoon Out',
          };

          Alert.alert(
            'Already Stamped',
            `${studentId} has already stamped for ${columnLabels[column]}.`,
          );
          return;
        }
      }

      console.log('Stamp saved locally:', {
        studentId,
        eventId: activeEvent.id,
        eventName: activeEvent.name,
        stampDate,
        timestamp,
        stampType: selectedStampType,
      });

      Alert.alert(
        'Stamp Recorded',
        `${studentId} was recorded for ${activeEvent.name}.\nStamp: ${
          STAMPING_MODES.find(
            (item) => item.value === selectedStampType,
          )?.label ?? selectedStampType
        }`,
      );
    } catch (error) {
      console.error('Failed to save stamping record:', error);

      Alert.alert(
        'Stamp Failed',
        'The stamping record could not be saved locally. Please try again.',
      );
    }
  };
    
  const getSubtitle = () => {
    if (!ready) {
      return 'Initializing scanner...';
    }

    if (status === 'registering-face') {
      return 'Registering student and face...';
    }

    if (status === 'complete') {
      return 'Registration completed successfully.';
    }

    return mode === 'recognition'
      ? 'Select a stamping mode before scanning.'
      : 'Registration camera';
  };

  const renderActiveEvent = () => {
    if (activeEventStatus === 'loading') {
      return (
        <View style={styles.eventCard}>
          <Text style={styles.eventEyebrow}>ACTIVE EVENT</Text>
          <Text style={styles.eventMessage}>
            Checking event schedule...
          </Text>
        </View>
      );
    }

    if (activeEventStatus === 'active' && activeEvent) {
      return (
        <View style={[styles.eventCard, styles.eventCardActive]}>
          <View style={styles.eventCardHeader}>
            <View style={styles.eventIcon}>
              <Text style={styles.eventIconText}>E</Text>
            </View>

            <View style={styles.eventHeading}>
              <Text style={styles.eventEyebrow}>ACTIVE EVENT</Text>
              <Text style={styles.eventName}>{activeEvent.name}</Text>
            </View>

            <View style={styles.activeBadge}>
              <View style={styles.activeDot} />
              <Text style={styles.activeBadgeText}>Active</Text>
            </View>
          </View>

          <View style={styles.eventDetails}>
            <View style={styles.eventDetail}>
              <Text style={styles.detailLabel}>DATE</Text>
              <Text style={styles.detailValue}>
                {formatEventDate(activeEvent.start_date)}
                {activeEvent.start_date !== activeEvent.end_date &&
                  ` – ${formatEventDate(activeEvent.end_date)}`}
              </Text>
            </View>

            <View style={styles.eventDetail}>
              <Text style={styles.detailLabel}>SCHEDULE</Text>
              <Text style={styles.detailValue}>
                {formatEventTime(activeEvent.start_time)} –{' '}
                {formatEventTime(activeEvent.end_time)}
              </Text>
            </View>
          </View>
        </View>
      );
    }

    if (activeEventStatus === 'multiple') {
      return (
        <View style={[styles.eventCard, styles.eventCardWarning]}>
          <Text style={styles.eventEyebrow}>ACTIVE EVENT</Text>
          <Text style={styles.eventMessage}>
            Multiple events are active
          </Text>
          <Text style={styles.eventDescription}>
            Event selection is required before face recognition can begin.
          </Text>
        </View>
      );
    }

    if (activeEventStatus === 'error') {
      return (
        <View style={[styles.eventCard, styles.eventCardWarning]}>
          <Text style={styles.eventEyebrow}>ACTIVE EVENT</Text>
          <Text style={styles.eventMessage}>
            Unable to verify the event
          </Text>
          <Text style={styles.eventDescription}>
            Check your connection and restart initialization before scanning.
          </Text>
        </View>
      );
    }

    return (
      <View style={[styles.eventCard, styles.eventCardWarning]}>
        <Text style={styles.eventEyebrow}>ACTIVE EVENT</Text>
        <Text style={styles.eventMessage}>No active event</Text>
        <Text style={styles.eventDescription}>
          The scanner will be available when an event is within its scheduled
          date and time.
        </Text>
      </View>
    );
  };

  return (
    <View style={styles.screen}>
      <StatusBar style="light" />

      <View style={styles.header}>
        <Text style={styles.title}>
          {mode === 'recognition' ? 'Face Recognition' : 'Face Registration'}
        </Text>
        <Text style={styles.subtitle}>{getSubtitle()}</Text>
      </View>

      <View style={styles.modeSwitcher}>
        <Pressable
          style={[
            styles.modeButton,
            mode === 'recognition' && styles.modeButtonActive,
          ]}
          onPress={() => setMode('recognition')}
        >
          <Text
            style={[
              styles.modeText,
              mode === 'recognition' && styles.modeTextActive,
            ]}
          >
            Recognition
          </Text>
        </Pressable>

        <Pressable
          style={[
            styles.modeButton,
            mode === 'registration' && styles.modeButtonActive,
          ]}
          onPress={() => setMode('registration')}
        >
          <Text
            style={[
              styles.modeText,
              mode === 'registration' && styles.modeTextActive,
            ]}
          >
            Registration
          </Text>
        </Pressable>
      </View>

      {mode === 'recognition' && (
        <>
          {renderActiveEvent()}

          <View style={styles.stampingSection}>
            <Text style={styles.stampingTitle}>Stamping Mode</Text>

            <View style={styles.stampingGrid}>
              {STAMPING_MODES.map((item) => {
                const isSelected = selectedStampType === item.value;

                return (
                  <Pressable
                    key={item.value}
                    accessibilityRole="radio"
                    accessibilityState={{ selected: isSelected }}
                    style={[
                      styles.stampingButton,
                      isSelected && styles.stampingButtonActive,
                      item.value === 'morning_out_afternoon_in' &&
                        styles.combinedStampButton,
                    ]}
                    onPress={() => setSelectedStampType(item.value)}
                  >
                    <Text
                      style={[
                        styles.stampingText,
                        isSelected && styles.stampingTextActive,
                      ]}
                    >
                      {item.label}
                    </Text>
                  </Pressable>
                );
              })}
            </View>
          </View>
        </>
      )}

      <View style={styles.previewShell}>
        {!ready ? (
          <View style={styles.permissionHolder}>
            <Text style={styles.permissionTitle}>Initializing</Text>
            <Text style={styles.permissionText}>
              Preparing the local database and face recognition data...
            </Text>
          </View>
        ) : hasCameraPermission !== true ? (
          <View style={styles.permissionHolder}>
            <Text style={styles.permissionTitle}>Camera access</Text>
            <Text style={styles.permissionText}>
              {hasCameraPermission === null
                ? 'Requesting permission...'
                : 'Permission was not granted. Enable camera access in your device settings.'}
            </Text>
          </View>
        ) : mode === 'recognition' && activeEventStatus !== 'active' ? (
          <View style={styles.permissionHolder}>
            <View style={styles.cameraBlockedIcon}>
              <Text style={styles.cameraBlockedIconText}>!</Text>
            </View>
            <Text style={styles.permissionTitle}>Scanner unavailable</Text>
            <Text style={styles.permissionText}>
              {activeEventStatus === 'multiple'
                ? 'Select one event before starting face recognition.'
                : activeEventStatus === 'error'
                  ? 'The event schedule could not be verified. Please retry initialization.'
                  : activeEventStatus === 'loading'
                    ? 'Checking for an active event...'
                    : 'There is no active event right now. The camera will remain closed until an event is active.'}
            </Text>
          </View>
        ) : mode === 'recognition' ? (
          <NativeFaceRecognitionView
            key="recognition"
            style={styles.preview}
            onStudentRecognized={handleStudentRecognized}
          />
        ) : (
          <NativeFaceRegistrationView
            key="registration"
            style={styles.preview}
            onEmbedding={handleEmbedding}
          />
        )}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: '#09090b',
    paddingHorizontal: 20,
    paddingTop: 56,
    paddingBottom: 28,
  },
  header: {
    marginBottom: 18,
  },
  title: {
    color: '#f4f4f5',
    fontSize: 28,
    fontWeight: '700',
    letterSpacing: 0.3,
  },
  subtitle: {
    marginTop: 8,
    color: '#a1a1aa',
    fontSize: 14,
    letterSpacing: 0.2,
  },
  modeSwitcher: {
    flexDirection: 'row',
    backgroundColor: '#18181b',
    borderRadius: 14,
    padding: 4,
    marginBottom: 14,
    borderWidth: 1,
    borderColor: '#27272a',
  },
  modeButton: {
    flex: 1,
    height: 42,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
  },
  modeButtonActive: {
    backgroundColor: '#f4f4f5',
  },
  modeText: {
    color: '#a1a1aa',
    fontSize: 14,
    fontWeight: '600',
  },
  modeTextActive: {
    color: '#18181b',
  },
  eventCard: {
    backgroundColor: '#18181b',
    borderWidth: 1,
    borderColor: '#27272a',
    borderRadius: 16,
    padding: 15,
    marginBottom: 14,
  },
  eventCardActive: {
    borderColor: '#3f3f46',
  },
  eventCardWarning: {
    borderColor: '#713f12',
    backgroundColor: '#1c1917',
  },
  eventCardHeader: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  eventIcon: {
    width: 42,
    height: 42,
    borderRadius: 12,
    backgroundColor: '#27272a',
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 12,
  },
  eventIconText: {
    color: '#c4b5fd',
    fontSize: 19,
    fontWeight: '800',
  },
  eventHeading: {
    flex: 1,
    minWidth: 0,
  },
  eventEyebrow: {
    color: '#a1a1aa',
    fontSize: 10,
    fontWeight: '700',
    letterSpacing: 1.2,
    marginBottom: 5,
  },
  eventName: {
    color: '#f4f4f5',
    fontSize: 16,
    fontWeight: '700',
  },
  activeBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#14532d',
    borderRadius: 20,
    paddingHorizontal: 9,
    paddingVertical: 5,
    marginLeft: 8,
  },
  activeDot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    backgroundColor: '#4ade80',
    marginRight: 6,
  },
  activeBadgeText: {
    color: '#bbf7d0',
    fontSize: 11,
    fontWeight: '700',
  },
  eventDetails: {
    flexDirection: 'row',
    borderTopWidth: 1,
    borderTopColor: '#27272a',
    marginTop: 14,
    paddingTop: 12,
    gap: 16,
  },
  eventDetail: {
    flex: 1,
  },
  detailLabel: {
    color: '#71717a',
    fontSize: 10,
    fontWeight: '700',
    letterSpacing: 0.8,
    marginBottom: 5,
  },
  detailValue: {
    color: '#e4e4e7',
    fontSize: 12,
    fontWeight: '600',
  },
  eventMessage: {
    color: '#f4f4f5',
    fontSize: 15,
    fontWeight: '700',
    marginTop: 2,
  },
  eventDescription: {
    color: '#a1a1aa',
    fontSize: 12,
    lineHeight: 18,
    marginTop: 6,
  },
  stampingSection: {
    marginBottom: 14,
  },
  stampingTitle: {
    color: '#f4f4f5',
    fontSize: 14,
    fontWeight: '600',
    marginBottom: 10,
  },
  stampingGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  stampingButton: {
    width: '48%',
    minHeight: 42,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: '#3f3f46',
    backgroundColor: '#18181b',
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 8,
    paddingVertical: 8,
  },
  combinedStampButton: {
    width: '100%',
  },
  stampingButtonActive: {
    backgroundColor: '#7c3aed',
    borderColor: '#8b5cf6',
  },
  stampingText: {
    color: '#d4d4d8',
    fontSize: 13,
    fontWeight: '600',
    textAlign: 'center',
  },
  stampingTextActive: {
    color: '#ffffff',
  },
  previewShell: {
    flex: 1,
    minHeight: 180,
    borderRadius: 28,
    overflow: 'hidden',
    backgroundColor: '#111827',
    borderWidth: 1,
    borderColor: '#27272a',
    justifyContent: 'center',
    alignItems: 'center',
  },
  preview: {
    width: '100%',
    height: '100%',
    backgroundColor: '#000000',
  },
  permissionHolder: {
    flex: 1,
    width: '100%',
    backgroundColor: '#111827',
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 24,
  },
  permissionTitle: {
    color: '#f4f4f5',
    fontSize: 22,
    fontWeight: '700',
    marginBottom: 8,
    textAlign: 'center',
  },
  permissionText: {
    color: '#d4d4d8',
    fontSize: 14,
    textAlign: 'center',
    lineHeight: 21,
  },
  cameraBlockedIcon: {
    width: 48,
    height: 48,
    borderRadius: 24,
    backgroundColor: '#27272a',
    borderWidth: 1,
    borderColor: '#52525b',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 16,
  },
  cameraBlockedIconText: {
    color: '#d4d4d8',
    fontSize: 24,
    fontWeight: '700',
  },
});