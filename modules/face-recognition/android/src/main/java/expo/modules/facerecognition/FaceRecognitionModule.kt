package expo.modules.facerecognition

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class FaceRecognitionModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("FaceRecognition")

    View(FaceRecognitionView::class) {
      // Defines an event that the view can send to JavaScript.
      //Events("onTap")
    }
  }
}
