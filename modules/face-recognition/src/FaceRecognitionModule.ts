import { NativeModule, requireNativeModule } from 'expo';

import { FaceRecognitionModuleEvents } from './FaceRecognition.types';

declare class FaceRecognitionModule extends NativeModule<FaceRecognitionModuleEvents> {
  //setValueAsync(value: string): Promise<void>;
}

export default requireNativeModule<FaceRecognitionModule>('FaceRecognition');
