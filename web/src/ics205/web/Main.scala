package ics205.web

import cats.effect.{IO, IOApp}
import com.comcast.ip4s.*
import com.google.inject.Guice
import com.typesafe.scalalogging.LazyLogging
import ics205.Ics205Store
import jakarta.inject.Inject
import org.http4s.ember.server.EmberServerBuilder
import scalatags.Text.all.*
import sttp.tapir.*
import sttp.tapir.server.http4s.Http4sServerInterpreter

object Main extends IOApp.Simple:
  def run: IO[Unit] =
    IO(Guice.createInjector(new ApplicationModule))
      .flatMap(injector => IO(injector.getInstance(classOf[WebApplication])))
      .flatMap(_.run)

class WebApplication @Inject() (val store: Ics205Store) extends LazyLogging:

  def index(): String =
    doctype("html")(
      html(
        head(
          meta(charset := "utf-8"),
          scalatags.Text.tags2.title("ICS-205")
        ),
        body(
          h1("ICS-205"),
          p("Incident Radio Communications Plan")
        )
      )
    ).render

  private val indexEndpoint = endpoint.get
    .in("")
    .out(htmlBodyUtf8)
    .serverLogicSuccess[IO](_ => IO(index()))

  def run: IO[Unit] =
    EmberServerBuilder.default[IO]
      .withHost(host"localhost")
      .withPort(port"8080")
      .withHttpApp(Http4sServerInterpreter[IO]().toRoutes(indexEndpoint).orNotFound)
      .build
      .use(server =>
        IO(logger.info(
          s"${BuildInfo.name} ${BuildInfo.version} listening at ${server.baseUri} " +
            s"(Scala ${BuildInfo.scalaVersion}, Mill ${BuildInfo.millVersion})"
        )) *> IO.never
      )
