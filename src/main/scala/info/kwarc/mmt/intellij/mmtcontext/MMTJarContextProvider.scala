package info.kwarc.mmt.intellij.mmtcontext

import java.lang.reflect.{Method, Proxy}
import java.net.URL

import com.intellij.openapi.project.Project
import com.stackoverflow.yoni.ChildFirstURLClassLoader
import info.kwarc.mmt.intellij.MMT

import scala.collection.JavaConverters._
import scala.collection.mutable

final case class NotInMMTContextException(private val message: String = "",
                                          private val cause: Throwable = None.orNull)
  extends Exception(message, cause)

object MMTJarContextProvider {
  private val parentLastClassLoaders: mutable.Map[Project, ClassLoader] = new mutable.HashMap()
  private val proxyObjects: mutable.Map[(Project, Any), Any] = new mutable.HashMap()

  private def getPLCL(project: Project): ClassLoader = {
    parentLastClassLoaders.getOrElseUpdate(project, {
      // val mmtJar: URL = /* infer from project */ Paths.get("C:/Users/nroux/Desktop/ba-repo/code/mmt/deploy/mmt.jar").toUri.toURL

      val mmtJar = MMT
        .get(project)
        .getOrElse(throw new NotInMMTContextException)
        .mmtjarfile
        .toURI
        .toURL

      val urls: List[URL] = MMTJarContextProvider.getClass.getClassLoader.asInstanceOf[com.intellij.util.lang.UrlClassLoader].getBaseUrls.asScala.toList ::: List(mmtJar)

      val plcl = new ChildFirstURLClassLoader(
        urls.toArray,
        MMTJarContextProvider.getClass.getClassLoader
      )

      plcl
    })
  }

  def getProxyFor[S, T](project: Project, obj: Any, interface: Class[S], actualClass: Class[T]): S = {
    proxyObjects.getOrElseUpdate((project, obj), {
      val plcl = getPLCL(project)

      val remoteApi: Class[_] = plcl.loadClass(interface.getName)
      val remoteImpl: Class[_] = plcl.loadClass(actualClass.getName)

      val remoteObject: Any = remoteImpl.getConstructor().newInstance()

      Proxy.newProxyInstance(
        interface.getClassLoader,
        Array[Class[_]](interface),

        (proxy: Any, method: Method, args: Array[AnyRef]) => {
          val remoteMethod: Method = remoteApi.getMethod(method.getName, method.getParameterTypes: _*)
          remoteMethod.invoke(remoteObject, args: _*)
        }
      ).asInstanceOf[S]
    }).asInstanceOf[S]
  }
}
