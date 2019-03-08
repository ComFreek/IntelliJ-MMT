package info.kwarc.mmt.intellij.checking

import java.awt.BorderLayout
import java.awt.datatransfer.StringSelection
import java.awt.event._

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.{FileEditorManager, OpenFileDescriptor}
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.{JBMenuItem, Messages}
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import com.intellij.ui.treeStructure.{PatchedDefaultMutableTreeNode, Tree}
import info.kwarc.mmt.api.utils.File
import info.kwarc.mmt.intellij.MMT
import info.kwarc.mmt.intellij.mmtcontext.{ControllerSingleton, MMTJarContextProvider}
import info.kwarc.mmt.intellij.ui.{AbstractErrorViewer, MMTToolWindow}
import info.kwarc.mmt.utils
import javax.swing.tree.{DefaultTreeModel, TreePath}
import javax.swing.{JPanel, JPopupMenu, JScrollPane, SwingUtilities}

class ErrorViewerPanelWrapper(project: Project) extends MMTToolWindow {
  private val errorViewerInContext = MMTJarContextProvider.getProxyFor(
    project,
    this,
    classOf[ErrorViewerPanelInterface],
    classOf[ErrorViewerPanel]
  )

  def getPanel: JPanel = errorViewerInContext.getPanel

  val displayName: String = "Errors"

  def doCheck: Boolean = errorViewerInContext.doCheck

  def clearFile(file: info.kwarc.mmt.utils.File) = {
    errorViewerInContext.clearFile(file.toJava)
  }

  def addError(short: String, long: List[String], psifile: PsiFile,
               file: info.kwarc.mmt.utils.File, sr: TextRange): Unit = {
    errorViewerInContext.addError(short, long, psifile, file.toJava, sr)
  }

  def deselectCheckBtn(): Unit = {
    errorViewerInContext.deselectCheckBtn()
  }
}

trait ErrorViewerPanelInterface extends ActionListener {
  def getPanel: JPanel

  def clearFile(file: java.io.File)

  def addError(short: String, long: List[String], psifile: PsiFile, file: java.io.File, sr: TextRange)

  def doCheck: Boolean

  def deselectCheckBtn(): Unit
}

class ErrorViewerPanel extends ErrorViewerPanelInterface {
  private val aev = new AbstractErrorViewer

  private val errorViewer = new ErrorViewer(ControllerSingleton.getSingletonController())

  private object Jar {
    def clearFile(file: File) = errorViewer.clearFile(file.toString)
  }

  val checkBtn = aev.check

  override def deselectCheckBtn(): Unit = {
    checkBtn.setSelected(false)
  }

  override def getPanel: JPanel = aev.panel

  val displayName: String = "Errors"

  val root = new PatchedDefaultMutableTreeNode("Errors")
  val errorTree = new Tree(root)
  aev.btn_clear.addActionListener(this)
  aev.btn_clearAll.addActionListener(this)
  aev.check.addActionListener(this)
  aev.btn_build.addActionListener(this)
  ApplicationManager.getApplication.invokeLater { () =>
    aev.pane.setLayout(new BorderLayout())
    val scp = new JScrollPane(errorTree)
    aev.pane.add(scp)
    errorTree.setVisible(true)
    errorTree.setRootVisible(false)
    errorTree.revalidate()
    aev.panel.revalidate()
  }
  // errorTree.setFont(new Font("Dialog",Font.PLAIN,12))

  override def doCheck = aev.check.isSelected

  private val mouseAdapter = new MouseAdapter {
    override def mouseClicked(e: MouseEvent): Unit = {
      super.mouseClicked(e)
      val (x, y) = (e.getX, e.getY)
      val path = errorTree.getPathForLocation(x, y)
      if (SwingUtilities.isLeftMouseButton(e)) {
        if (path != null) path.getPath.lastOption match {
          case Some(el: ErrorLine) =>
            val elem = el.psiFile.findElementAt(el.textrange.getStartOffset)
            val descriptor = new OpenFileDescriptor(el.psiFile.getProject, el.psiFile.getContainingFile.getVirtualFile)
            val editor = FileEditorManager.getInstance(el.psiFile.getProject).openTextEditor(descriptor, true)
            editor.getCaretModel.moveToOffset(elem.getTextOffset)
          case _ =>
        }
      } else if (SwingUtilities.isRightMouseButton(e)) {
        if (path != null) path.getPath.lastOption match {
          case Some(el: PatchedDefaultMutableTreeNode) =>
            val popup = new JPopupMenu()
            object copythis extends JBMenuItem("Copy line to clipboard") with ActionListener {
              val text = el.getText

              override def actionPerformed(actionEvent: ActionEvent): Unit = {
                val clipboard = java.awt.Toolkit.getDefaultToolkit.getSystemClipboard
                clipboard.setContents(new StringSelection(text), null)
              }
            }
            copythis.addActionListener(copythis)
            // val copythis = new JBMenuItem("Copy line to clipboard")
            object copyall extends JBMenuItem("Copy full error to clipboard") with ActionListener {
              val text = getNodeText(el)

              override def actionPerformed(actionEvent: ActionEvent): Unit = {
                val clipboard = java.awt.Toolkit.getDefaultToolkit.getSystemClipboard
                clipboard.setContents(new StringSelection(text), null)
              }
            }
            copyall.addActionListener(copyall)
            if (el.getChildCount == 0) copyall.setEnabled(false)
            popup.add(copythis)
            popup.add(copyall)
            popup.show(errorTree, x, y)
          case _ =>
        }
      }
    }
  }

  private def getNodeText(node: PatchedDefaultMutableTreeNode, prefix: String = ""): String = {
    var str = prefix + node.getText
    val enum = node.children()
    while (enum.hasMoreElements) {
      val n = enum.nextElement().asInstanceOf[PatchedDefaultMutableTreeNode]
      str = str + "\n" + getNodeText(n, prefix + "- ")
    }
    str
  }

  errorTree.addMouseListener(mouseAdapter)

  private def model = errorTree.getModel.asInstanceOf[DefaultTreeModel]

  private def redraw = ApplicationManager.getApplication.invokeLater { () =>
    model.reload()
    errorTree.revalidate()
  }

  implicit def convert[A](e: java.util.Enumeration[A]): List[PatchedDefaultMutableTreeNode] = {
    var ls = Nil.asInstanceOf[List[PatchedDefaultMutableTreeNode]]
    while (e.hasMoreElements) ls ::= e.nextElement().asInstanceOf[PatchedDefaultMutableTreeNode]
    ls.reverse
  }

  override def clearFile(fileOldClass: java.io.File) = {
    val file = File(fileOldClass)

    root.children().find(_.getUserObject == file).foreach(root.remove)
    Jar.clearFile(file)
    redraw
  }

  private def getTop(file: File) = root.children().find(_.getUserObject == file).getOrElse {
    val nt = new PatchedDefaultMutableTreeNode(file)
    root.add(nt)
    redraw
    // errorTree.expandPath(new TreePath(nt.getPath.asInstanceOf[Array[Object]]))
    nt
  }

  private def add(file: File, node: PatchedDefaultMutableTreeNode) = {
    ApplicationManager.getApplication.invokeLater { () =>
      val top = getTop(file) /*
    (top.getLastLeaf,node) match {
      case (s : StatusLine,t : StatusLine) =>
        top.remove(s)
        top.add(node)
      case (s : StatusLine,_) =>
        top.remove(s)
        top.add(node)
        top.add(s)
      case (_,_) =>
        top.add(node)
    } */
      top.add(node)
      // val row = errorTree.getRowForPath(new TreePath(Array(root,top,top.getLastChild).asInstanceOf[Array[Object]]))
      // errorTree.setSelectionRow(row)
      model.reload()
      errorTree.expandPath(new TreePath(node.getPath.init.asInstanceOf[Array[Object]]))
      //
      errorTree.revalidate()
    }
  }

  def status(file: File, s: String) = add(file, StatusLine(s))

  class ErrorLine(message: String, val textrange: TextRange, val psiFile: PsiFile) extends PatchedDefaultMutableTreeNode(message) {
    def addLine(s: String) = add(new PatchedDefaultMutableTreeNode(s))
  }

  case class StatusLine(message: String) extends PatchedDefaultMutableTreeNode(message)

  override def addError(short: String, long: List[String], psifile: PsiFile,
                        file: java.io.File, sr: TextRange) = {
    //val filetop = getTop(file)
    val entry = new ErrorLine(short.trim, sr, psifile)
    add(File(file), entry)
    ApplicationManager.getApplication.invokeLater { () =>
      long.reverse.foreach(s => entry.addLine(s.trim))
    }
    // filetop.add(entry)
    // redraw
  }

  override def actionPerformed(e: ActionEvent): Unit = {
    if (e.getActionCommand == "Clear") {
      root.removeAllChildren()
      redraw
    } else if (e.getActionCommand == "Clear All") {
      root.removeAllChildren()
      ControllerSingleton.getSingletonController().clear
      redraw
    } else if (e.getActionCommand == "Type Checking" && aev.check.isSelected) {
      MMT.getProject match {
        case Some(pr) =>
          // LocalFileSystem.getInstance().refresh(false)
          val editor = FileEditorManager.getInstance(pr).getSelectedTextEditor
          val psifile = PsiDocumentManager.getInstance(pr).getPsiFile(editor.getDocument)
          // psifile.getVirtualFile.refresh(true,true)
          utils.writable {
            DaemonCodeAnalyzer.getInstance(pr).restart(psifile)
            // psifile.getVirtualFile.refresh(true,true)
            // BlockSupport.getInstance(pr).reparseRange(psifile,0,psifile.getTextRange.getEndOffset,psifile.getText)
          }
        // ???
        case _ =>
      }
    } else if (e.getActionCommand == "Build File") {
      MMT.getProject match {
        case Some(pr) =>
          val editor = FileEditorManager.getInstance(pr).getSelectedTextEditor
          val psifile = PsiDocumentManager.getInstance(pr).getPsiFile(editor.getDocument)
          val f = utils.toFile(psifile)
          val p = utils.inotifyP("building " + f.name.toString + " to OMDoc...")
          utils.background {
            ControllerSingleton.getSingletonController().build(File(f))(e => {
              ApplicationManager.getApplication.invokeLater(() => {
                Messages.showErrorDialog(e.toStringLong, "MMT Build File error")
              })
            })
          }.onComplete { _ =>
            p.expire()
            utils.inotifyP("Done.", exp = 3000)
          }(scala.concurrent.ExecutionContext.global)
        /*
        utils.notifyWhileP("building " + f.name.toString + " to OMDoc...") {
          mmtjar.method("buildFile", Reflection.unit, List(f.toString))
        }
        */
        case _ =>
      }
    }
  }
}