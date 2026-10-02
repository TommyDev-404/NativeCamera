import { requireNativeView } from 'expo';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import type { ComponentType } from 'react';
import {
  Alert,
  PermissionsAndroid,
  Pressable,
  StyleSheet,
  Text,
  View,
  type ViewProps,
} from 'react-native';

const cameraConfig = {
  facing: 'back',
  flashMode: 'off',
  quality: 'high',
};

const NativeFaceRecognitionView = requireNativeView<ViewProps>('FaceRecognition');

export default function App() {
  const [hasCameraPermission, setHasCameraPermission] = useState<boolean | null>(
    null,
  );

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
      } catch (error) {
        setHasCameraPermission(false);

        Alert.alert(
          'Permission error',
          'Unable to access the camera permission.',
        );
      }
    };

    requestCameraPermission();
  }, []);

  return (
    <View style={styles.screen}>
      <StatusBar style="light" />

      <View style={styles.header}>
        <Text style={styles.title}>Camera</Text>

        <Text style={styles.subtitle}>
          {cameraConfig.facing === 'back' ? 'Rear camera' : 'Front camera'}
        </Text>
      </View>

      <View style={styles.previewShell}>
        {hasCameraPermission ? (
          <NativeFaceRecognitionView style={styles.preview} />
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

        <View pointerEvents="none" style={styles.overlayFrame}>
          <View style={styles.cornerTopLeft} />
          <View style={styles.cornerTopRight} />
          <View style={styles.cornerBottomLeft} />
          <View style={styles.cornerBottomRight} />
        </View>
      </View>

      <View style={styles.controls}>
        <Pressable style={styles.secondaryButton}>
          <Text style={styles.secondaryText}>Flip</Text>
        </Pressable>

        <Pressable style={styles.captureButton}>
          <View style={styles.captureInner} />
        </Pressable>

        <Pressable style={styles.secondaryButton}>
          <Text style={styles.secondaryText}>Flash</Text>
        </Pressable>
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
  overlayFrame: {
    position: 'absolute',
    width: '78%',
    height: '62%',
    borderRadius: 24,
  },
  cornerTopLeft: {
    position: 'absolute',
    top: 0,
    left: 0,
    width: 28,
    height: 28,
    borderTopWidth: 4,
    borderLeftWidth: 4,
    borderColor: '#f4f4f5',
    borderTopLeftRadius: 10,
  },
  cornerTopRight: {
    position: 'absolute',
    top: 0,
    right: 0,
    width: 28,
    height: 28,
    borderTopWidth: 4,
    borderRightWidth: 4,
    borderColor: '#f4f4f5',
    borderTopRightRadius: 10,
  },
  cornerBottomLeft: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    width: 28,
    height: 28,
    borderBottomWidth: 4,
    borderLeftWidth: 4,
    borderColor: '#f4f4f5',
    borderBottomLeftRadius: 10,
  },
  cornerBottomRight: {
    position: 'absolute',
    bottom: 0,
    right: 0,
    width: 28,
    height: 28,
    borderBottomWidth: 4,
    borderRightWidth: 4,
    borderColor: '#f4f4f5',
    borderBottomRightRadius: 10,
  },
  controls: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginTop: 24,
    paddingHorizontal: 12,
  },
  secondaryButton: {
    width: 72,
    height: 42,
    borderRadius: 16,
    backgroundColor: '#1f2937',
    alignItems: 'center',
    justifyContent: 'center',
  },
  secondaryText: {
    color: '#e5e7eb',
    fontWeight: '600',
  },
  captureButton: {
    width: 78,
    height: 78,
    borderRadius: 39,
    backgroundColor: '#f4f4f5',
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#f4f4f5',
    shadowOpacity: 0.4,
    shadowRadius: 18,
    shadowOffset: { width: 0, height: 0 },
    elevation: 8,
  },
  captureInner: {
    width: 60,
    height: 60,
    borderRadius: 30,
    backgroundColor: '#111827',
    borderWidth: 6,
    borderColor: '#f4f4f5',
  },
});
