package expo.modules.faceregistration

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class FaceRegistrationModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("FaceRegistration")

    View(FaceRegistrationView::class) {
      Events("onEmbedding")
    }
  }
}
