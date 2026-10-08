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

package ics205.web.auth

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import ics205.auth.*
import ics205.store.{InMemJsonSessionStore, SessionStore, UserStore}
import ics205.util.{FileHelper, LoggingConfig}
import ics205.web.admin.UserAdminEndpoints
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property
import org.apache.logging.log4j.core.{LogEvent, LoggerContext}
import org.apache.logging.log4j.{Level, LogManager}
import org.http4s.{Header, Method, Request, Status, Uri, UrlForm}
import org.typelevel.ci.CIString
import sttp.tapir.server.http4s.Http4sServerInterpreter

import scala.collection.mutable.ListBuffer

class AuthLoggingTests extends munit.FunSuite:

  class TestLogAppender extends AbstractAppender("AuthLoggingTestAppender", null, null, true, Property.EMPTY_ARRAY):
    val events = ListBuffer[LogEvent]()
    override def append(event: LogEvent): Unit =
      events += event.toImmutable

  private def withContext(
    test: (UserStore, SessionStore, PasswordService, AuthenticationService, AuthEndpoints, UserAdminEndpoints, org.http4s.HttpApp[IO], ListBuffer[LogEvent]) => Unit
  ): Unit =
    val tempDir = os.temp.dir()
    val ctx = LoggingConfig.init(tempDir)
    val config = ctx.getConfiguration
    val appender = new TestLogAppender()
    appender.start()
    config.addAppender(appender)
    config.getRootLogger.addAppender(appender, Level.INFO, null)
    ctx.updateLoggers()
    try
      val helper = new FileHelper(tempDir)
      val authConfig = AuthConfig()
      val userStore = new UserStore(helper, authConfig)
      val sessionStore = new InMemJsonSessionStore(helper, authConfig)
      val passwordService = new ScalaPassPasswordService()
      val authService = new AuthenticationService(userStore, passwordService, sessionStore)
      val security = new AuthSecurity(authService, authConfig)
      val authEndpoints = new AuthEndpoints(authService, sessionStore, security, authConfig, userStore, passwordService)
      val adminEndpoints = new UserAdminEndpoints(userStore, sessionStore, passwordService, security)
      val httpApp = Http4sServerInterpreter[IO]().toRoutes(authEndpoints.endpoints ++ adminEndpoints.endpoints).orNotFound

      test(userStore, sessionStore, passwordService, authService, authEndpoints, adminEndpoints, httpApp, appender.events)
    finally
      config.getRootLogger.removeAppender("AuthLoggingTestAppender")
      ctx.updateLoggers()
      appender.stop()
      os.remove.all(tempDir)

  test("Login success is logged at INFO level with username, role, and source IP"):
    withContext { (userStore, sessionStore, passwordService, _, _, _, app, events) =>
      val hash = passwordService.hash("validpassword123")
      userStore.add(User("alice", hash, Role.Admin, enabled = true, id = "u-alice"))
      events.clear()

      // Form login with X-Forwarded-For IP
      val form = UrlForm("username" -> "alice", "password" -> "validpassword123", "redirect" -> "/editor")
      val req = Request[IO](Method.POST, Uri.unsafeFromString("/login"))
        .withEntity(form)
        .putHeaders(
          Header.Raw(CIString("X-Forwarded-For"), "192.168.1.100"),
          Header.Raw(CIString("User-Agent"), "TestBrowser/1.0")
        )
      val res = app.run(req).unsafeRunSync()
      assertEquals(res.status, Status.SeeOther)

      val loginSuccessEvents = events.filter(e => e.getLevel == Level.INFO && e.getMessage.getFormattedMessage.contains("Login successful"))
      assert(loginSuccessEvents.nonEmpty, "Expected INFO login successful log event")
      val msg = loginSuccessEvents.last.getMessage.getFormattedMessage
      assert(msg.contains("alice"), s"Expected username alice in: $msg")
      assert(msg.contains("192.168.1.100"), s"Expected IP 192.168.1.100 in: $msg")
      assert(msg.contains("TestBrowser/1.0"), s"Expected User-Agent in: $msg")
    }

  test("Login error is logged at INFO level with source IP and failure reason without exposing password"):
    withContext { (userStore, sessionStore, passwordService, _, _, _, app, events) =>
      val hash = passwordService.hash("validpassword123")
      userStore.add(User("alice", hash, Role.Admin, enabled = true, id = "u-alice"))
      userStore.add(User("disabled_bob", hash, Role.User, enabled = false, id = "u-bob"))
      events.clear()

      val sensitiveSecret = "secret_plaintext_pass_999"

      // 1. Nonexistent user
      val req1 = Request[IO](Method.POST, Uri.unsafeFromString("/login"))
        .withEntity(UrlForm("username" -> "unknown_user", "password" -> sensitiveSecret))
        .putHeaders(Header.Raw(CIString("X-Forwarded-For"), "10.0.0.1"), Header.Raw(CIString("User-Agent"), "AgentX"))
      val res1 = app.run(req1).unsafeRunSync()
      assertEquals(res1.status, Status.SeeOther)

      val errEvent1 = events.find(e => e.getLevel == Level.INFO && e.getMessage.getFormattedMessage.contains("Login failed") && e.getMessage.getFormattedMessage.contains("unknown_user"))
      assert(errEvent1.isDefined, "Expected login failed log for unknown_user")
      val msg1 = errEvent1.get.getMessage.getFormattedMessage
      assert(msg1.contains("10.0.0.1"), s"Expected IP in: $msg1")
      assert(msg1.contains("User does not exist"), s"Expected reason in: $msg1")
      assert(!msg1.contains(sensitiveSecret), "Password must not be exposed in logs")

      // 2. Disabled user
      events.clear()
      val req2 = Request[IO](Method.POST, Uri.unsafeFromString("/login"))
        .withEntity(UrlForm("username" -> "disabled_bob", "password" -> sensitiveSecret))
        .putHeaders(Header.Raw(CIString("X-Forwarded-For"), "10.0.0.2"))
      val res2 = app.run(req2).unsafeRunSync()
      assertEquals(res2.status, Status.SeeOther)

      val errEvent2 = events.find(e => e.getLevel == Level.INFO && e.getMessage.getFormattedMessage.contains("Login failed") && e.getMessage.getFormattedMessage.contains("disabled_bob"))
      assert(errEvent2.isDefined, "Expected login failed log for disabled user")
      val msg2 = errEvent2.get.getMessage.getFormattedMessage
      assert(msg2.contains("10.0.0.2"), s"Expected IP in: $msg2")
      assert(msg2.contains("disabled"), s"Expected disabled reason in: $msg2")
      assert(!msg2.contains(sensitiveSecret), "Password must not be exposed in logs")

      // 3. Incorrect password
      events.clear()
      val req3 = Request[IO](Method.POST, Uri.unsafeFromString("/login"))
        .withEntity(UrlForm("username" -> "alice", "password" -> "wrong_password"))
        .putHeaders(Header.Raw(CIString("X-Forwarded-For"), "10.0.0.3"))
      val res3 = app.run(req3).unsafeRunSync()
      assertEquals(res3.status, Status.SeeOther)

      val errEvent3 = events.find(e => e.getLevel == Level.INFO && e.getMessage.getFormattedMessage.contains("Login failed") && e.getMessage.getFormattedMessage.contains("alice"))
      assert(errEvent3.isDefined, "Expected login failed log for wrong password")
      val msg3 = errEvent3.get.getMessage.getFormattedMessage
      assert(msg3.contains("10.0.0.3"), s"Expected IP in: $msg3")
      assert(msg3.contains("Incorrect password"), s"Expected incorrect password reason in: $msg3")
      assert(!msg3.contains("wrong_password"), "Password must not be exposed in logs")

      // 4. Invalid JSON body
      events.clear()
      val req4 = Request[IO](Method.POST, Uri.unsafeFromString("/login"))
        .withEntity("{malformed_json")
        .putHeaders(
          Header.Raw(CIString("Content-Type"), "application/json"),
          Header.Raw(CIString("X-Forwarded-For"), "10.0.0.4")
        )
      val res4 = app.run(req4).unsafeRunSync()
      assertEquals(res4.status, Status.BadRequest)

      val errEvent4 = events.find(e => e.getLevel == Level.INFO && e.getMessage.getFormattedMessage.contains("Login failed") && e.getMessage.getFormattedMessage.contains("Invalid JSON"))
      assert(errEvent4.isDefined, "Expected login failed log for invalid JSON")
      val msg4 = errEvent4.get.getMessage.getFormattedMessage
      assert(msg4.contains("10.0.0.4"), s"Expected IP in: $msg4")
    }

  test("Add user is logged at INFO level for both initial setup and admin creation"):
    withContext { (userStore, sessionStore, passwordService, _, _, _, app, events) =>
      events.clear()

      // Initial admin creation
      val formInit = UrlForm("username" -> "initialadmin", "password" -> "password123", "confirmPassword" -> "password123")
      val reqInit = Request[IO](Method.POST, Uri.unsafeFromString("/admin/users/create"))
        .withEntity(formInit)
        .putHeaders(Header.Raw(CIString("X-Forwarded-For"), "172.16.0.10"))
      val resInit = app.run(reqInit).unsafeRunSync()
      assertEquals(resInit.status, Status.SeeOther)

      val initialAddEvents = events.filter(e => e.getLevel == Level.INFO && e.getMessage.getFormattedMessage.contains("initialadmin"))
      assert(initialAddEvents.nonEmpty, "Expected initial user creation log")
      assert(initialAddEvents.exists(_.getMessage.getFormattedMessage.contains("172.16.0.10")))

      // Admin adds a second user
      val adminUser = userStore.findByUsername("initialadmin").get
      val session = sessionStore.create(adminUser.id)
      events.clear()

      val formAdd = UrlForm("username" -> "operator1", "password" -> "pass123456", "confirmPassword" -> "pass123456", "role" -> "editor", "enabled" -> "true")
      val reqAdd = Request[IO](Method.POST, Uri.unsafeFromString("/admin/users/create"))
        .withEntity(formAdd)
        .putHeaders(
          Header.Raw(CIString("Cookie"), s"session=${session.id}"),
          Header.Raw(CIString("X-Forwarded-For"), "172.16.0.20")
        )
      val resAdd = app.run(reqAdd).unsafeRunSync()
      assertEquals(resAdd.status, Status.SeeOther)

      val adminAddEvents = events.filter(e => e.getLevel == Level.INFO && e.getMessage.getFormattedMessage.contains("operator1"))
      assert(adminAddEvents.nonEmpty, "Expected admin user creation log")
      assert(adminAddEvents.exists(e => e.getMessage.getFormattedMessage.contains("initialadmin") && e.getMessage.getFormattedMessage.contains("operator1")))
    }

  test("Remove user is logged at INFO level"):
    withContext { (userStore, sessionStore, passwordService, _, _, _, app, events) =>
      val hash = passwordService.hash("password123")
      userStore.add(User("admin1", hash, Role.Admin, enabled = true, id = "u-admin"))
      userStore.add(User("targetuser", hash, Role.User, enabled = true, id = "u-target"))
      val session = sessionStore.create("u-admin")
      events.clear()

      val formDel = UrlForm("id" -> "u-target")
      val reqDel = Request[IO](Method.POST, Uri.unsafeFromString("/admin/users/delete"))
        .withEntity(formDel)
        .putHeaders(
          Header.Raw(CIString("Cookie"), s"session=${session.id}"),
          Header.Raw(CIString("X-Forwarded-For"), "10.1.2.3")
        )
      val resDel = app.run(reqDel).unsafeRunSync()
      assertEquals(resDel.status, Status.SeeOther)

      val deleteEvents = events.filter(e => e.getLevel == Level.INFO && e.getMessage.getFormattedMessage.contains("deleted") && e.getMessage.getFormattedMessage.contains("targetuser"))
      assert(deleteEvents.nonEmpty, "Expected delete user log event")
      assert(deleteEvents.exists(_.getMessage.getFormattedMessage.contains("admin1")), "Expected admin name in delete log")
      assert(deleteEvents.exists(_.getMessage.getFormattedMessage.contains("10.1.2.3")), "Expected IP in delete log")
    }

  test("User changes own password is logged at INFO level"):
    withContext { (userStore, sessionStore, passwordService, _, _, _, app, events) =>
      val hash = passwordService.hash("oldpassword123")
      userStore.add(User("charlie", hash, Role.User, enabled = true, id = "u-charlie"))
      val session = sessionStore.create("u-charlie")
      events.clear()

      val formChange = UrlForm("currentPassword" -> "oldpassword123", "newPassword" -> "newpassword123", "confirmPassword" -> "newpassword123")
      val reqChange = Request[IO](Method.POST, Uri.unsafeFromString("/change-password"))
        .withEntity(formChange)
        .putHeaders(
          Header.Raw(CIString("Cookie"), s"session=${session.id}"),
          Header.Raw(CIString("X-Forwarded-For"), "192.168.5.55")
        )
      val resChange = app.run(reqChange).unsafeRunSync()
      assertEquals(resChange.status, Status.SeeOther)

      val changePassEvents = events.filter(e => e.getLevel == Level.INFO && e.getMessage.getFormattedMessage.contains("charlie") && e.getMessage.getFormattedMessage.contains("changed their password"))
      assert(changePassEvents.nonEmpty, "Expected password change log event")
      val msg = changePassEvents.last.getMessage.getFormattedMessage
      assert(msg.contains("192.168.5.55"), s"Expected IP in: $msg")
      assert(!msg.contains("newpassword123") && !msg.contains("oldpassword123"), "Actual passwords must not be exposed")
    }

  test("Admin changes user record logs changed fields including password changed flag without revealing password"):
    withContext { (userStore, sessionStore, passwordService, _, _, _, app, events) =>
      val hash = passwordService.hash("originalpass123")
      userStore.add(User("admin1", hash, Role.Admin, enabled = true, id = "u-admin"))
      userStore.add(User("member1", hash, Role.User, enabled = true, id = "u-member"))
      val session = sessionStore.create("u-admin")
      events.clear()

      val newSecret = "verysecretnewpassword123"

      // Admin updates role and password
      val formEdit = UrlForm(
        "id" -> "u-member",
        "username" -> "member1_renamed",
        "password" -> newSecret,
        "confirmPassword" -> newSecret,
        "role" -> "editor",
        "enabled" -> "true"
      )
      val reqEdit = Request[IO](Method.POST, Uri.unsafeFromString("/admin/users/edit"))
        .withEntity(formEdit)
        .putHeaders(
          Header.Raw(CIString("Cookie"), s"session=${session.id}"),
          Header.Raw(CIString("X-Forwarded-For"), "10.99.88.77")
        )
      val resEdit = app.run(reqEdit).unsafeRunSync()
      assertEquals(resEdit.status, Status.SeeOther)

      val updateEvents = events.filter(e => e.getLevel == Level.INFO && e.getMessage.getFormattedMessage.contains("updated user"))
      assert(updateEvents.nonEmpty, "Expected admin update user log event")
      val msg = updateEvents.last.getMessage.getFormattedMessage

      assert(msg.contains("admin1"), s"Expected admin name in: $msg")
      assert(msg.contains("10.99.88.77"), s"Expected IP in: $msg")
      assert(msg.contains("username"), s"Expected username change in: $msg")
      assert(msg.contains("role"), s"Expected role change in: $msg")
      assert(msg.contains("password: changed"), s"Expected 'password: changed' in: $msg")
      assert(!msg.contains(newSecret), "Actual new password must not be exposed in log")

      // Now test editing without changing password
      events.clear()
      val formEditNoPass = UrlForm(
        "id" -> "u-member",
        "username" -> "member1_renamed",
        "password" -> "",
        "confirmPassword" -> "",
        "role" -> "admin",
        "enabled" -> "false"
      )
      val reqEditNoPass = Request[IO](Method.POST, Uri.unsafeFromString("/admin/users/edit"))
        .withEntity(formEditNoPass)
        .putHeaders(
          Header.Raw(CIString("Cookie"), s"session=${session.id}"),
          Header.Raw(CIString("X-Forwarded-For"), "10.99.88.77")
        )
      val resEditNoPass = app.run(reqEditNoPass).unsafeRunSync()
      assertEquals(resEditNoPass.status, Status.SeeOther)

      val updateEvents2 = events.filter(e => e.getLevel == Level.INFO && e.getMessage.getFormattedMessage.contains("updated user"))
      assert(updateEvents2.nonEmpty, "Expected admin update user log event")
      val msg2 = updateEvents2.last.getMessage.getFormattedMessage
      assert(!msg2.contains("password: changed"), s"Password should not be listed as changed in: $msg2")
      assert(msg2.contains("role"), s"Expected role change in: $msg2")
      assert(msg2.contains("enabled"), s"Expected enabled change in: $msg2")
    }
