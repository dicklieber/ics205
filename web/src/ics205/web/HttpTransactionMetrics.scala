package ics205.web

import cats.data.Kleisli
import cats.effect.{IO, Outcome}
import ics205.metrics.ApplicationMetrics
import org.http4s.HttpApp

private[web] object HttpTransactionMetrics:
  def apply(app: HttpApp[IO], metrics: ApplicationMetrics): HttpApp[IO] =
    Kleisli { request =>
      IO(metrics.startHttpTransaction()).bracketCase { stopTimer =>
        app(request).map(response =>
          response.withBodyStream(response.body.onFinalize(IO(stopTimer())))
        )
      } { (stopTimer, outcome) =>
        outcome match
          case Outcome.Succeeded(_) => IO.unit // The response body owns completion.
          case _ => IO(stopTimer())
      }
    }
