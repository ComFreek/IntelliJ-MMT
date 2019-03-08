package info.kwarc.mmt.intellij.ui

import java.awt.event.{ActionEvent, ActionListener}

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import info.kwarc.mmt.api.frontend.ReportHandler
import info.kwarc.mmt.intellij.mmtcontext.{ControllerSingleton, MMTJarContextProvider}
import javax.swing.JPanel

class ShellViewerWrapper(project: Project) extends MMTToolWindow {
  private val shellInContext = MMTJarContextProvider.getProxyFor(
    project,
    this,
    classOf[ShellViewerInterface],
    classOf[ShellViewer]
  )

  def getPanel: JPanel = shellInContext.getPanel
  val displayName: String = "Shell"

  override def init(tw: ToolWindow): Unit = {
    super.init(tw)
    shellInContext.init(tw)
  }
}

trait ShellViewerInterface {
  def init(tw: ToolWindow): Unit

  def getPanel: JPanel

  def actionPerformed(e: ActionEvent): Unit
}

class ShellViewer extends ShellViewerInterface with ActionListener {
  private val shell = new ShellForm

  shell.input.addActionListener(this)
  shell.btn_run.addActionListener(this)

  private def doLine(str: String): Unit = {
    shell.output.append(str + "\n")
  }

  override def getPanel: JPanel = shell.panel

  override def init(tw: ToolWindow): Unit = {
    ControllerSingleton.getSingletonController().report.addHandler(new ReportHandler("IntelliJ Shell") {
      override def apply(ind: Int, caller: => String, group: String, msgParts: List[String]): Unit = {
        msgParts.foreach { msg =>
          doLine(indentString(ind) + group + ": " + msg)
        }
      }
    })

    doLine("This is the MMT Shell")
  }

  override def actionPerformed(e: ActionEvent): Unit = {
    val command = shell.input.getText
    shell.input.setText("")
    ControllerSingleton.getSingletonController().handleLine(command)
  }
}

