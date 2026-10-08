import { NativeModule, requireNativeModule } from 'expo';

declare class FaceRegistrationModule extends NativeModule {}

export default requireNativeModule<FaceRegistrationModule>('FaceRegistration');
