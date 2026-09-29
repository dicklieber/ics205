/*
 * Copyright (c) 2026. Dick Lieber, WA9NNN
 *
 * This program is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License as published by 
 * the Free Software Foundation, either version 3 of the License, or    
 * (at your option) any later version.                                  
 *                                                                      
 * This program is distributed in the hope that it will be useful,      
 * but WITHOUT ANY WARRANTY; without even the implied warranty of       
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the        
 * GNU General Public License for more details.                         
 *                                                                      
 * You should have received a copy of the GNU General Public License    
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package ics205.metrics

import io.dropwizard.metrics5.*
import java.util.concurrent.TimeUnit

class PrometheusMetricsTests extends munit.FunSuite:

  test("render empty metric registry returns empty trailing newline"):
    val registry = new MetricRegistry()
    val output = PrometheusMetrics.render(registry)
    assertEquals(output, "")

  test("render counter formats correctly with _total suffix"):
    val registry = new MetricRegistry()
    val counter = registry.counter("http_requests")
    counter.inc(42)

    val output = PrometheusMetrics.render(registry)
    assert(output.contains("# HELP ics205_http_requests_total Dropwizard counter count"))
    assert(output.contains("# TYPE ics205_http_requests_total counter"))
    assert(output.contains("ics205_http_requests_total 42.0"))

  test("render gauge with numeric value formats correctly"):
    val registry = new MetricRegistry()
    registry.registerGauge("active_users", () => 15)

    val output = PrometheusMetrics.render(registry)
    assert(output.contains("# HELP ics205_active_users Dropwizard gauge"))
    assert(output.contains("# TYPE ics205_active_users gauge"))
    assert(output.contains("ics205_active_users 15.0"))

  test("render gauge with non-numeric value produces no samples"):
    val registry = new MetricRegistry()
    registry.registerGauge("string_gauge", () => "not a number")

    val output = PrometheusMetrics.render(registry)
    assertEquals(output, "")

  test("render gauge with infinities formats correctly"):
    val registry = new MetricRegistry()
    registry.registerGauge("pos_inf", () => Double.PositiveInfinity)
    registry.registerGauge("neg_inf", () => Double.NegativeInfinity)

    val output = PrometheusMetrics.render(registry)
    assert(output.contains("ics205_pos_inf +Inf"))
    assert(output.contains("ics205_neg_inf -Inf"))

  test("render histogram formats quantiles and count"):
    val registry = new MetricRegistry()
    val hist = registry.histogram("payload_size")
    (1 to 100).foreach(hist.update)

    val output = PrometheusMetrics.render(registry)
    assert(output.contains("# HELP ics205_payload_size Dropwizard histogram snapshot"))
    assert(output.contains("# TYPE ics205_payload_size summary"))
    assert(output.contains("""ics205_payload_size{quantile="0.5"}"""))
    assert(output.contains("""ics205_payload_size{quantile="0.75"}"""))
    assert(output.contains("""ics205_payload_size{quantile="0.95"}"""))
    assert(output.contains("""ics205_payload_size{quantile="0.98"}"""))
    assert(output.contains("""ics205_payload_size{quantile="0.99"}"""))
    assert(output.contains("""ics205_payload_size{quantile="0.999"}"""))
    assert(output.contains("ics205_payload_size_count 100.0"))

  test("render timer formats seconds summary, rates, and count"):
    val registry = new MetricRegistry()
    val timer = registry.timer("request_duration")
    timer.update(100, TimeUnit.MILLISECONDS)
    timer.update(200, TimeUnit.MILLISECONDS)

    val output = PrometheusMetrics.render(registry)
    assert(output.contains("# HELP ics205_request_duration_seconds Dropwizard timer duration"))
    assert(output.contains("# TYPE ics205_request_duration_seconds summary"))
    assert(output.contains("""ics205_request_duration_seconds{quantile="0.5"}"""))
    assert(output.contains("ics205_request_duration_seconds_count 2.0"))
    assert(output.contains("# HELP ics205_request_duration_rate_per_second Dropwizard exponentially-weighted moving average rates"))
    assert(output.contains("# TYPE ics205_request_duration_rate_per_second gauge"))
    assert(output.contains("""ics205_request_duration_rate_per_second{window="1m"}"""))
    assert(output.contains("""ics205_request_duration_rate_per_second{window="5m"}"""))
    assert(output.contains("""ics205_request_duration_rate_per_second{window="15m"}"""))

  test("render metric names sanitizes illegal chars, multiple underscores, and numbers"):
    val registry = new MetricRegistry()
    registry.counter("123.metric-name@test__foo...bar").inc(1)

    val output = PrometheusMetrics.render(registry)
    assert(output.contains("ics205__123_metric_name_test_foo_bar_total 1.0"))

  test("render metric names handles empty sanitized name fallback"):
    val registry = new MetricRegistry()
    registry.counter("...").inc(5)

    val output = PrometheusMetrics.render(registry)
    assert(output.contains("ics205_metric_total 5.0"))

  test("render unhandled metric type produces empty output"):
    val registry = new MetricRegistry()
    registry.register(MetricName.build("custom_set"), new MetricSet:
      override def getMetrics: java.util.Map[MetricName, Metric] = java.util.Collections.emptyMap()
    )
    val output = PrometheusMetrics.render(registry)
    assertEquals(output, "")

  test("ApplicationMetrics startHttpTransaction and renderPrometheus"):
    val registry = new MetricRegistry()
    val appMetrics = new ApplicationMetrics(registry)
    val stopTimer = appMetrics.startHttpTransaction()
    stopTimer()

    val output = appMetrics.renderPrometheus
    assert(output.contains("ics205_http_transactions_seconds"))
    assert(output.contains("ics205_http_transactions_seconds_count 1.0"))

  test("ApplicationMetrics.default exists and renders"):
    val defaultMetrics = ApplicationMetrics.default
    assert(defaultMetrics != null)
    assert(defaultMetrics.registry != null)
    val rendered = defaultMetrics.renderPrometheus
    assert(rendered.isInstanceOf[String])
