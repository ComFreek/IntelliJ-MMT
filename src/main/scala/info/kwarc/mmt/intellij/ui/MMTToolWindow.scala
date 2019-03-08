package info.kwarc.mmt.intellij.ui

import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.content.ContentFactory
import javax.swing.JPanel
import info.kwarc.mmt.utils._

trait MMTToolWindow {
  def getPanel : JPanel
  val displayName : String
  def init(tw : ToolWindow) : Unit = {
    val content = ContentFactory.SERVICE.getInstance().createContent(getPanel,displayName,false)
    // background {
      getPanel.setVisible(true)
      tw.getContentManager.addContent(content)
    // }
  }
}
