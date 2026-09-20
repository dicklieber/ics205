package ics205.web

import cats.effect.{IO, IOApp}
import com.comcast.ip4s.*
import com.google.inject.Guice
import com.typesafe.scalalogging.LazyLogging
import ics205.BuildInfo
import ics205.metrics.ApplicationMetrics
import jakarta.inject.Inject
import org.http4s.ember.server.EmberServerBuilder
import sttp.tapir.server.http4s.Http4sServerInterpreter

import scala.jdk.CollectionConverters.*

object Main extends IOApp.Simple:
  def run: IO[Unit] =
    IO(Guice.createInjector(new ApplicationModule))
      .flatMap(injector => IO(injector.getInstance(classOf[WebApplication])))
      .flatMap(_.run)

class WebApplication @Inject() (endpointsSet: java.util.Set[ApiEndpoints]) extends LazyLogging:

  val httpApp: org.http4s.HttpApp[IO] =
    val allEndpoints = endpointsSet.asScala.toList
      .sortBy(_.getClass.getName)
      .flatMap { group =>
        logger.debug(s"Adding endpoints from ${group.getClass.getName}")
        group.endpoints
      }
    HttpTransactionMetrics(
      Http4sServerInterpreter[IO]().toRoutes(allEndpoints).orNotFound,
      ApplicationMetrics.default
    )

  def run: IO[Unit] =
    EmberServerBuilder.default[IO]
      .withHost(host"localhost")
      .withPort(port"8080")
      .withHttpApp(httpApp)
      .build
      .use(server =>
        IO(logger.info(
          s"${BuildInfo.name} ${BuildInfo.version} listening at ${server.baseUri} " +
            s"(Scala ${BuildInfo.scalaVersion}, Mill ${BuildInfo.millVersion})"
        )) *> IO.never
      )
