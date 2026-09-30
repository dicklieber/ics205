package ics205.web

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import ics205.auth.*
import ics205.model.*
import ics205.store.{Ics205Store, InMemJsonSessionStore, UserStore}
import ics205.util.FileHelper
import org.http4s.{Method, Request, Status, Uri, UrlForm}
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
      val endpoints = new IndexEndpoints(store, authService, userStore, config)
      val routes = Http4sServerInterpreter[IO]().toRoutes(endpoints.endpoints)

      test(tempDir, store, userStore, sessionStore, authService, routes)
    finally
      os.remove.all(tempDir)

  test("GET /events requires authentication"):
    withTestContext { (_, _, _, _, _, routes) =>
      val req = Request[IO](Method.GET, Uri.unsafeFromString("/events"))
      val res = routes.orNotFound.run(req).unsafeRunSync()
      assertEquals(res.status, Status.SeeOther)
      assertEquals(res.headers.get(org.typelevel.ci.CIString("Location")).map(_.head.value), Some("/login"))
    }

  test("GET /events lists visible events for authenticated user"):
    withTestContext { (_, store, userStore, sessionStore, _, routes) =>
      val passwordService = new ScalaPassPasswordService
      val admin = userStore.add(User("admin", passwordService.hash("pass"), RolePermissions.Admin, enabled = true, id = "u-admin")).toOption.get
      val session = sessionStore.create(admin.id)

      store.saveEvent(Ics205Event("Field Day", Ics205(incidentName = "Field Day 2026", operationalPeriod = OperationalPeriod(), channels = Seq.empty)))
      store.saveEvent(Ics205Event("Marathon", Ics205(incidentName = "City Marathon", operationalPeriod = OperationalPeriod(), channels = Seq.empty)))

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

  test("POST /events/create creates new event and sets cookie"):
    withTestContext { (_, store, userStore, sessionStore, _, routes) =>
      val passwordService = new ScalaPassPasswordService
      val admin = userStore.add(User("admin", passwordService.hash("pass"), RolePermissions.Admin, enabled = true, id = "u-admin")).toOption.get
      val session = sessionStore.create(admin.id)

      val req = Request[IO](Method.POST, Uri.unsafeFromString("/events/create"))
        .putHeaders(org.http4s.Header.Raw(org.typelevel.ci.CIString("Cookie"), s"session=${session.id}"))
        .withEntity(UrlForm("eventName" -> "Winter Drill", "incidentName" -> "Winter Drill 2026"))

      val res = routes.orNotFound.run(req).unsafeRunSync()
      assertEquals(res.status, Status.SeeOther)
      val setCookie = res.headers.get(org.typelevel.ci.CIString("Set-Cookie")).map(_.head.value).getOrElse("")
      assert(setCookie.contains("ics205_event=Winter+Drill"))

      val created = store.getEvent("Winter Drill")
      assert(created.isDefined)
      assertEquals(created.get.ics205.incidentName, "Winter Drill 2026")
    }

  test("GET /events/select selects active event and sets cookie"):
    withTestContext { (_, store, userStore, sessionStore, _, routes) =>
      val passwordService = new ScalaPassPasswordService
      val admin = userStore.add(User("admin", passwordService.hash("pass"), RolePermissions.Admin, enabled = true, id = "u-admin")).toOption.get
      val session = sessionStore.create(admin.id)

      store.saveEvent(Ics205Event("Campout", Ics205(incidentName = "Scout Campout", operationalPeriod = OperationalPeriod(), channels = Seq.empty)))

      val req = Request[IO](Method.GET, Uri.unsafeFromString("/events/select?name=Campout&returnUrl=/radio"))
        .putHeaders(org.http4s.Header.Raw(org.typelevel.ci.CIString("Cookie"), s"session=${session.id}"))

      val res = routes.orNotFound.run(req).unsafeRunSync()
      assertEquals(res.status, Status.SeeOther)
      assertEquals(res.headers.get(org.typelevel.ci.CIString("Location")).map(_.head.value), Some("/radio"))
      val setCookie = res.headers.get(org.typelevel.ci.CIString("Set-Cookie")).map(_.head.value).getOrElse("")
      assert(setCookie.contains("ics205_event=Campout"))
      assertEquals(store.currentEventName, Some("Campout"))
    }

  test("GET and POST /events/metadata manages event permissions"):
    withTestContext { (_, store, userStore, sessionStore, _, routes) =>
      val passwordService = new ScalaPassPasswordService
      val admin = userStore.add(User("admin", passwordService.hash("pass"), RolePermissions.Admin, enabled = true, id = "u-admin")).toOption.get
      val operator = userStore.add(User("operator", passwordService.hash("pass"), RolePermissions.Viewer, enabled = true, id = "u-op")).toOption.get
      val session = sessionStore.create(admin.id)

      store.saveEvent(Ics205Event("Airshow", Ics205(incidentName = "Annual Airshow", operationalPeriod = OperationalPeriod(), channels = Seq.empty)))

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
      val admin = userStore.add(User("admin", passwordService.hash("pass"), RolePermissions.Admin, enabled = true, id = "u-admin")).toOption.get
      val session = sessionStore.create(admin.id)

      store.saveEvent(Ics205Event("OldEvent", Ics205(incidentName = "Old Incident", operationalPeriod = OperationalPeriod(), channels = Seq.empty)))

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
      assertEquals(renamed.get.eventName, "NewEvent")
      assertEquals(renamed.get.ics205.incidentName, "New Incident")
    }

  test("Event authorization isolates permissions across multiple events"):
    withTestContext { (_, store, userStore, sessionStore, _, routes) =>
      val passwordService = new ScalaPassPasswordService
      val userViewer = userStore.add(User("viewerUser", passwordService.hash("pass"), RolePermissions.Viewer, enabled = true, id = "u-v")).toOption.get
      val session = sessionStore.create(userViewer.id)

      // Event 1 has explicit EditPlans for viewerUser
      val event1 = Ics205Event("AllowedEvent", Ics205(incidentName = "Allowed Incident", operationalPeriod = OperationalPeriod(), channels = Seq.empty),
        Ics205Metadata(permissions = Map(userViewer.id -> Permission.EditPlans)))
      // Event 2 has explicit ViewPlans for other users only
      val event2 = Ics205Event("RestrictedEvent", Ics205(incidentName = "Restricted Incident", operationalPeriod = OperationalPeriod(), channels = Seq.empty),
        Ics205Metadata(permissions = Map("other-user" -> Permission.EditPlans)))

      store.saveEvent(event1)
      store.saveEvent(event2)

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
      val admin = userStore.add(User("admin", passwordService.hash("pass"), RolePermissions.Admin, enabled = true, id = "u-admin")).toOption.get
      val session = sessionStore.create(admin.id)

      store.saveEvent(Ics205Event("ToDelete", Ics205(incidentName = "To Delete", operationalPeriod = OperationalPeriod(), channels = Seq.empty)))
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
      val admin = userStore.add(User("admin", passwordService.hash("pass"), RolePermissions.Admin, enabled = true, id = "u-admin")).toOption.get
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
