package ics205


import io.dropwizard.metrics5.*

import scala.collection.mutable

trait StatsSource():
  private val metricRegistry: MetricRegistry = SharedMetricRegistries.getOrCreate(
    "default"
  )
  private val ourMetrics: mutable.Set[Metric] = mutable.Set[Metric]()

  def addCounter(name: String): Counter =
    track(metricRegistry.counter(prefixWithClass(name)))

  def addHistogram(name: String): Histogram =
    track(metricRegistry.histogram(prefixWithClass(name)))

  def addMeter(name: String): Meter =
    track(metricRegistry.meter(prefixWithClass(name)))

  def addTimer(name: String): Timer =
    track(metricRegistry.timer(prefixWithClass(name)))

  def addGauge[T](name: String)(value: => T): Gauge[T] =
    addGauge(
      name,
      new Gauge[T]:
        override def getValue: T = value
    )

  private def addGauge[T](name: String, gauge: Gauge[T]): Gauge[T] =
    track(metricRegistry.registerGauge(prefixWithClass(name), gauge))

  /** Prefixes metrics with the runtime class name, like LazyLogging's logger names. */
  def prefixWithClass(name: String): String =
    s"${getClass.getName.stripSuffix("$")}.$name"

  /**
   * Tracks and registers the given metric in the internal set of metrics.
   *
   * @param metric the metric instance to be registered and tracked.
   * @return the same metric instance that was passed as an argument. */
  private def track[T <: Metric](metric: T): T =
    ourMetrics += metric
    metric

//  def addSettableGauge[T](name: String): SettableGauge[T] =
//    val gauge = new DefaultSettableGauge[T]()
//    metricRegistry.registerGauge(name, gauge)
//    track(gauge)

//  def addSettableGauge[T](name: String, initialValue: T): SettableGauge[T] =
//    val gauge = new DefaultSettableGauge[T](initialValue)
//    metricRegistry.registerGauge(name, gauge)
//    track(gauge)

//  def addMetric[T <: Metric](name: String, metric: T): T =
//    addMetric(MetricRegistry.name(prefixWithClass(name)), metric)
//
//  def addMetric[T <: Metric](name: MetricName, metric: T): T =
//    track(metricRegistry.register(name, metric))

//  def addMetricSet(metricSet: MetricSet): MetricSet =
//    metricRegistry.registerAll(metricSet)
//    track(metricSet)
//
//  def addMetricSet(name: String, metricSet: MetricSet): MetricSet =
//    metricRegistry.registerAll(MetricRegistry.name(prefixWithClass(name)), metricSet)
//    track(metricSet)
