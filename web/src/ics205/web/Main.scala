package ics205.web

import scalatags.Text.all.*

object Main extends cask.MainRoutes:

  @cask.get("/")
  def index() =
    cask.Response(
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
      ).render,
      headers = Seq("Content-Type" -> "text/html; charset=utf-8")
    )

  initialize()
