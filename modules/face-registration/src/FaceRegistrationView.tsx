import { requireNativeView } from 'expo';
import type { FaceRegistrationViewProps } from './FaceRegistration.types';

const NativeView = requireNativeView<FaceRegistrationViewProps>('FaceRegistration');

export default function FaceRegistrationView(
  props: FaceRegistrationViewProps
) {
  return <NativeView {...props} />;
}

