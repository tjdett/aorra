package controllers

import java.awt.Dimension
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

import scala.collection.JavaConversions.asScalaBuffer
import scala.collection.JavaConversions.mapAsScalaMap
import scala.collection.JavaConversions.seqAsJavaList
import scala.util.Try

import org.jcrom.Jcrom

import com.google.inject.Inject

import ScalaSecured.isAuthenticated
import charts.Region
import charts.builder.ChartBuilder
import charts.representations.Format
import controllers.Chart.getDatasourcesFromIDs
import javax.jcr.Session
import models.CacheableUser
import play.api.libs.iteratee.Enumerator
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc.Controller
import play.api.mvc.EssentialAction
import play.libs.F

class ArchiveAsync @Inject() (
  val jcrom: Jcrom,
  val filestore: service.filestore.FileStore,
  val sessionFactory: service.JcrSessionFactory)
  extends Controller {

  def chartArchive(id: String): EssentialAction = isAuthenticated { user =>
    implicit request => {
      val enumerator = zipEnumerator(addChartFilesToArchive(user, id))
      Ok.chunked(enumerator).withHeaders(
        "Content-Type" -> "application/zip",
        "Content-Disposition" -> s"attachment; filename=${id}.zip",
        "Cache-Control" -> "max-age=0, must-revalidate")
    }
  }

  protected def zipEnumerator(f: (ZipOutputStream) => Unit) =
    Enumerator.outputStream { os =>
      val zip = new ZipOutputStream(os);
      zip.setMethod(ZipOutputStream.DEFLATED);
      zip.setLevel(9);
      f(zip)
      zip.close()
    }

  protected def addChartFilesToArchive(
    user: CacheableUser, id: String)(zos: ZipOutputStream) {
    val cb = new ChartBuilder
    inSession(user) { session =>
      for (
        (file, datasource) <-
            getDatasourcesFromIDs(filestore, session, Seq(id));
        chart <- cb.getCharts(
            Seq(datasource), Region.values.toSeq, new Dimension);
        f <- Format.values;
        data <- Try(chart.outputAs(f).getContent()) // skip if unsupported
      ) {
        val filepath = "%s/%s-%s.%s".format(
          id,
          chart.getDescription(),
          file.getIdentifier(),
          f.name()).toLowerCase()
        zos.putNextEntry(new ZipEntry(filepath))
        zos.write(data)
        zos.closeEntry
      }
    }
  }

  protected def inSession[A](user: CacheableUser)(f: Session => A): A =
    sessionFactory.inSession(user.getJackrabbitUserId(),
      new F.Function[Session, A] {
        override def apply(session: Session) = f(session)
      })
}