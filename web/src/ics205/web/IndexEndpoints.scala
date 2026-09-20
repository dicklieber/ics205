package ics205.web

import cats.effect.IO
import ics205.Ics205Store
import jakarta.inject.{Inject, Singleton}
import scalatags.Text.all.*
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint

@Singleton
class IndexEndpoints @Inject() (val store: Ics205Store) extends ApiEndpoints:

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

  override val endpoints: List[ServerEndpoint[Any, IO]] = List(indexEndpoint)
