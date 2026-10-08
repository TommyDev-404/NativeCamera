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
import { fetchSyncData } from './api/syncApi';
import FaceRecognitionModule from '../NativeCamera/modules/face-recognition/src/FaceRecognitionModule';
import { initializeDatabase } from './local_db/migrations';
import { insertEvents } from './local_db/repositories/eventRepository';

type EmbeddingEvent = {
  nativeEvent: {
    embedding: number[];
  };
};

type FaceRegistrationViewProps = ViewProps & {
  onEmbedding?: (event: EmbeddingEvent) => void;
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

const API_URL = 'http://10.231.147.22:3000';

const NativeFaceRecognitionView =
  requireNativeView<ViewProps>('FaceRecognition');

const NativeFaceRegistrationView =
  requireNativeView<FaceRegistrationViewProps>('FaceRegistration');
  

const accountData = {
  id: '202425-1469',
  password: '123456789',
  first_name: 'Rustom',
  last_name: 'Galicia',
  email: 'galiciarustom14@gmail.com',
  section_id: 142,
};

async function registerStudent(
  data: RegisterStudentData,
): Promise<RegisterStudentResponse> {
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

export default function App() {
  const [hasCameraPermission, setHasCameraPermission] = useState<boolean | null>(null);
  const [mode, setMode] = useState<'recognition' | 'registration'>('registration',);
  const [status, setStatus] = useState<'idle' | 'registering-face' | 'complete'>('idle');
  
  const [ready, setReady] = useState(false);

	useEffect(() => {
		const requestCameraPermission = async () => {
			try {
				const alreadyGranted = await PermissionsAndroid.check(PermissionsAndroid.PERMISSIONS.CAMERA);

				if (alreadyGranted) {
					setHasCameraPermission(true);
					return;
				}

				const result = await PermissionsAndroid.request(
					PermissionsAndroid.PERMISSIONS.CAMERA,
					{
						title: 'Camera Permission',
						message:
						'Camera access is required to use the camera preview.',
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
    const initializeApp = async () => {
      try {

        // INITIALIZE NATIVE DB
        const nativeDbInitialized = FaceRecognitionModule.initializeDatabase();
  
        if (!nativeDbInitialized) {
          throw new Error(
            'Failed to initialize native scanner database'
          );
        }

        // INITIALIZE EXPO DB
        await initializeDatabase();

        const lastSyncedAt = FaceRecognitionModule.getLastSyncAt();

        const syncData = await fetchSyncData(lastSyncedAt);
      
        // SYNC INTO EXPO SQLITE
        await insertEvents(syncData.events);

        // SYNC INTO NATIVE MODULE
        const nativeSyncData = {
          students: syncData.students,
          face_embeddings: syncData.face_embeddings,
          synced_at: syncData.synced_at,
        };
        
        const synced = FaceRecognitionModule.syncDatabase(JSON.stringify(nativeSyncData));
  
        if (!synced) {
          throw new Error(
            'Failed to sync data to native module'
          );
        }

        console.log('Native sync test successful');

      } catch (error) {
        console.error('App initialization failed:', error);
      } finally {
        setReady(true);
      }
    };
  
    initializeApp();
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

  const getSubtitle = () => {
    if (status === 'registering-face') {
      return 'Registering student and face...';
    }

    if (status === 'complete') {
      return 'Registration completed successfully.';
    }

    return mode === 'recognition'
      ? 'Recognition camera'
      : 'Registration camera';
  };

  return (
    <View style={styles.screen}>
      <StatusBar style="light" />

      <View style={styles.header}>
        <Text style={styles.title}>
          {mode === 'recognition'
            ? 'Face Recognition'
            : 'Face Registration'}
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

      <View style={styles.previewShell}>
        {hasCameraPermission ? (
          mode === 'recognition' ? (
            <NativeFaceRecognitionView
              key="recognition"
              style={styles.preview}
            />
          ) : (
            <NativeFaceRegistrationView
              key="registration"
              style={styles.preview}
              onEmbedding={handleEmbedding}
            />
          )
        ) : (
          <View style={styles.permissionHolder}>
            <Text style={styles.permissionTitle}>
              Camera access
            </Text>

            <Text style={styles.permissionText}>
              {hasCameraPermission === null
                ? 'Requesting permission...'
                : 'Permission was not granted.'}
            </Text>
          </View>
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

  previewShell: {
    flex: 1,
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
    backgroundColor: '#000',
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
  },

  permissionText: {
    color: '#d4d4d8',
    fontSize: 14,
    textAlign: 'center',
  },
});
