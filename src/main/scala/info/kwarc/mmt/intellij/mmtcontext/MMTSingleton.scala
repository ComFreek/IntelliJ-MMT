package info.kwarc.mmt.intellij.mmtcontext

import com.intellij.openapi.project.Project
import info.kwarc.mmt.api.frontend.Controller

object ControllerSingleton {
  private var ctrl: Controller = null

  def getSingletonController(): Controller = {
    if (ctrl == null) {
      ctrl = new Controller()
      ctrl
    }
    else {
      ctrl
    }
  }
}
