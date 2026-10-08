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

package ics205.web

import cats.effect.IO
import ics205.auth.{AuthConfig, AuthenticatedUser, AuthenticationService, Permission, Role}
import ics205.log.Ics205ActivityLogger
import ics205.model.{Ics205, Ics205Event, Ics205Metadata, OperationalPeriod}
import ics205.store.{Ics205Store, SessionStore, UserStore}
import ics205.util.Ids
import ics205.web.auth.AuthSecurity
import io.circe.syntax.*
import jakarta.inject.{Inject, Singleton}
import sttp.model.{Part, StatusCode}
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.server.ServerEndpoint

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant

case class EventImportData(
  file: Part[Array[Byte]]
)

@Singleton
class IndexEndpoints @Inject() (
  val store: Ics205Store,
  security: AuthSecurity,
  userStore: UserStore,
  sessionStore: SessionStore
) extends ApiEndpoints with EventResolving:

  def this(store: Ics205Store, authService: AuthenticationService, userStore: UserStore, config: AuthConfig, sessionStore: SessionStore) =
    this(store, new AuthSecurity(authService, config), userStore, sessionStore)

  def this(store: Ics205Store, authService: AuthenticationService, userStore: UserStore, sessionStore: SessionStore) =
    this(store, new AuthSecurity(authService, AuthConfig()), userStore, sessionStore)

  def this(store: Ics205Store, authService: AuthenticationService, userStore: UserStore, config: AuthConfig) =
    this(
      store,
      new AuthSecurity(authService, config),
      userStore,
      new ics205.store.InMemJsonSessionStore(new ics205.util.FileHelper)
    )

  def this(store: Ics205Store, authService: AuthenticationService, config: AuthConfig) =
    this(
      store,
      new AuthSecurity(authService, config),
      new ics205.store.UserStore(new ics205.util.FileHelper),
      new ics205.store.InMemJsonSessionStore(new ics205.util.FileHelper)
    )

  def this(store: Ics205Store) = this(
    store,
    new AuthSecurity(
      new AuthenticationService(
        new ics205.store.UserStore(new ics205.util.FileHelper),
        new ics205.auth.ScalaPassPasswordService,
        new ics205.store.InMemJsonSessionStore(new ics205.util.FileHelper)
      ),
      ics205.auth.AuthConfig()
    ),
    new ics205.store.UserStore(new ics205.util.FileHelper),
    new ics205.store.InMemJsonSessionStore(new ics205.util.FileHelper)
  )

  private def encode(s: String): String = URLEncoder.encode(s, StandardCharsets.UTF_8.toString)

  private val indexEndpoint: ServerEndpoint[Any, IO] = endpoint
    .get
    .in("")
    .in(cookie[Option[String]](security.config.cookieName))
    .in(query[Option[String]]("event"))
    .in(query[Option[String]]("saved"))
    .out(statusCode.and(header[Option[String]]("Location")).and(htmlBodyUtf8))
    .serverLogicSuccess { (sessionIdOpt, eventQueryOpt, saved) =>
      IO.blocking {
        sessionIdOpt.flatMap(sid => security.authService.authenticateSession(sid).toOption) match
          case None =>
            (StatusCode.SeeOther, Some("/login"), "")
          case Some(user) =>
            val (currentEventOpt, authorizedEvents) = resolveEvent(eventQueryOpt, user)
            currentEventOpt match
              case None =>
                (StatusCode.SeeOther, Some("/events"), "")
              case Some(currentEvent) =>
                if !currentEvent.canView(user) && user.role != Role.Admin then
                  (StatusCode.Forbidden, None, "You do not have permission to view this event.")
                else if eventQueryOpt.exists(_ != currentEvent.id) then
                  val savedParam = if saved.contains("1") then "&saved=1" else ""
                  (StatusCode.SeeOther, Some(s"/?event=${encode(currentEvent.id)}$savedParam"), "")
                else
                  (
                    StatusCode.Ok,
                    None,
                    Ics205Editor.render(
                      plan = currentEvent.ics205,
                      saved = saved.contains("1"),
                      currentUser = Some(user),
                      metadata = Some(currentEvent.metadata),
                      currentEventName = Some(currentEvent.eventName),
                      currentEventId = Some(currentEvent.id),
                      availableEvents = authorizedEvents.map(_.eventName)
                    )
                  )
      }
    }

  private val saveEndpoint: ServerEndpoint[Any, IO] = security.secureEndpoint
    .post
    .in("")
    .in(query[Option[String]]("event"))
    .in(formBody[Map[String, String]])
    .out(statusCode.and(header[Option[String]]("Location")).and(htmlBodyUtf8))
    .serverLogicSuccess { user => (eventQueryOpt, data) =>
      IO.blocking {
        val targetName = data.get("eventId").filter(_.nonEmpty)
          .orElse(data.get("eventName").filter(_.nonEmpty))
          .orElse(eventQueryOpt.filter(_.nonEmpty))
        val (currentEventOpt, authorizedEvents) = resolveEvent(targetName, user)

        currentEventOpt match
          case None =>
            val eventName = targetName.getOrElse(data.getOrElse("incidentName", "Default Event")).trim
            val effectiveName = if eventName.isEmpty then "Default Event" else eventName
            Ics205Form.decode(data, Ics205(incidentName = effectiveName, operationalPeriod = OperationalPeriod(), channels = Seq.empty)) match
              case Left(message) =>
                (StatusCode.SeeOther, Some("/events"), "")
              case Right(plan) =>
                try
                  val newEvent = Ics205Event(
                    id = Ids.generateId(),
                    ics205 = plan,
                    metadata = Ics205Metadata()
                  )
                  store.save(newEvent, user)
                  sessionStore.save(user.session.copy(currentIcs205 = Some(newEvent.id)))
                  Ics205ActivityLogger.logUpdate(
                    username = user.user.username,
                    eventName = newEvent.eventName,
                    incidentName = Option(plan.incidentName).filter(_.nonEmpty),
                    channelCount = Some(plan.channels.size),
                    action = Some("save")
                  )
                  val redirectUrl = s"/?saved=1&event=${encode(newEvent.id)}"
                  (StatusCode.SeeOther, Some(redirectUrl), "")
                catch
                  case _: java.io.IOException =>
                    (
                      StatusCode.InternalServerError,
                      None,
                      Ics205Editor.render(
                        plan = plan,
                        submitted = Some(data),
                        error = Some("The plan could not be saved. Check that the data directory is writable and try again."),
                        currentUser = Some(user),
                        metadata = Some(Ics205Metadata()),
                        currentEventName = Some(effectiveName),
                        currentEventId = Some(effectiveName),
                        availableEvents = authorizedEvents.map(_.eventName)
                      )
                    )
          case Some(currentEvent) =>
            if !currentEvent.canEdit(user) && user.role != Role.Admin then
              (
                StatusCode.Forbidden,
                None,
                Ics205Editor.render(
                  plan = currentEvent.ics205,
                  error = Some("You do not have permission to edit this plan."),
                  currentUser = Some(user),
                  metadata = Some(currentEvent.metadata),
                  currentEventName = Some(currentEvent.eventName),
                  currentEventId = Some(currentEvent.id),
                  availableEvents = authorizedEvents.map(_.eventName)
                )
              )
            else
              Ics205Form.decode(data, currentEvent.ics205) match
                case Left(message) =>
                  (
                    StatusCode.UnprocessableEntity,
                    None,
                    Ics205Editor.render(
                      plan = currentEvent.ics205,
                      submitted = Some(data),
                      error = Some(message),
                      currentUser = Some(user),
                      metadata = Some(currentEvent.metadata),
                      currentEventName = Some(currentEvent.eventName),
                      currentEventId = Some(currentEvent.id),
                      availableEvents = authorizedEvents.map(_.eventName)
                    )
                  )
                case Right(plan) =>
                  try
                    val updatedEvent = currentEvent.copy(ics205 = plan)
                    store.save(updatedEvent, user)
                    Ics205ActivityLogger.logUpdate(
                      username = user.user.username,
                      eventName = updatedEvent.eventName,
                      incidentName = Option(plan.incidentName).filter(_.nonEmpty),
                      channelCount = Some(plan.channels.size),
                      action = Some("save")
                    )
                    val redirectUrl = s"/?saved=1&event=${encode(currentEvent.id)}"
                    (StatusCode.SeeOther, Some(redirectUrl), "")
                  catch
                    case _: java.io.IOException =>
                      (
                        StatusCode.InternalServerError,
                        None,
                        Ics205Editor.render(
                          plan = currentEvent.ics205,
                          submitted = Some(data),
                          error = Some("The plan could not be saved. Check that the data directory is writable and try again."),
                          currentUser = Some(user),
                          metadata = Some(currentEvent.metadata),
                          currentEventName = Some(currentEvent.eventName),
                          currentEventId = Some(currentEvent.id),
                          availableEvents = authorizedEvents.map(_.eventName)
                        )
                      )
      }
    }

  private val previewEndpoint: ServerEndpoint[Any, IO] = security.secureEndpoint
    .post
    .in("preview")
    .in(query[Option[String]]("event"))
    .in(formBody[Map[String, String]])
    .out(statusCode.and(header[Option[String]]("Location")).and(htmlBodyUtf8))
    .serverLogicSuccess { user => (eventQueryOpt, data) =>
      IO.blocking {
        val targetName = data.get("eventId").filter(_.nonEmpty)
          .orElse(data.get("eventName").filter(_.nonEmpty))
          .orElse(eventQueryOpt.filter(_.nonEmpty))
        val (currentEventOpt, _) = resolveEvent(targetName, user)

        currentEventOpt match
          case None =>
            (StatusCode.SeeOther, Some("/events"), "")
          case Some(currentEvent) =>
            Ics205Form.decode(data, currentEvent.ics205) match
              case Left(message) =>
                val (_, authorizedEvents) = resolveEvent(targetName, user)
                (
                  StatusCode.UnprocessableEntity,
                  None,
                  Ics205Editor.render(
                    plan = currentEvent.ics205,
                    submitted = Some(data),
                    error = Some(message),
                    currentUser = Some(user),
                    metadata = Some(currentEvent.metadata),
                    currentEventName = Some(currentEvent.eventName),
                    currentEventId = Some(currentEvent.id),
                    availableEvents = authorizedEvents.map(_.eventName)
                  )
                )
              case Right(plan) =>
                (StatusCode.Ok, None, Ics205Page.renderPrintable(plan))
      }
    }

  private val radioEndpoint: ServerEndpoint[Any, IO] = security.secureEndpoint
    .get
    .in("radio")
    .in(query[Option[String]]("event"))
    .out(statusCode.and(header[Option[String]]("Location")).and(htmlBodyUtf8))
    .serverLogicSuccess { user => eventQueryOpt =>
      IO.blocking {
        val (currentEventOpt, authorizedEvents) = resolveEvent(eventQueryOpt, user)
        currentEventOpt match
          case None =>
            (StatusCode.SeeOther, Some("/events"), "")
          case Some(currentEvent) =>
            if !currentEvent.canView(user) && user.role != Role.Admin then
              (StatusCode.Forbidden, None, "You do not have permission to view this radio plan.")
            else
              (
                StatusCode.Ok,
                None,
                RadioPage.render(
                  plan = currentEvent.ics205,
                  currentUser = Some(user),
                  currentEventName = Some(currentEvent.eventName),
                  availableEvents = authorizedEvents.map(_.eventName)
                )
              )
      }
    }

  private val eventsEndpoint: ServerEndpoint[Any, IO] = security.secureEndpoint
    .get
    .in("events")
    .in(query[Option[String]]("msg"))
    .in(query[Option[String]]("err"))
    .out(statusCode.and(header[Option[String]]("Location")).and(htmlBodyUtf8))
    .serverLogicSuccess { user => (msg, err) =>
      IO.blocking {
        val allEvents = store.listEvents()
        val visibleEvents = if user.role == Role.Admin then allEvents else allEvents.filter(_.canView(user))
        val currentEventName = user.session.currentIcs205.flatMap(store.findByName).map(_.eventName).orElse(user.session.currentIcs205)
        val html = EventsPage.render(
          currentUser = user,
          events = visibleEvents,
          currentEventName = currentEventName,
          message = msg,
          error = err
        )
        (StatusCode.Ok, None, html)
      }
    }

  private val selectEventEndpoint: ServerEndpoint[Any, IO] = security.secureEndpoint
    .get
    .in("events" / "select")
    .in(query[Option[String]]("name"))
    .in(query[Option[String]]("returnUrl"))
    .out(statusCode.and(header[String]("Location")).and(htmlBodyUtf8))
    .serverLogicSuccess { user => (nameOpt, returnUrlOpt) =>
      IO.blocking {
        nameOpt match
          case Some(name) =>
            store.findByName(name) match
              case Some(ev) if ev.canView(user) || user.role == Role.Admin =>
                sessionStore.save(user.session.copy(currentIcs205 = Some(ev.id)))
                val target = returnUrlOpt.getOrElse("/")
                (StatusCode.SeeOther, target, "")
              case Some(_) =>
                (StatusCode.Forbidden, "/events?err=Unauthorized", "You do not have permission to access this event.")
              case None =>
                (StatusCode.SeeOther, "/events?err=Event+not+found", "")
          case None =>
            (StatusCode.SeeOther, returnUrlOpt.getOrElse("/"), "")
      }
    }

  private val createEventEndpoint: ServerEndpoint[Any, IO] = security.secureEndpoint
    .post
    .in("events" / "create")
    .in(formBody[Map[String, String]])
    .out(statusCode.and(header[String]("Location")).and(htmlBodyUtf8))
    .serverLogicSuccess { user => formData =>
      IO.blocking {
        if user.role != Role.Admin && !user.hasPermission(Permission.EditPlans) then
          (StatusCode.Forbidden, "/events", "You do not have permission to create events.")
        else
          val eventName = formData.getOrElse("eventName", "").trim
          val incidentName = formData.get("incidentName").map(_.trim).filter(_.nonEmpty).getOrElse(eventName)

          if eventName.isEmpty then
            (StatusCode.SeeOther, "/events?err=Event+name+cannot+be+empty", "")
          else
            val newEvent = Ics205Event(
              id = Ids.generateId(),
              ics205 = Ics205(incidentName = incidentName, operationalPeriod = OperationalPeriod(), channels = Seq.empty),
              metadata = Ics205Metadata()
            )
            store.save(newEvent, user)
            sessionStore.save(user.session.copy(currentIcs205 = Some(newEvent.id)))
            Ics205ActivityLogger.logUpdate(
              username = user.user.username,
              eventName = newEvent.eventName,
              incidentName = Option(newEvent.ics205.incidentName).filter(_.nonEmpty),
              channelCount = Some(newEvent.ics205.channels.size),
              action = Some("create")
            )
            (StatusCode.SeeOther, s"/?event=${encode(newEvent.id)}&saved=1", "")
      }
    }

  private val getMetadataEndpoint: ServerEndpoint[Any, IO] = security.secureEndpoint
    .get
    .in("events" / "metadata")
    .in(query[Option[String]]("name"))
    .in(query[Option[String]]("msg"))
    .in(query[Option[String]]("err"))
    .out(statusCode.and(header[Option[String]]("Location")).and(htmlBodyUtf8))
    .serverLogicSuccess { user => (nameOpt, msg, err) =>
      IO.blocking {
        val targetName = nameOpt.filter(_.nonEmpty).orElse(user.session.currentIcs205).getOrElse("")
        store.findByName(targetName) match
          case None =>
            (StatusCode.SeeOther, Some("/events?err=Event+not+found"), "")
          case Some(ev) =>
            if user.role != Role.Admin && !ev.canEdit(user) then
              (StatusCode.Forbidden, None, "You do not have permission to edit metadata for this event.")
            else
              val allUsers = userStore.all()
              val availableNames = store.listEvents().filter(e => user.role == Role.Admin || e.canView(user)).map(_.eventName)
              val html = EventMetadataPage.render(
                currentUser = user,
                event = ev,
                users = allUsers,
                availableEvents = availableNames,
                message = msg,
                error = err
              )
              (StatusCode.Ok, None, html)
      }
    }

  private val postMetadataEndpoint: ServerEndpoint[Any, IO] = security.secureEndpoint
    .post
    .in("events" / "metadata")
    .in(formBody[Map[String, String]])
    .out(statusCode.and(header[String]("Location")).and(htmlBodyUtf8))
    .serverLogicSuccess { user => formData =>
      IO.blocking {
        val origTarget = formData.get("eventId").filter(_.nonEmpty)
          .orElse(formData.get("originalEventName").filter(_.nonEmpty))
          .orElse(formData.get("eventName").filter(_.nonEmpty))
          .getOrElse("").trim
        val newName = formData.getOrElse("newEventName", origTarget).trim
        val incidentName = formData.getOrElse("incidentName", "").trim

        store.findByName(origTarget) match
          case None =>
            (StatusCode.SeeOther, "/events?err=Event+not+found", "")
          case Some(ev) =>
            if user.role != Role.Admin && !ev.canEdit(user) then
              (StatusCode.Forbidden, "/events", "You do not have permission to edit metadata for this event.")
            else if newName.isEmpty then
              (StatusCode.SeeOther, s"/events/metadata?name=${encode(ev.id)}&err=Event+name+cannot+be+empty", "")
            else
              val allUsers = userStore.all()
              val newPermissions = allUsers.flatMap { u =>
                formData.get(s"perm_${u.id}").flatMap {
                  case "edit" => Some(u.id -> Permission.EditPlans)
                  case "view" => Some(u.id -> Permission.ViewPlans)
                  case _ => None
                }
              }.toMap

              val updatedPlan = ev.ics205.copy(incidentName = if incidentName.nonEmpty then incidentName else newName)
              val updatedMetadata = ev.metadata.copy(
                permissions = newPermissions,
                lastEditedBy = Some(user.user.id),
                savedAt = Instant.now()
              )
              val finalEvent = if !newName.equalsIgnoreCase(origTarget) && ev.id.equalsIgnoreCase(origTarget) then
                store.deleteEvent(origTarget, user)
                ev.copy(id = newName, ics205 = updatedPlan, metadata = updatedMetadata)
              else
                ev.copy(ics205 = updatedPlan, metadata = updatedMetadata)

              store.save(finalEvent, user)
              if !newName.equalsIgnoreCase(origTarget) && user.session.currentIcs205.contains(origTarget) then
                sessionStore.save(user.session.copy(currentIcs205 = Some(finalEvent.id)))

              Ics205ActivityLogger.logUpdate(
                username = user.user.username,
                eventName = finalEvent.eventName,
                incidentName = Option(finalEvent.ics205.incidentName).filter(_.nonEmpty),
                channelCount = Some(finalEvent.ics205.channels.size),
                action = Some("metadata")
              )

              (StatusCode.SeeOther, s"/events/metadata?name=${encode(finalEvent.id)}&msg=Metadata+updated+successfully", "")
      }
    }

  private val exportEventEndpoint: ServerEndpoint[Any, IO] = security.secureEndpoint
    .get
    .in("events" / "export")
    .in(query[Option[String]]("name"))
    .out(statusCode
      .and(header[Option[String]]("Location"))
      .and(header[String]("Content-Type"))
      .and(header[Option[String]]("Content-Disposition"))
      .and(header[String]("Cache-Control"))
      .and(stringBody))
    .serverLogicSuccess { user => nameOpt =>
      IO.blocking {
        val targetName = nameOpt.filter(_.nonEmpty).orElse(user.session.currentIcs205).getOrElse("")
        store.findByName(targetName) match
          case None =>
            (StatusCode.SeeOther, Some("/events?err=Event+not+found"), "text/plain", None, "no-store", "")
          case Some(ev) =>
            if user.role != Role.Admin && !ev.canView(user) then
              (StatusCode.Forbidden, None, "text/plain", None, "no-store", "You do not have permission to export this event.")
            else
              Ics205ActivityLogger.logExport(
                username = user.user.username,
                eventName = ev.eventName,
                format = "json",
                incidentName = Option(ev.ics205.incidentName).filter(_.nonEmpty),
                channelCount = Some(ev.ics205.channels.size)
              )
              val sanitizedName = if ev.eventName.trim.nonEmpty then
                ev.eventName.trim.replaceAll("""[\\/:*?"<>|]""", "_")
              else "ics205"
              (StatusCode.Ok, None, "application/json; charset=utf-8",
                Some(s"""attachment; filename="$sanitizedName.json""""),
                "no-store", ev.asJson.spaces2)
      }
    }

  private val importEventEndpoint: ServerEndpoint[Any, IO] = security.secureEndpoint
    .post
    .in("events" / "import")
    .in(multipartBody[EventImportData])
    .out(statusCode.and(header[String]("Location")).and(htmlBodyUtf8))
    .serverLogicSuccess { user => importData =>
      IO.blocking {
        if user.role != Role.Admin && !user.hasPermission(Permission.EditPlans) then
          (StatusCode.Forbidden, "/events?err=You+do+not+have+permission+to+import+events.", "You do not have permission to import events.")
        else
          val fileBytes = importData.file.body
          val content = new String(fileBytes, StandardCharsets.UTF_8).trim
          if content.isEmpty then
            (StatusCode.SeeOther, "/events?err=Uploaded+file+is+empty", "")
          else
            val parsedResult = io.circe.parser.decode[Ics205Event](content)
              .orElse(io.circe.parser.decode[Ics205](content).map(plan => Ics205Event(plan)))
            parsedResult match
              case Left(err) =>
                (StatusCode.SeeOther, s"/events?err=${encode(s"Failed to parse event JSON: $err")}", "")
              case Right(parsedEvent) =>
                val baseName = parsedEvent.eventName
                val finalName = store.uniqueEventName(baseName)
                val toSave = parsedEvent.copy(id = finalName)
                store.save(toSave, user)
                sessionStore.save(user.session.copy(currentIcs205 = Some(toSave.id)))
                Ics205ActivityLogger.logImport(
                  username = user.user.username,
                  eventName = toSave.eventName,
                  incidentName = Option(toSave.ics205.incidentName).filter(_.nonEmpty),
                  channelCount = Some(toSave.ics205.channels.size),
                  fileName = importData.file.fileName
                )
                (StatusCode.SeeOther, s"/events?msg=Event+'${encode(toSave.eventName)}'+imported+successfully", "")
      }
    }

  private val duplicateEventEndpoint: ServerEndpoint[Any, IO] = security.secureEndpoint
    .post
    .in("events" / "duplicate")
    .in(formBody[Map[String, String]])
    .out(statusCode.and(header[String]("Location")).and(htmlBodyUtf8))
    .serverLogicSuccess { user => formData =>
      IO.blocking {
        val target = formData.get("eventId").filter(_.nonEmpty)
          .orElse(formData.get("eventName").filter(_.nonEmpty))
          .getOrElse("").trim
        store.findByName(target) match
          case None => (StatusCode.SeeOther, "/events?err=Event+not+found", "")
          case Some(ev) if user.role != Role.Admin && !ev.canEdit(user) =>
            (StatusCode.Forbidden, "/events", "You do not have permission to duplicate this event.")
          case Some(ev) =>
            val newName = formData.getOrElse("newEventName", "").trim
            if newName.isEmpty then
              (StatusCode.SeeOther, "/events?err=Event+name+cannot+be+empty", "")
            else if store.findByName(newName).isDefined then
              (StatusCode.SeeOther, s"/events?err=Event+'${encode(newName)}'+already+exists", "")
            else
              val duplicate = ev.copy(
                id = Ids.generateId(),
                ics205 = ev.ics205.copy(incidentName = newName),
                metadata = ev.metadata.copy(lastEditedBy = Some(user.user.id), savedAt = Instant.now())
              )
              store.save(duplicate, user)
              sessionStore.save(user.session.copy(currentIcs205 = Some(duplicate.id)))
              Ics205ActivityLogger.logUpdate(
                username = user.user.username,
                eventName = duplicate.eventName,
                incidentName = Option(duplicate.ics205.incidentName).filter(_.nonEmpty),
                channelCount = Some(duplicate.ics205.channels.size),
                action = Some("duplicate")
              )
              (StatusCode.SeeOther, s"/?event=${encode(duplicate.id)}", "")
      }
    }

  private val deleteEventEndpoint: ServerEndpoint[Any, IO] = security.secureEndpoint
    .post
    .in("events" / "delete")
    .in(formBody[Map[String, String]])
    .out(statusCode.and(header[String]("Location")).and(htmlBodyUtf8))
    .serverLogicSuccess { user => formData =>
      IO.blocking {
        if user.role != Role.Admin then
          (StatusCode.Forbidden, "/events", "Only administrators can delete events.")
        else
          val target = formData.get("eventId").filter(_.nonEmpty)
            .orElse(formData.get("eventName").filter(_.nonEmpty))
            .getOrElse("").trim
          val displayName = formData.get("eventName").filter(_.nonEmpty).getOrElse(target)
          if target.isEmpty then
            (StatusCode.SeeOther, "/events?err=Cannot+delete+unnamed+event", "")
          else
            if store.deleteEvent(target, user) then
              if user.session.currentIcs205.contains(target) then
                sessionStore.save(user.session.copy(currentIcs205 = None))
              Ics205ActivityLogger.logUpdate(
                username = user.user.username,
                eventName = displayName,
                action = Some("delete")
              )
              (StatusCode.SeeOther, s"/events?msg=Event+'${encode(displayName)}'+deleted+successfully", "")
            else
              (StatusCode.SeeOther, "/events?err=Event+not+found", "")
      }
    }

  override val endpoints: List[ServerEndpoint[Any, IO]] = List(
    indexEndpoint,
    saveEndpoint,
    previewEndpoint,
    radioEndpoint,
    eventsEndpoint,
    selectEventEndpoint,
    createEventEndpoint,
    getMetadataEndpoint,
    postMetadataEndpoint,
    deleteEventEndpoint,
    duplicateEventEndpoint,
    exportEventEndpoint,
    importEventEndpoint
  )
