package ics205.web

import ics205.model.*
import java.time.LocalDateTime

class Ics205PageTests extends munit.FunSuite:
  private val prepared = LocalDateTime.of(2026, 9, 20, 14, 5)
  private val channel = Ics205Channel(
    id = "repeater", zoneGroup = Some("Local"), channelNumber = Some("1"),
    function = "Command", name = "Repeater", assignment = "Operations",
    frequency = RxWithOffset(mhz"146.94", mhz"-0.6"),
    bandwidth = Some(Bandwidth.Narrow),
    ctcss = Ctcss(Some(CtcssFrequency.Hz100_0), CtcssMode.Tone),
    remarks = "Monitor"
  )
  private val plan = Ics205(
    incidentName = "Exercise",
    operationalPeriod = OperationalPeriod(Some(prepared), Some(prepared.plusHours(4))),
    channels = Seq(channel), specialInstructions = "Check in\nEvery hour",
    preparedBy = Some(PreparedBy("Alex", Some("W9ABC"))), prepared = prepared
  )

  test("maps the form with a single CTCSS column"):
    val html = Ics205Page.renderPrintable(plan)
    val row = html.split("<tr class=\"channel-row\" data-channel-id=\"repeater\">")(1).split("</tr>")(0)
    val cells = "<td[^>]*>(.*?)</td>".r.findAllMatchIn(row).map(_.group(1)).toSeq
    assertEquals(cells, Seq(
      "Local", "1", "Command", "Repeater", "Operations", "146.94", "-0.6",
      "Narrow", "Tone 100 Hz", "FM", "Monitor"
    ))
    Seq("Exercise", "09/20/2026", "14:05", "18:05", "Alex / W9ABC",
      "Check in\nEvery hour", "Signature:", "Bandwidth", "Offset (MHz)").foreach(value =>
      assert(html.contains(value), value)
    )
    assert(!html.contains("TX Freq"))
    assert(!html.contains("146.34"))

  test("renders signed positive and zero offsets without rounding receive frequency"):
    val html = Ics205Page.renderPrintable(plan.copy(channels = Seq(
      channel.copy(frequency = RxWithOffset(mhz"446.00625", mhz"5")),
      channel.copy(frequency = RxWithOffset(mhz"146.52"))
    )))
    assert(html.contains(">446.00625</td>"))
    assert(html.contains(">+5</td>"))
    assert(html.contains(">0</td>"))

  test("escapes user data and leaves absent fields blank"):
    val html = Ics205Page.renderPrintable(plan.copy(
      incidentName = "<script>alert(1)</script>",
      preparedBy = None, operationalPeriod = OperationalPeriod(),
      channels = Seq(channel.copy(
        remarks = "<b>unsafe</b>", bandwidth = None,
        ctcss = Ctcss()
      ))
    ))
    assert(!html.contains("<script>"))
    assert(html.contains("&lt;script&gt;"))
    assert(html.contains("&lt;b&gt;unsafe&lt;/b&gt;"))
    assert(!html.contains("null"))
    assert(!html.contains("Tone 100 Hz"))

  test("renders plain remarks and TSQL"):
    val html = Ics205Page.renderPrintable(plan.copy(channels = Seq(
      channel.copy(mode = RadioMode.Digital, ctcss = Ctcss(Some(CtcssFrequency.Hz88_5), CtcssMode.TSQL),
        remarks = "Monitor\nCommand")
    )))
    Seq("TSQL 88.5 Hz", "Digital", "Monitor\nCommand").foreach(value => assert(html.contains(value), value))

  test("pads empty forms to eight rows and paginates without losing channels"):
    val empty = Ics205Page.renderPrintable(plan.copy(channels = Seq.empty))
    assertEquals("class=\"channel-row\"".r.findAllIn(empty).size, 8)
    val many = Ics205Page.renderPrintable(plan.copy(channels =
      (1 to 17).map(i => channel.copy(id = s"channel-$i"))
    ))
    assertEquals("class=\"sheet\"".r.findAllIn(many).size, 3)
    assertEquals("class=\"channel-row\"".r.findAllIn(many).size, 24)
    (1 to 17).foreach(i =>
      assertEquals(s"""data-channel-id="channel-$i"""".r.findAllIn(many).size, 1)
    )
