package info.kwarc.mmt.intellij.mmtcontext

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import com.intellij.openapi.ui.Messages
import info.kwarc.mmt.intellij.ui.Actions.{MMTAction, MMTActionTrait}

trait InMMTContextAction {
  def actionPerformed(event: AnActionEvent): Unit
  def update(event: AnActionEvent): Unit = {
  }
}

class BinderAction(id: String, text: String, inMmtContextActionClass: Class[_], descr: String = "") extends MMTAction(id, text, descr = descr) with MMTActionTrait {
  override def actionPerformed(event: AnActionEvent): Unit = {
    try {
      MMTJarContextProvider.getProxyFor(event.getProject, this, classOf[InMMTContextAction], inMmtContextActionClass).actionPerformed(event)
    }
    catch {
      case _: NotInMMTContextException =>
        Messages.showErrorDialog("Not a MathHub/MMT Project", "MMT")
    }
  }

  override def update(event: AnActionEvent): Unit = {
    MMTJarContextProvider.getProxyFor(event.getProject, this, classOf[InMMTContextAction], inMmtContextActionClass).update(event)
  }
}