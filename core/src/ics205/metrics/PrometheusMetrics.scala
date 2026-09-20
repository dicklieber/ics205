package ics205.metrics

import io.dropwizard.metrics5.*
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*

private[metrics] object PrometheusMetrics:
  private val PrometheusNamePrefix = "ics205_"

  def render(
      metricRegistry: MetricRegistry
  ): String =
    val lines = metricRegistry.getMetrics.asScala.toSeq
      .sortBy(
        _._1.getKey
      )
      .flatMap {
        case (metricName, metric) =>
          renderMetric(
            prometheusName(metricName.getKey),
            metric
          )
      }

    (lines :+ "").mkString(
      "\n"
    )

  private def renderMetric(
      name: String,
      metric: Metric
  ): Seq[String] =
    metric match
      case gauge: Gauge[?] =>
        renderGauge(
          name,
          gauge
        )
      case counter: Counter =>
        renderCounter(
          name,
          counter
        )
      case timer: Timer =>
        renderTimer(
          name,
          timer
        )
      case histogram: Histogram =>
        renderHistogram(
          name,
          histogram
        )
      case _ =>
        Seq.empty

  private def renderGauge(
      name: String,
      gauge: Gauge[?]
  ): Seq[String] =
    numericValue(
      gauge.getValue
    ).toSeq.flatMap(value =>
      typedSample(
        name = name,
        metricType = "gauge",
        help = "Dropwizard gauge",
        sample = sample(
          name,
          value
        )
      )
    )

  private def renderCounter(
      name: String,
      counter: Counter
  ): Seq[String] =
    val prometheusName = s"${name}_total"
    typedSample(
      name = prometheusName,
      metricType = "counter",
      help = "Dropwizard counter count",
      sample = sample(
        prometheusName,
        counter.getCount.toDouble
      )
    )


  private def renderHistogram(
      name: String,
      histogram: Histogram
  ): Seq[String] =
    val snapshot = histogram.getSnapshot
    typedSample(
      name = name,
      metricType = "summary",
      help = "Dropwizard histogram snapshot",
      sample = summarySamples(
        name = name,
        count = histogram.getCount,
        snapshot = snapshot,
        scale = identity
      )
    )

  private def renderTimer(
      name: String,
      timer: Timer
  ): Seq[String] =
    val secondsName = s"${name}_seconds"
    typedSample(
      name = secondsName,
      metricType = "summary",
      help = "Dropwizard timer duration",
      sample = summarySamples(
        name = secondsName,
        count = timer.getCount,
        snapshot = timer.getSnapshot,
        scale = nanosToSeconds
      )
    ) ++ rateSamples(
      name,
      timer
    )

  private def rateSamples(
      name: String,
      meter: Metered
  ): Seq[String] =
    val rateName = s"${name}_rate_per_second"
    typedSample(
      name = rateName,
      metricType = "gauge",
      help = "Dropwizard exponentially-weighted moving average rates",
      sample = Seq(
        sample(
          rateName,
          meter.getOneMinuteRate,
          "window" -> "1m"
        ),
        sample(
          rateName,
          meter.getFiveMinuteRate,
          "window" -> "5m"
        ),
        sample(
          rateName,
          meter.getFifteenMinuteRate,
          "window" -> "15m"
        )
      )
    )

  private def summarySamples(
      name: String,
      count: Long,
      snapshot: Snapshot,
      scale: Double => Double
  ): Seq[String] =
    Seq(
      sample(
        name,
        scale(
          snapshot.getMedian
        ),
        "quantile" -> "0.5"
      ),
      sample(
        name,
        scale(
          snapshot.get75thPercentile
        ),
        "quantile" -> "0.75"
      ),
      sample(
        name,
        scale(
          snapshot.get95thPercentile
        ),
        "quantile" -> "0.95"
      ),
      sample(
        name,
        scale(
          snapshot.get98thPercentile
        ),
        "quantile" -> "0.98"
      ),
      sample(
        name,
        scale(
          snapshot.get99thPercentile
        ),
        "quantile" -> "0.99"
      ),
      sample(
        name,
        scale(
          snapshot.get999thPercentile
        ),
        "quantile" -> "0.999"
      ),
      sample(
        s"${name}_count",
        count.toDouble
      )
    )

  private def typedSample(
      name: String,
      metricType: String,
      help: String,
      sample: Seq[String]
  ): Seq[String] =
    if sample.isEmpty then
      Seq.empty
    else
      Seq(
        s"# HELP $name ${escapeHelp(help)}",
        s"# TYPE $name $metricType"
      ) ++ sample

  private def typedSample(
      name: String,
      metricType: String,
      help: String,
      sample: String
  ): Seq[String] =
    typedSample(
      name = name,
      metricType = metricType,
      help = help,
      sample = Seq(
        sample
      )
    )

  private def sample(
      name: String,
      value: Double,
      labels: (String, String)*
  ): String =
    val labelText =
      if labels.isEmpty then
        ""
      else
        labels
          .map {
            case (key, value) =>
              s"""${prometheusLabelName(key)}="${escapeLabel(value)}""""
          }
          .mkString(
            "{",
            ",",
            "}"
          )

    s"$name$labelText ${formatDouble(value)}"

  private def numericValue(
      value: Any
  ): Option[Double] =
    value match
      case number: java.lang.Number =>
        Some(
          number.doubleValue()
        )
      case _ =>
        None

  private def prometheusName(
      rawName: String
  ): String =
    s"$PrometheusNamePrefix${prometheusIdentifier(rawName)}"

  private def prometheusLabelName(
      rawName: String
  ): String =
    prometheusIdentifier(
      rawName
    )

  private def prometheusIdentifier(
      rawName: String
  ): String =
    val sanitized = rawName.toSeq
      .map { char =>
        if char.isLetterOrDigit || char == '_' then
          char
        else
          '_'
      }
      .mkString
      .replaceAll(
        "_+",
        "_"
      )
      .stripPrefix(
        "_"
      )
      .stripSuffix(
        "_"
      )

    val normalized =
      if sanitized.isEmpty then
        "metric"
      else if sanitized.head.isDigit then
        s"_$sanitized"
      else
        sanitized

    normalized

  private def escapeHelp(
      value: String
  ): String =
    value
      .replace(
        "\\",
        "\\\\"
      )
      .replace(
        "\n",
        "\\n"
      )

  private def escapeLabel(
      value: String
  ): String =
    escapeHelp(
      value
    ).replace(
      "\"",
      "\\\""
    )

  private def formatDouble(
      value: Double
  ): String =
    if value.isPosInfinity then
      "+Inf"
    else if value.isNegInfinity then
      "-Inf"
    else
      value.toString

  private def nanosToSeconds(
      nanos: Double
  ): Double =
    nanos / TimeUnit.SECONDS.toNanos(
      1L
    )
