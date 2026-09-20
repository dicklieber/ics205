package ics205.metrics

import io.dropwizard.metrics5.{MetricRegistry, SharedMetricRegistries}

final class ApplicationMetrics(val registry: MetricRegistry):
  private val httpTransactions = registry.timer("http.transactions")

  /** Starts a transaction timer and returns its completion callback. */
  def startHttpTransaction(): () => Unit =
    val context = httpTransactions.time()
    () => { context.stop(); () }

  def renderPrometheus: String = PrometheusMetrics.render(registry)

object ApplicationMetrics:
  lazy val default: ApplicationMetrics =
    new ApplicationMetrics(SharedMetricRegistries.getOrCreate("default"))
