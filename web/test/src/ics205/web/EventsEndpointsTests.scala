package ics205.web

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import ics205.auth.*
import ics205.model.*
import ics205.store.{Ics205Store, InMemJsonSessionStore, UserStore}
import ics205.util.FileHelper
import io.circe.parser.decode
import io.circe.syntax.*
import org.http4s.multipart.{Multipart, Part}
import org.http4s.{EntityEncoder, Method, Request, Status, Uri, UrlForm}
import org.typelevel.ci.CIString
import sttp.tapir.server.http4s.Http4sServerInterpreter

class EventsEndpointsTests extends munit.FunSuite:

  private def withTestContext(test: (os.Path, Ics205Store, UserStore, InMemJsonSessionStore, AuthenticationService, org.http4s.HttpRoutes[IO]) => Unit): Unit =
    val tempDir = os.temp.dir()
    try
      val helper = new FileHelper(tempDir)

      val store = new Ics205Store(helper)
      val userStore = new UserStore(helper)
      val sessionStore = new InMemJsonSessionStore(helper)
      val passwordService = new ScalaPassPasswordService
      val authService = new AuthenticationService(userStore, passwordService, sessionStore)
      val config = AuthConfig()
      val endpoints = new IndexEndpoints(store, authService, userStore, config, sessionStore)
      val routes = Http4sServerInterpreter[IO]().toRoutes(endpoints.endpoints)

      test(tempDir, store, userStore, sessionStore, authService, routes)
    finally
      os.remove.all(tempDir)

  test("GET /events requires authentication"):
    withTestContext { (_, _, _, _, _, routes) =>
      val req = Request[IO](Method.GET, Uri.unsafeFromString("/events"))
      val res = routes.orNotFound.run(req).unsafeRunSync()
      assertEquals(res.status, Status.Unauthorized)
    }

  test("GET /events lists visible events for authenticated user"):
    withTestContext { (_, store, userStore, sessionStore, _, routes) =>
      val passwordService = new ScalaPassPasswordService
      val admin = userStore.add(User("admin", passwordService.hash("pass"), Role.Admin, enabled = true, id = "u-admin")).toOption.get
      val session = sessionStore.create(admin.id)

      store.save(Ics205Event("Field Day", Ics205(incidentName = "Field Day 2026", operationalPeriod = OperationalPeriod(), channels = Seq.empty)))
      store.save(Ics205Event("Marathon", Ics205(incidentName = "City Marathon", operationalPeriod = OperationalPeriod(), channels = Seq.empty)))

      val req = Request[IO](Method.GET, Uri.unsafeFromString("/events"))
        .putHeaders(org.http4s.Header.Raw(org.typelevel.ci.CIString("Cookie"), s"session=${session.id}"))
      val res = routes.orNotFound.run(req).unsafeRunSync()
      assertEquals(res.status, Status.Ok)
      val body = res.as[String].unsafeRunSync()
      assert(body.contains("Field Day"))
      assert(body.contains("Marathon"))
      assert(body.contains("Plan"))
      assert(body.contains("Metadata"))
      assert(body.contains("Create New Event"))
      assert(!body.contains("Working On") && !body.contains(">Select<"))
      assert(body.contains("/?event=Field+Day"))
    }

  test("POST /events/create creates new event and sets current event in session"):
    withTestContext { (_, store, userStore, sessionStore, _, routes) =>
      val passwordService = new ScalaPassPasswordService
      val admin = userStore.add(User("admin", passwordService.hash("pass"), Role.Admin, enabled = true, id = "u-admin")).toOption.get
      val session = sessionStore.create(admin.id)

      val req = Request[IO](Method.POST, Uri.unsafeFromString("/events/create"))
        .putHeaders(org.http4s.Header.Raw(org.typelevel.ci.CIString("Cookie"), s"session=${session.id}"))
        .withEntity(UrlForm("eventName" -> "Winter Drill", "incidentName" -> "Winter Drill 2026"))

      val res = routes.orNotFound.run(req).unsafeRunSync()
      assertEquals(res.status, Status.SeeOther)
      val created = store.findByName("Winter Drill 2026")
      assert(created.isDefined)
      assertEquals(sessionStore.get(session.id).flatMap(_.currentIcs205), Some(created.get.id))
      assertEquals(created.get.ics205.incidentName, "Winter Drill 2026")
      assertEquals(res.headers.get(org.typelevel.ci.CIString("Location")).map(_.head.value), Some(s"/?event=${java.net.URLEncoder.encode(created.get.id, "UTF-8")}&saved=1"))
    }

  test("GET /events/select selects active event and sets current event in session"):
    withTestContext { (_, store, userStore, sessionStore, _, routes) =>
      val passwordService = new ScalaPassPasswordService
      val admin = userStore.add(User("admin", passwordService.hash("pass"), Role.Admin, enabled = true, id = "u-admin")).toOption.get
      val session = sessionStore.create(admin.id)

      store.save(Ics205Event("Campout", Ics205(incidentName = "Scout Campout", operationalPeriod = OperationalPeriod(), channels = Seq.empty)))

      val req = Request[IO](Method.GET, Uri.unsafeFromString("/events/select?name=Campout&returnUrl=/radio"))
        .putHeaders(org.http4s.Header.Raw(org.typelevel.ci.CIString("Cookie"), s"session=${session.id}"))

      val res = routes.orNotFound.run(req).unsafeRunSync()
      assertEquals(res.status, Status.SeeOther)
      assertEquals(res.headers.get(org.typelevel.ci.CIString("Location")).map(_.head.value), Some("/radio"))
      assertEquals(sessionStore.get(session.id).flatMap(_.currentIcs205), Some("Campout"))
    }

  test("GET and POST /events/metadata manages event permissions"):
    withTestContext { (_, store, userStore, sessionStore, _, routes) =>
      val passwordService = new ScalaPassPasswordService
      val admin = userStore.add(User("admin", passwordService.hash("pass"), Role.Admin, enabled = true, id = "u-admin")).toOption.get
      val operator = userStore.add(User("operator", passwordService.hash("pass"), Role.Viewer, enabled = true, id = "u-op")).toOption.get
      val session = sessionStore.create(admin.id)

      store.save(Ics205Event("Airshow", Ics205(incidentName = "Annual Airshow", operationalPeriod = OperationalPeriod(), channels = Seq.empty)))

      // GET metadata
      val getReq = Request[IO](Method.GET, Uri.unsafeFromString("/events/metadata?name=Airshow"))
        .putHeaders(org.http4s.Header.Raw(org.typelevel.ci.CIString("Cookie"), s"session=${session.id}"))
      val getRes = routes.orNotFound.run(getReq).unsafeRunSync()
      assertEquals(getRes.status, Status.Ok)
      val getBody = getRes.as[String].unsafeRunSync()
      assert(getBody.contains("Airshow"))
      assert(getBody.contains("operator"))

      // POST metadata to grant operator EditPlans
      val postReq = Request[IO](Method.POST, Uri.unsafeFromString("/events/metadata"))
        .putHeaders(org.http4s.Header.Raw(org.typelevel.ci.CIString("Cookie"), s"session=${session.id}"))
        .withEntity(UrlForm(
          "originalEventName" -> "Airshow",
          "newEventName" -> "Airshow",
          "incidentName" -> "Annual Airshow",
          s"perm_${operator.id}" -> "edit"
        ))

      val postRes = routes.orNotFound.run(postReq).unsafeRunSync()
      assertEquals(postRes.status, Status.SeeOther)

      val updatedEvent = store.getEvent("Airshow").get
      assertEquals(updatedEvent.metadata.permissions.get(operator.id), Some(Permission.EditPlans))
      assertEquals(updatedEvent.canEdit(operator), true)
    }

  test("POST /events/metadata renames event and updates incident name"):
    withTestContext { (_, store, userStore, sessionStore, _, routes) =>
      val passwordService = new ScalaPassPasswordService
      val admin = userStore.add(User("admin", passwordService.hash("pass"), Role.Admin, enabled = true, id = "u-admin")).toOption.get
      val session = sessionStore.create(admin.id)

      store.save(Ics205Event("OldEvent", Ics205(incidentName = "Old Incident", operationalPeriod = OperationalPeriod(), channels = Seq.empty)))

      val postReq = Request[IO](Method.POST, Uri.unsafeFromString("/events/metadata"))
        .putHeaders(org.http4s.Header.Raw(org.typelevel.ci.CIString("Cookie"), s"session=${session.id}"))
        .withEntity(UrlForm(
          "originalEventName" -> "OldEvent",
          "newEventName" -> "NewEvent",
          "incidentName" -> "New Incident"
        ))

      val postRes = routes.orNotFound.run(postReq).unsafeRunSync()
      assertEquals(postRes.status, Status.SeeOther)
      val location = postRes.headers.get(org.typelevel.ci.CIString("Location")).map(_.head.value).getOrElse("")
      assert(location.contains("name=NewEvent"))

      assertEquals(store.getEvent("OldEvent"), None)
      val renamed = store.getEvent("NewEvent")
      assert(renamed.isDefined)
      assertEquals(renamed.get.eventName, "New Incident")
      assertEquals(renamed.get.ics205.incidentName, "New Incident")
    }

  test("Event authorization isolates permissions across multiple events"):
    withTestContext { (_, store, userStore, sessionStore, _, routes) =>
      val passwordService = new ScalaPassPasswordService
      val userViewer = userStore.add(User("viewerUser", passwordService.hash("pass"), Role.Viewer, enabled = true, id = "u-v")).toOption.get
      val session = sessionStore.create(userViewer.id)

      // Event 1 has explicit EditPlans for viewerUser
      val event1 = Ics205Event("AllowedEvent", Ics205(incidentName = "Allowed Incident", operationalPeriod = OperationalPeriod(), channels = Seq.empty),
        Ics205Metadata(permissions = Map(userViewer.id -> Permission.EditPlans)))
      // Event 2 has explicit ViewPlans for other users only
      val event2 = Ics205Event("RestrictedEvent", Ics205(incidentName = "Restricted Incident", operationalPeriod = OperationalPeriod(), channels = Seq.empty),
        Ics205Metadata(permissions = Map("other-user" -> Permission.ViewPlans)))

      store.save(event1)
      store.save(event2)

      // User can view and edit AllowedEvent
      val req1 = Request[IO](Method.GET, Uri.unsafeFromString("/?event=AllowedEvent"))
        .putHeaders(org.http4s.Header.Raw(org.typelevel.ci.CIString("Cookie"), s"session=${session.id}"))
      val res1 = routes.orNotFound.run(req1).unsafeRunSync()
      assertEquals(res1.status, Status.Ok)
      val body1 = res1.as[String].unsafeRunSync()
      assert(body1.contains("Allowed Incident"))
      assert(body1.contains("Save plan"))

      // User is forbidden from RestrictedEvent
      val req2 = Request[IO](Method.GET, Uri.unsafeFromString("/?event=RestrictedEvent"))
        .putHeaders(org.http4s.Header.Raw(org.typelevel.ci.CIString("Cookie"), s"session=${session.id}"))
      val res2 = routes.orNotFound.run(req2).unsafeRunSync()
      assertEquals(res2.status, Status.Forbidden)
    }

  test("POST /events/delete deletes event for Admin"):
    withTestContext { (_, store, userStore, sessionStore, _, routes) =>
      val passwordService = new ScalaPassPasswordService
      val admin = userStore.add(User("admin", passwordService.hash("pass"), Role.Admin, enabled = true, id = "u-admin")).toOption.get
      val session = sessionStore.create(admin.id)

      store.save(Ics205Event("ToDelete", Ics205(incidentName = "To Delete", operationalPeriod = OperationalPeriod(), channels = Seq.empty)))
      assert(store.getEvent("ToDelete").isDefined)

      val req = Request[IO](Method.POST, Uri.unsafeFromString("/events/delete"))
        .putHeaders(org.http4s.Header.Raw(org.typelevel.ci.CIString("Cookie"), s"session=${session.id}"))
        .withEntity(UrlForm("eventName" -> "ToDelete"))

      val res = routes.orNotFound.run(req).unsafeRunSync()
      assertEquals(res.status, Status.SeeOther)
      assertEquals(store.getEvent("ToDelete"), None)
    }

  test("When no events exist, navigating to / and /radio redirects to /events"):
    withTestContext { (_, store, userStore, sessionStore, _, routes) =>
      val passwordService = new ScalaPassPasswordService
      val admin = userStore.add(User("admin", passwordService.hash("pass"), Role.Admin, enabled = true, id = "u-admin")).toOption.get
      val session = sessionStore.create(admin.id)

      assertEquals(store.events().size, 0)

      val req1 = Request[IO](Method.GET, Uri.unsafeFromString("/"))
        .putHeaders(org.http4s.Header.Raw(org.typelevel.ci.CIString("Cookie"), s"session=${session.id}"))
      val res1 = routes.orNotFound.run(req1).unsafeRunSync()
      assertEquals(res1.status, Status.SeeOther)
      assertEquals(res1.headers.get(org.typelevel.ci.CIString("Location")).map(_.head.value), Some("/events"))

      val req2 = Request[IO](Method.GET, Uri.unsafeFromString("/radio"))
        .putHeaders(org.http4s.Header.Raw(org.typelevel.ci.CIString("Cookie"), s"session=${session.id}"))
      val res2 = routes.orNotFound.run(req2).unsafeRunSync()
      assertEquals(res2.status, Status.SeeOther)
      assertEquals(res2.headers.get(org.typelevel.ci.CIString("Location")).map(_.head.value), Some("/events"))

      val reqEvents = Request[IO](Method.GET, Uri.unsafeFromString("/events"))
        .putHeaders(org.http4s.Header.Raw(org.typelevel.ci.CIString("Cookie"), s"session=${session.id}"))
      val resEvents = routes.orNotFound.run(reqEvents).unsafeRunSync()
      assertEquals(resEvents.status, Status.Ok)
      val body = resEvents.as[String].unsafeRunSync()
      assert(body.contains("No events found. Create an event below to get started."))
      assert(!body.contains("(Default)"))
    }

  test("GET /events/export exports Ics205Event as JSON attachment"):
    withTestContext { (_, store, userStore, sessionStore, _, routes) =>
      val passwordService = new ScalaPassPasswordService
      val admin = userStore.add(User("admin", passwordService.hash("pass"), Role.Admin, enabled = true, id = "u-admin")).toOption.get
      val session = sessionStore.create(admin.id)

      val plan = Ics205(incidentName = "Wildfire Incident", operationalPeriod = OperationalPeriod(), channels = Seq.empty)
      val event = Ics205Event("Wildfire 2026", plan)
      store.save(event)

      val req = Request[IO](Method.GET, Uri.unsafeFromString("/events/export?name=Wildfire+2026"))
        .putHeaders(org.http4s.Header.Raw(CIString("Cookie"), s"session=${session.id}"))

      val res = routes.orNotFound.run(req).unsafeRunSync()
      assertEquals(res.status, Status.Ok)
      val disposition = res.headers.get(CIString("Content-Disposition")).map(_.head.value).getOrElse("")
      assert(disposition.contains("attachment; filename=\"Wildfire Incident.json\""))
      val body = res.as[String].unsafeRunSync()
      val decoded = decode[Ics205Event](body)
      assert(decoded.isRight)
      assertEquals(decoded.toOption.get.id, "Wildfire 2026")
      assertEquals(decoded.toOption.get.eventName, "Wildfire Incident")
      assertEquals(decoded.toOption.get.ics205.incidentName, "Wildfire Incident")
    }

  test("POST /events/import imports Ics205Event JSON and adds suffix if event exists"):
    withTestContext { (_, store, userStore, sessionStore, _, routes) =>
      val passwordService = new ScalaPassPasswordService
      val admin = userStore.add(User("admin", passwordService.hash("pass"), Role.Admin, enabled = true, id = "u-admin")).toOption.get
      val session = sessionStore.create(admin.id)

      // Seed an existing event named "Winter Drill"
      store.save(Ics205Event("Winter Drill", Ics205(incidentName = "Winter Drill", operationalPeriod = OperationalPeriod(), channels = Seq.empty)))

      // Prepare an imported event JSON with the same name "Winter Drill"
      val importPayload = Ics205Event("Winter Drill", Ics205(incidentName = "Winter Drill", operationalPeriod = OperationalPeriod(), channels = Seq.empty, specialInstructions = "Imported"))
      val jsonBytes = importPayload.asJson.spaces2.getBytes(java.nio.charset.StandardCharsets.UTF_8)

      val multipart = Multipart[IO](Vector(
        Part.formData[IO]("file", new String(jsonBytes, java.nio.charset.StandardCharsets.UTF_8), org.http4s.headers.`Content-Disposition`("form-data", Map(CIString("name") -> "file", CIString("filename") -> "drill.json")))
      ))

      val req = Request[IO](Method.POST, Uri.unsafeFromString("/events/import"))
        .putHeaders(org.http4s.Header.Raw(CIString("Cookie"), s"session=${session.id}"))
        .withEntity(multipart)(using EntityEncoder.multipartEncoder[IO])
        .putHeaders(multipart.headers.headers.map(h => org.http4s.Header.Raw(h.name, h.value)))

      val res = routes.orNotFound.run(req).unsafeRunSync()
      assertEquals(res.status, Status.SeeOther)
      assertEquals(sessionStore.get(session.id).flatMap(_.currentIcs205), Some("Winter Drill (1)"))

      // Verify that both original and suffixed events exist in store
      assert(store.getEvent("Winter Drill").isDefined)
      val imported = store.getEvent("Winter Drill (1)")
      assert(imported.isDefined)
      assertEquals(imported.get.ics205.specialInstructions, "Imported")
    }

  test("POST /events/import imports unwrapped Ics205 plan JSON"):
    withTestContext { (_, store, userStore, sessionStore, _, routes) =>
      val passwordService = new ScalaPassPasswordService
      val admin = userStore.add(User("admin", passwordService.hash("pass"), Role.Admin, enabled = true, id = "u-admin")).toOption.get
      val session = sessionStore.create(admin.id)

      val plan = Ics205(incidentName = "Marathon Incident", operationalPeriod = OperationalPeriod(), channels = Seq.empty)
      val jsonBytes = Ics205Event(plan).asJson.spaces2.getBytes(java.nio.charset.StandardCharsets.UTF_8)

      val multipart = Multipart[IO](Vector(
        Part.formData[IO]("file", new String(jsonBytes, java.nio.charset.StandardCharsets.UTF_8), org.http4s.headers.`Content-Disposition`("form-data", Map(CIString("name") -> "file", CIString("filename") -> "marathon.json")))
      ))

      val req = Request[IO](Method.POST, Uri.unsafeFromString("/events/import"))
        .putHeaders(org.http4s.Header.Raw(CIString("Cookie"), s"session=${session.id}"))
        .withEntity(multipart)(using EntityEncoder.multipartEncoder[IO])
        .putHeaders(multipart.headers.headers.map(h => org.http4s.Header.Raw(h.name, h.value)))

      val res = routes.orNotFound.run(req).unsafeRunSync()
      assertEquals(res.status, Status.SeeOther)

      val imported = store.getEvent("Marathon Incident")
      assert(imported.isDefined)
      assertEquals(imported.get.ics205.incidentName, "Marathon Incident")
    }

  test("POST /events/import rejects viewer without EditPlans permission"):
    withTestContext { (_, store, userStore, sessionStore, _, routes) =>
      val passwordService = new ScalaPassPasswordService
      val viewer = userStore.add(User("viewer", passwordService.hash("pass"), Role.Viewer, enabled = true, id = "u-view")).toOption.get
      val session = sessionStore.create(viewer.id)

      val plan = Ics205(incidentName = "Forbidden Incident", operationalPeriod = OperationalPeriod(), channels = Seq.empty)
      val multipart = Multipart[IO](Vector(
        Part.formData[IO]("file", Ics205Event(plan).asJson.spaces2, org.http4s.headers.`Content-Disposition`("form-data", Map(CIString("name") -> "file", CIString("filename") -> "forbid.json")))
      ))

      val req = Request[IO](Method.POST, Uri.unsafeFromString("/events/import"))
        .putHeaders(org.http4s.Header.Raw(CIString("Cookie"), s"session=${session.id}"))
        .withEntity(multipart)(using EntityEncoder.multipartEncoder[IO])
        .putHeaders(multipart.headers.headers.map(h => org.http4s.Header.Raw(h.name, h.value)))

      val res = routes.orNotFound.run(req).unsafeRunSync()
      assertEquals(res.status, Status.Forbidden)
    }

  test("Duplicate copies the plan and permissions, validates names, and requires edit access"):
    withTestContext { (_, store, userStore, sessionStore, _, routes) =>
      val passwordService = new ScalaPassPasswordService
      val admin = userStore.add(User("admin", passwordService.hash("pass"), Role.Admin, enabled = true, id = "u-admin")).toOption.get
      val viewer = userStore.add(User("viewer", passwordService.hash("pass"), Role.Viewer, enabled = true, id = "u-view")).toOption.get
      val adminSession = sessionStore.create(admin.id)
      val viewerSession = sessionStore.create(viewer.id)
      store.save(Ics205Event("Original", Ics205(incidentName = "Original", operationalPeriod = OperationalPeriod(), channels = Seq.empty),
        Ics205Metadata()))
      val original = store.getEvent("Original").get
      def duplicate(name: String, sessionId: String) =
        routes.orNotFound.run(Request[IO](Method.POST, Uri.unsafeFromString("/events/duplicate"))
          .putHeaders(org.http4s.Header.Raw(CIString("Cookie"), s"session=$sessionId"))
          .withEntity(UrlForm("eventName" -> "Original", "newEventName" -> name))).unsafeRunSync()

      assertEquals(duplicate("Original (1)", viewerSession.id).status, Status.Forbidden)
      assertEquals(store.findByName("Original (1)"), None)
      assertEquals(duplicate("Original (1)", adminSession.id).status, Status.SeeOther)
      val copied = store.findByName("Original (1)").get
      assertEquals(copied.ics205, original.ics205.copy(incidentName = "Original (1)"))
      assertEquals(copied.metadata.permissions, original.metadata.permissions)
      assertEquals(copied.metadata.lastEditedBy, Some(admin.id))
      assertEquals(store.getEvent("Original").get, original)
      val collision = duplicate("original", adminSession.id)
      assert(collision.headers.get(CIString("Location")).get.head.value.contains("err="))
      val blank = duplicate("   ", adminSession.id)
      assert(blank.headers.get(CIString("Location")).get.head.value.contains("err="))
      assertEquals(store.events().size, 2)
    }

  test("POST / saves plan to store for existing event"):
    withTestContext { (_, store, userStore, sessionStore, _, routes) =>
      val passwordService = new ScalaPassPasswordService
      val admin = userStore.add(User("admin", passwordService.hash("pass"), Role.Admin, enabled = true, id = "u-admin")).toOption.get
      val session = sessionStore.create(admin.id)

      val originalPlan = Ics205(incidentName = "Field Day Original", operationalPeriod = OperationalPeriod(), channels = Seq.empty)
      store.save(Ics205Event("Field Day", originalPlan))

      val fields = Ics205Form.fields(originalPlan)
        .updated("eventName", "Field Day")
        .updated("incidentName", "Field Day Updated")
        .updated("specialInstructions", "Check repeaters")

      val req = Request[IO](Method.POST, Uri.unsafeFromString("/?event=Field+Day"))
        .putHeaders(org.http4s.Header.Raw(CIString("Cookie"), s"session=${session.id}"))
        .withEntity(UrlForm(fields.toSeq*))

      val res = routes.orNotFound.run(req).unsafeRunSync()
      assertEquals(res.status, Status.SeeOther)

      val savedEvent = store.findByName("Field Day")
      assert(savedEvent.isDefined)
      assertEquals(savedEvent.get.ics205.incidentName, "Field Day Updated")
      assertEquals(savedEvent.get.ics205.specialInstructions, "Check repeaters")
      assertEquals(savedEvent.get.metadata.lastEditedBy, Some(admin.id))
    }

  test("POST / saves plan when event has different ID from event name"):
    withTestContext { (_, store, userStore, sessionStore, _, routes) =>
      val passwordService = new ScalaPassPasswordService
      val admin = userStore.add(User("admin", passwordService.hash("pass"), Role.Admin, enabled = true, id = "u-admin")).toOption.get
      val session = sessionStore.create(admin.id)

      val originalPlan = Ics205(incidentName = "Custom Event", operationalPeriod = OperationalPeriod(), channels = Seq.empty)
      val customEvent = Ics205Event(
        id = "custom-uuid-12345",
        ics205 = originalPlan,
        metadata = Ics205Metadata()
      )
      store.save(customEvent)

      val fields = Ics205Form.fields(originalPlan)
        .updated("eventId", "custom-uuid-12345")
        .updated("eventName", "Custom Event")
        .updated("incidentName", "Custom Event Modified")

      val req = Request[IO](Method.POST, Uri.unsafeFromString("/?event=custom-uuid-12345"))
        .putHeaders(org.http4s.Header.Raw(CIString("Cookie"), s"session=${session.id}"))
        .withEntity(UrlForm(fields.toSeq*))

      val res = routes.orNotFound.run(req).unsafeRunSync()
      assertEquals(res.status, Status.SeeOther)
      assertEquals(res.headers.get(CIString("Location")).map(_.head.value), Some("/?saved=1&event=custom-uuid-12345"))

      val savedEvent = store.getEvent("custom-uuid-12345")
      assert(savedEvent.isDefined)
      assertEquals(savedEvent.get.ics205.incidentName, "Custom Event Modified")
    }

  test("GET /?event=<id> renders editor referencing event by id"):
    withTestContext { (_, store, userStore, sessionStore, _, routes) =>
      val passwordService = new ScalaPassPasswordService
      val admin = userStore.add(User("admin", passwordService.hash("pass"), Role.Admin, enabled = true, id = "u-admin")).toOption.get
      val session = sessionStore.create(admin.id)

      val plan = Ics205(incidentName = "Special Drill", operationalPeriod = OperationalPeriod(), channels = Seq.empty)
      val event = Ics205Event(
        id = "ev-drill-999",
        ics205 = plan,
        metadata = Ics205Metadata()
      )
      store.save(event)

      val req = Request[IO](Method.GET, Uri.unsafeFromString("/?event=ev-drill-999"))
        .putHeaders(org.http4s.Header.Raw(CIString("Cookie"), s"session=${session.id}"))

      val res = routes.orNotFound.run(req).unsafeRunSync()
      assertEquals(res.status, Status.Ok)
      val html = res.as[String].unsafeRunSync()
      assert(html.contains("action=\"/?event=ev-drill-999\""))
      assert(html.contains("name=\"eventId\" value=\"ev-drill-999\""))
    }
