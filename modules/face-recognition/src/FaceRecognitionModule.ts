import { NativeModule, requireNativeModule } from 'expo';

import { FaceRecognitionModuleEvents } from './FaceRecognition.types';

declare class FaceRecognitionModule extends NativeModule<FaceRecognitionModuleEvents> {
  initializeDatabase(): boolean;
  syncDatabase(data: string): boolean;
  getLastSyncAt(): string;
}

export default requireNativeModule<FaceRecognitionModule>('FaceRecognition');
