package info.kwarc.mmt.intellij

import com.intellij.notification.{Notification, NotificationType, Notifications}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.{VirtualFile, VirtualFileManager}
import info.kwarc.mmt.api.utils.File

package object util {
  def resource(f : String) = getClass.getResource("/" + f)
  def writable(fun : => Unit) : Unit = ApplicationManager.getApplication.runWriteAction(
    new Runnable {
      override def run(): Unit = fun
    })
  def errorMsg(f: => Unit)(implicit project : Project) : Unit = errorMsg(f,())
  def errorMsg [A](f : => A, orElse : => A)(implicit project : Project) : A = try {
    f
  } catch {
    case e : Exception =>
      Messages.showErrorDialog(project,e.getMessage + "\n\n" + e.getStackTrace.toList.mkString("\n"),"MMT Error")
      orElse
  }
  def inotify(message : String, title : String = "MMT") = {
    val not = new Notification("MMT",title,message,NotificationType.INFORMATION)
    Notifications.Bus.notify(not)
    not
  }
  def notifyWhile[A](message : String, title : String = "MMT")(fun : => A) : A = {
    val not = inotify(message,title)
    val ret = fun
    not.expire()
    ret
  }

  lazy val vfm = VirtualFileManager.getInstance()
  lazy val fs = vfm.getFileSystem("file")

  implicit def toVF(f : File): VirtualFile = {
    val vf = fs.findFileByPath(f.toFilePath.toString())
    if (vf != null) vf
    else {
      println(f.toFilePath)
      ???
    }
  }

  import java.util.Collections
  implicit def toJava[A](ls : List[A]) : java.util.List[A] = {
    val empty = new java.util.ArrayList[A]()
    Collections.addAll(empty,ls:_*)
    empty
  }
  implicit def toScala[A](ls : java.util.Collection[A]) : List[A] = ls.toArray().asInstanceOf[Array[A]].toList

}
