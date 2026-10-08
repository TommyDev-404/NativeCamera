import type { StyleProp, ViewStyle } from 'react-native';

export type FaceRegistrationViewProps = {
  onEmbedding: (event: {
    nativeEvent: {
      embedding: number[];
    };
  }) => void;
  style?: StyleProp<ViewStyle>;
};
