package ics205.web

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import ics205.model.*
import ics205.store.Ics205Store
import ics205.util.FileHelper
import org.http4s.{Method, Request, Status, Uri, UrlForm}
import sttp.tapir.server.http4s.Http4sServerInterpreter
import java.time.LocalDateTime

class Ics205FormTests extends munit.FunSuite:
  private val prepared = LocalDateTime.of(2026, 9, 20, 12, 30, 15)
  private val base = Ics205(
    incidentName = "Exercise", operationalPeriod = OperationalPeriod(Some(prepared), Some(prepared.plusHours(1))),
    prepared = prepared, preparedBy = Some(PreparedBy("Alex", Some("W9ABC"))),
    specialInstructions = "Line one\nLine two", formatVersion = "1.1",
    channels = Seq(
      Ics205Channel("a", Some("Zone"), Some("1"), "Command", "Repeater", "All",
        RxWithOffset(mhz"146.940", mhz"-0.600"), bandwidth = Some(Bandwidth.Narrow),
        ctcss = Ctcss(Some(CtcssFrequency.Hz100_0), CtcssMode.Tone),
        remarks = "Monitor"),
      Ics205Channel("b", function = "Tactical", name = "Simplex", assignment = "Teams",
        frequency = RxWithOffset(mhz"446.00625"), mode = RadioMode.Digital,
        ctcss = Ctcss(Some(CtcssFrequency.Hz88_5), CtcssMode.TSQL)),
      Ics205Channel("c", function = "", name = "", assignment = "", frequency = RxWithOffset(mhz"155.5"))
    )
  )

  test("form fields round trip every model field, precision, and empty remarks"):
    assertEquals(Ics205Form.decode(Ics205Form.fields(base), base), Right(base))
    val blank = base.copy(channels = Seq.empty, preparedBy = None, operationalPeriod = OperationalPeriod())
    assertEquals(Ics205Form.decode(Ics205Form.fields(blank), base), Right(blank))

  test("row order, deletion, and new stable IDs are decoded from submitted order"):
    val edited = base.copy(channels = Seq(base.channels(2), base.channels.head.copy(id = "new")))
    assertEquals(Ics205Form.decode(Ics205Form.fields(edited), base), Right(edited))

  test("invalid values fail instead of dropping or silently changing fields"):
    val fields = Ics205Form.fields(base)
    Seq(
      "row.0.rx" -> "oops", "row.0.offset" -> "-999",
      "row.0.ctcssMode" -> "Unknown", "row.1.ctcssFrequency" -> "XYZ",
      "row.0.ctcssFrequency" -> "-1", "row.0.mode" -> "Unknown",
      "row.0.id" -> "b", "prepared" -> "bad", "to" -> prepared.minusDays(1).toString
    ).foreach { (key, value) =>
      assert(Ics205Form.decode(fields.updated(key, value), base).isLeft, s"$key=$value")
    }

  test("HTML uses named controls and safely retains rejected text"):
    val html = Ics205Editor.render(base, Some(Ics205Form.fields(base).updated("incidentName", "<script>bad</script>")),
      Some("RX is invalid"))
    assert(html.contains("<form"))
    assert(html.contains("method=\"post\""))
    assert(html.contains("name=\"row.0.rx\""))
    assert(html.contains("value=\"&lt;script&gt;bad&lt;/script&gt;\""))
    assert(html.contains("role=\"alert\""))
    assert(html.contains("data-action=\"up\""))
    assert(html.contains("data-action=\"down\""))
    assert(html.contains("data-action=\"delete\""))

  test("save persists edits and prepared time; preview and validation do not write"):
    val tempDirectory = os.temp.dir()
    try
      val helper = new FileHelper:
        override val directory: os.Path = tempDirectory
      val store = new Ics205Store(helper)
      val app = Http4sServerInterpreter[IO]().toRoutes(new IndexEndpoints(store).endpoints).orNotFound
      def post(path: String, fields: Map[String, String]) =
        app.run(Request[IO](Method.POST, Uri.unsafeFromString(path))
          .withEntity(UrlForm(fields.toSeq*))).unsafeRunSync()
      val fields = Ics205Form.fields(base)
      val preview = post("/preview", fields)
      assertEquals(preview.status, Status.Ok)
      assert(preview.as[String].unsafeRunSync().contains("Exercise"))
      assert(!os.exists(tempDirectory / "ics205.json"))
      val invalid = post("/", fields.updated("row.0.rx", "invalid"))
      assertEquals(invalid.status, Status.UnprocessableEntity)
      assert(invalid.as[String].unsafeRunSync().contains("value=\"invalid\""))
      assert(!os.exists(tempDirectory / "ics205.json"))
      val saved = post("/", fields)
      assertEquals(saved.status, Status.SeeOther)
      assert(saved.headers.headers.exists(h => h.name.toString == "Location" && h.value == "/?saved=1"))
      // Internal format version comes from the stored document, not form input.
      val expected = base.copy(formatVersion = "1.0")
      assertEquals(store.ics205(), expected)
      assertEquals(new Ics205Store(helper).ics205(), expected)
      val empty = expected.copy(channels = Seq.empty)
      assertEquals(post("/", Ics205Form.fields(empty)).status, Status.SeeOther)
      assertEquals(store.ics205().channels, Seq.empty)
    finally os.remove.all(tempDirectory)



  test("None needs no frequency; Tone and TSQL require a standard frequency"):
    val fields = Ics205Form.fields(base)
    val none = fields.updated("row.0.ctcssMode", "None") - "row.0.ctcssFrequency"
    assertEquals(Ics205Form.decode(none, base).toOption.get.channels.head.ctcss, Ctcss())
    assertEquals(Ics205Form.decode(none.updated("row.0.ctcssFrequency", "ignored"), base)
      .toOption.get.channels.head.ctcss, Ctcss())
    Seq("Tone", "TSQL").foreach { mode =>
      Seq("", "0", "-1", "100.1", "300").foreach { frequency =>
        assert(Ics205Form.decode(fields.updated("row.0.ctcssMode", mode)
          .updated("row.0.ctcssFrequency", frequency), base).isLeft)
      }
    }
