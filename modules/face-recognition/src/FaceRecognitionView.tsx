import { requireNativeView } from 'expo';
import * as React from 'react';

import { FaceRecognitionViewProps } from './FaceRecognition.types';

const NativeView: React.ComponentType<FaceRecognitionViewProps> = requireNativeView('FaceRecognition');

export default function FaceRecognitionView(props: FaceRecognitionViewProps) {
  return <NativeView {...props} />;
}
