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
import ics205.auth.{AuthConfig, AuthenticatedUser, AuthenticationService, Permission, RolePermissions}
import ics205.log.Ics205ActivityLogger
import ics205.model.{Ics205, Ics205Event, Ics205Metadata, OperationalPeriod}
import ics205.store.{Ics205Store, UserStore}
import jakarta.inject.{Inject, Singleton}
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant

@Singleton
class IndexEndpoints @Inject() (
  val store: Ics205Store,
  authService: AuthenticationService,
  userStore: UserStore,
  config: AuthConfig
) extends ApiEndpoints:

  def this(store: Ics205Store) = this(
    store,
    new AuthenticationService(
      new ics205.store.UserStore(new ics205.util.FileHelper),
      new ics205.auth.ScalaPassPasswordService,
      new ics205.store.InMemJsonSessionStore(new ics205.util.FileHelper)
    ),
    new ics205.store.UserStore(new ics205.util.FileHelper),
    ics205.auth.AuthConfig()
  )

  def this(store: Ics205Store, authService: AuthenticationService, config: AuthConfig) =
    this(store, authService, new ics205.store.UserStore(new ics205.util.FileHelper), config)

  def index(): String = Ics205Page.render(store.ics205())

  private def encode(s: String): String = URLEncoder.encode(s, StandardCharsets.UTF_8.toString)

  private def resolveEvent(
    eventQuery: Option[String],
    eventCookie: Option[String],
    user: AuthenticatedUser
  ): (Option[Ics205Event], Seq[Ics205Event]) =
    val allEvents = store.events()
    val authorizedEvents = if user.role == RolePermissions.Admin then allEvents else allEvents.filter(_.canView(user))
    val chosenEvent = eventQuery.filter(_.nonEmpty).flatMap(store.getEvent)
      .orElse(eventCookie.filter(_.nonEmpty).flatMap(store.getEvent))
      .orElse(authorizedEvents.headOption)
      .orElse(store.currentEvent())
    (chosenEvent, authorizedEvents)

  private val indexEndpoint: ServerEndpoint[Any, IO] = endpoint.get
    .in("")
    .in(cookie[Option[String]](config.cookieName))
    .in(cookie[Option[String]]("ics205_event"))
    .in(query[Option[String]]("event"))
    .in(query[Option[String]]("saved"))
    .out(statusCode.and(header[Option[String]]("Location")).and(htmlBodyUtf8))
    .serverLogicSuccess[IO] { (sessionIdOpt, eventCookieOpt, eventQueryOpt, saved) =>
      IO.blocking {
        sessionIdOpt.flatMap(id => authService.authenticateSession(id).toOption) match
          case None =>
            (StatusCode.SeeOther, Some("/login"), "")
          case Some(user) =>
            val (currentEventOpt, authorizedEvents) = resolveEvent(eventQueryOpt, eventCookieOpt, user)
            currentEventOpt match
              case None =>
                (StatusCode.SeeOther, Some("/events"), "")
              case Some(currentEvent) =>
                if !currentEvent.canView(user) && user.role != RolePermissions.Admin then
                  (StatusCode.Forbidden, None, "You do not have permission to view this event.")
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
                      availableEvents = authorizedEvents.map(_.eventName)
                    )
                  )
      }
    }

  private val saveEndpoint: ServerEndpoint[Any, IO] = endpoint.post
    .in("")
    .in(cookie[Option[String]](config.cookieName))
    .in(cookie[Option[String]]("ics205_event"))
    .in(query[Option[String]]("event"))
    .in(formBody[Map[String, String]])
    .errorOut(statusCode.and(header[Option[String]]("Location")).and(htmlBodyUtf8))
    .out(statusCode.and(header[String]("Location")))
    .serverLogic[IO] { (sessionIdOpt, eventCookieOpt, eventQueryOpt, data) =>
      IO.blocking {
        sessionIdOpt.flatMap(id => authService.authenticateSession(id).toOption) match
          case None =>
            Left((StatusCode.SeeOther, Some("/login"), ""))
          case Some(user) =>
            val targetName = data.get("eventName").filter(_.nonEmpty)
              .orElse(eventQueryOpt.filter(_.nonEmpty))
              .orElse(eventCookieOpt.filter(_.nonEmpty))
            val (currentEventOpt, authorizedEvents) = resolveEvent(targetName, eventCookieOpt, user)

            currentEventOpt match
              case None =>
                Left((StatusCode.SeeOther, Some("/events"), ""))
              case Some(currentEvent) =>
                if !currentEvent.canEdit(user) && user.role != RolePermissions.Admin then
                  Left((
                    StatusCode.Forbidden,
                    None,
                    Ics205Editor.render(
                      plan = currentEvent.ics205,
                      error = Some("You do not have permission to edit this plan."),
                      currentUser = Some(user),
                      metadata = Some(currentEvent.metadata),
                      currentEventName = Some(currentEvent.eventName),
                      availableEvents = authorizedEvents.map(_.eventName)
                    )
                  ))
                else
                  Ics205Form.decode(data, currentEvent.ics205) match
                    case Left(message) =>
                      Left((
                        StatusCode.UnprocessableEntity,
                        None,
                        Ics205Editor.render(
                          plan = currentEvent.ics205,
                          submitted = Some(data),
                          error = Some(message),
                          currentUser = Some(user),
                          metadata = Some(currentEvent.metadata),
                          currentEventName = Some(currentEvent.eventName),
                          availableEvents = authorizedEvents.map(_.eventName)
                        )
                      ))
                    case Right(plan) =>
                      try
                        val updatedEvent = currentEvent.copy(ics205 = plan)
                        store.saveEvent(updatedEvent, Some(user.id), refreshPrepared = false)
                        Ics205ActivityLogger.logUpdate(
                          username = user.username,
                          eventName = updatedEvent.eventName,
                          incidentName = Option(plan.incidentName).filter(_.nonEmpty),
                          channelCount = Some(plan.channels.size),
                          action = Some("save")
                        )
                        val redirectUrl = s"/?saved=1&event=${encode(currentEvent.eventName)}"
                        Right((StatusCode.SeeOther, redirectUrl))
                      catch
                        case _: java.io.IOException =>
                          Left((
                            StatusCode.InternalServerError,
                            None,
                            Ics205Editor.render(
                              plan = currentEvent.ics205,
                              submitted = Some(data),
                              error = Some("The plan could not be saved. Check that the data directory is writable and try again."),
                              currentUser = Some(user),
                              metadata = Some(currentEvent.metadata),
                              currentEventName = Some(currentEvent.eventName),
                              availableEvents = authorizedEvents.map(_.eventName)
                            )
                          ))
      }
    }

  private val previewEndpoint: ServerEndpoint[Any, IO] = endpoint.post
    .in("preview")
    .in(cookie[Option[String]](config.cookieName))
    .in(cookie[Option[String]]("ics205_event"))
    .in(query[Option[String]]("event"))
    .in(formBody[Map[String, String]])
    .out(statusCode.and(header[Option[String]]("Location")).and(htmlBodyUtf8))
    .serverLogicSuccess[IO] { (sessionIdOpt, eventCookieOpt, eventQueryOpt, data) =>
      IO.blocking {
        sessionIdOpt.flatMap(id => authService.authenticateSession(id).toOption) match
          case None =>
            (StatusCode.SeeOther, Some("/login"), "")
          case Some(user) =>
            val targetName = data.get("eventName").filter(_.nonEmpty)
              .orElse(eventQueryOpt.filter(_.nonEmpty))
              .orElse(eventCookieOpt.filter(_.nonEmpty))
            val (currentEventOpt, authorizedEvents) = resolveEvent(targetName, eventCookieOpt, user)

            currentEventOpt match
              case None =>
                (StatusCode.SeeOther, Some("/events"), "")
              case Some(currentEvent) =>
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
                        availableEvents = authorizedEvents.map(_.eventName)
                      )
                    )
                  case Right(plan) =>
                    (StatusCode.Ok, None, Ics205Page.renderPrintable(plan))
      }
    }

  private val radioEndpoint: ServerEndpoint[Any, IO] = endpoint.get
    .in("radio")
    .in(cookie[Option[String]](config.cookieName))
    .in(cookie[Option[String]]("ics205_event"))
    .in(query[Option[String]]("event"))
    .out(statusCode.and(header[Option[String]]("Location")).and(htmlBodyUtf8))
    .serverLogicSuccess[IO] { (sessionIdOpt, eventCookieOpt, eventQueryOpt) =>
      IO.blocking {
        sessionIdOpt.flatMap(id => authService.authenticateSession(id).toOption) match
          case None =>
            (StatusCode.SeeOther, Some("/login"), "")
          case Some(user) =>
            val (currentEventOpt, authorizedEvents) = resolveEvent(eventQueryOpt, eventCookieOpt, user)
            currentEventOpt match
              case None =>
                (StatusCode.SeeOther, Some("/events"), "")
              case Some(currentEvent) =>
                if !currentEvent.canView(user) && user.role != RolePermissions.Admin then
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

  private val eventsEndpoint: ServerEndpoint[Any, IO] = endpoint.get
    .in("events")
    .in(cookie[Option[String]](config.cookieName))
    .in(cookie[Option[String]]("ics205_event"))
    .in(query[Option[String]]("msg"))
    .in(query[Option[String]]("err"))
    .out(statusCode.and(header[Option[String]]("Location")).and(htmlBodyUtf8))
    .serverLogicSuccess[IO] { (sessionIdOpt, eventCookieOpt, msg, err) =>
      IO.blocking {
        sessionIdOpt.flatMap(id => authService.authenticateSession(id).toOption) match
          case None =>
            (StatusCode.SeeOther, Some("/login"), "")
          case Some(user) =>
            val allEvents = store.events()
            val visibleEvents = if user.role == RolePermissions.Admin then allEvents else allEvents.filter(_.canView(user))
            val currentEventName = eventCookieOpt.filter(_.nonEmpty).orElse(store.currentEventName)
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

  private val selectEventEndpoint: ServerEndpoint[Any, IO] = endpoint.get
    .in("events" / "select")
    .in(cookie[Option[String]](config.cookieName))
    .in(query[Option[String]]("name"))
    .in(query[Option[String]]("returnUrl"))
    .out(statusCode.and(header[Option[String]]("Set-Cookie")).and(header[String]("Location")).and(htmlBodyUtf8))
    .serverLogicSuccess[IO] { (sessionIdOpt, nameOpt, returnUrlOpt) =>
      IO.blocking {
        sessionIdOpt.flatMap(id => authService.authenticateSession(id).toOption) match
          case None =>
            (StatusCode.SeeOther, None, "/login", "")
          case Some(user) =>
            nameOpt match
              case Some(name) =>
                store.getEvent(name) match
                  case Some(ev) if ev.canView(user) || user.role == RolePermissions.Admin =>
                    store.setCurrentEvent(ev.eventName)
                    val cookieHeader = s"ics205_event=${encode(ev.eventName)}; Path=/; SameSite=Lax"
                    val target = returnUrlOpt.getOrElse("/")
                    (StatusCode.SeeOther, Some(cookieHeader), target, "")
                  case Some(_) =>
                    (StatusCode.Forbidden, None, "/events?err=Unauthorized", "You do not have permission to access this event.")
                  case None =>
                    (StatusCode.SeeOther, None, "/events?err=Event+not+found", "")
              case None =>
                (StatusCode.SeeOther, None, returnUrlOpt.getOrElse("/"), "")
      }
    }

  private val createEventEndpoint: ServerEndpoint[Any, IO] = endpoint.post
    .in("events" / "create")
    .in(cookie[Option[String]](config.cookieName))
    .in(formBody[Map[String, String]])
    .out(statusCode.and(header[Option[String]]("Set-Cookie")).and(header[String]("Location")).and(htmlBodyUtf8))
    .serverLogicSuccess[IO] { (sessionIdOpt, formData) =>
      IO.blocking {
        sessionIdOpt.flatMap(id => authService.authenticateSession(id).toOption) match
          case None =>
            (StatusCode.SeeOther, None, "/login", "")
          case Some(user) =>
            if user.role != RolePermissions.Admin && !user.hasPermission(Permission.EditPlans) then
              (StatusCode.Forbidden, None, "/events", "You do not have permission to create events.")
            else
              val eventName = formData.getOrElse("eventName", "").trim
              val incidentName = formData.get("incidentName").map(_.trim).filter(_.nonEmpty).getOrElse(eventName)

              if eventName.isEmpty then
                (StatusCode.SeeOther, None, "/events?err=Event+name+cannot+be+empty", "")
              else
                val newEvent = Ics205Event(
                  eventName = eventName,
                  ics205 = Ics205(incidentName = incidentName, operationalPeriod = OperationalPeriod(), channels = Seq.empty),
                  metadata = Ics205Metadata(lastEditedBy = Some(user.id))
                )
                store.addEvent(newEvent) match
                  case Left(errorMsg) =>
                    (StatusCode.SeeOther, None, s"/events?err=${encode(errorMsg)}", "")
                  case Right(created) =>
                    Ics205ActivityLogger.logUpdate(
                      username = user.username,
                      eventName = created.eventName,
                      incidentName = Option(created.ics205.incidentName).filter(_.nonEmpty),
                      channelCount = Some(created.ics205.channels.size),
                      action = Some("create")
                    )
                    val cookieHeader = s"ics205_event=${encode(created.eventName)}; Path=/; SameSite=Lax"
                    (StatusCode.SeeOther, Some(cookieHeader), s"/?event=${encode(created.eventName)}&saved=1", "")
      }
    }

  private val getMetadataEndpoint: ServerEndpoint[Any, IO] = endpoint.get
    .in("events" / "metadata")
    .in(cookie[Option[String]](config.cookieName))
    .in(query[Option[String]]("name"))
    .in(query[Option[String]]("msg"))
    .in(query[Option[String]]("err"))
    .out(statusCode.and(header[Option[String]]("Location")).and(htmlBodyUtf8))
    .serverLogicSuccess[IO] { (sessionIdOpt, nameOpt, msg, err) =>
      IO.blocking {
        sessionIdOpt.flatMap(id => authService.authenticateSession(id).toOption) match
          case None =>
            (StatusCode.SeeOther, Some("/login"), "")
          case Some(user) =>
            val targetName = nameOpt.filter(_.nonEmpty).orElse(store.currentEventName).getOrElse("")
            store.getEvent(targetName) match
              case None =>
                (StatusCode.SeeOther, Some("/events?err=Event+not+found"), "")
              case Some(ev) =>
                if user.role != RolePermissions.Admin && !ev.canEdit(user) then
                  (StatusCode.Forbidden, None, "You do not have permission to edit metadata for this event.")
                else
                  val allUsers = userStore.all()
                  val availableNames = store.events().filter(e => user.role == RolePermissions.Admin || e.canView(user)).map(_.eventName)
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

  private val postMetadataEndpoint: ServerEndpoint[Any, IO] = endpoint.post
    .in("events" / "metadata")
    .in(cookie[Option[String]](config.cookieName))
    .in(formBody[Map[String, String]])
    .out(statusCode.and(header[Option[String]]("Set-Cookie")).and(header[String]("Location")).and(htmlBodyUtf8))
    .serverLogicSuccess[IO] { (sessionIdOpt, formData) =>
      IO.blocking {
        sessionIdOpt.flatMap(id => authService.authenticateSession(id).toOption) match
          case None =>
            (StatusCode.SeeOther, None, "/login", "")
          case Some(user) =>
            val origName = formData.get("originalEventName").filter(_.nonEmpty)
              .orElse(formData.get("eventName").filter(_.nonEmpty))
              .getOrElse("").trim
            val newName = formData.getOrElse("newEventName", origName).trim
            val incidentName = formData.getOrElse("incidentName", "").trim

            store.getEvent(origName) match
              case None =>
                (StatusCode.SeeOther, None, "/events?err=Event+not+found", "")
              case Some(ev) =>
                if user.role != RolePermissions.Admin && !ev.canEdit(user) then
                  (StatusCode.Forbidden, None, "/events", "You do not have permission to edit metadata for this event.")
                else if newName.isEmpty then
                  (StatusCode.SeeOther, None, s"/events/metadata?name=${encode(origName)}&err=Event+name+cannot+be+empty", "")
                else
                  val allUsers = userStore.all()
                  val newPermissions = allUsers.flatMap { u =>
                    formData.get(s"perm_${u.id}").flatMap {
                      case "edit" => Some(u.id -> Permission.EditPlans)
                      case "view" => Some(u.id -> Permission.ViewPlans)
                      case _ => None
                    }
                  }.toMap

                  val renameResult = if !newName.equalsIgnoreCase(origName) then
                    store.renameEvent(origName, newName)
                  else
                    Right(ev.copy(eventName = newName))

                  renameResult match
                    case Left(errMsg) =>
                      (StatusCode.SeeOther, None, s"/events/metadata?name=${encode(origName)}&err=${encode(errMsg)}", "")
                    case Right(renamedEv) =>
                      val updatedPlan = renamedEv.ics205.copy(incidentName = incidentName)
                      val updatedMetadata = renamedEv.metadata.copy(
                        permissions = newPermissions,
                        lastEditedBy = Some(user.id),
                        savedAt = Instant.now()
                      )
                      val finalEvent = renamedEv.copy(eventName = newName, ics205 = updatedPlan, metadata = updatedMetadata)
                      store.saveEvent(finalEvent, userId = Some(user.id), refreshPrepared = false)
                      Ics205ActivityLogger.logUpdate(
                        username = user.username,
                        eventName = finalEvent.eventName,
                        incidentName = Option(finalEvent.ics205.incidentName).filter(_.nonEmpty),
                        channelCount = Some(finalEvent.ics205.channels.size),
                        action = Some("metadata")
                      )

                      val cookieHeader = if !newName.equalsIgnoreCase(origName) then
                        Some(s"ics205_event=${encode(newName)}; Path=/; SameSite=Lax")
                      else
                        None

                      (StatusCode.SeeOther, cookieHeader, s"/events/metadata?name=${encode(newName)}&msg=Metadata+updated+successfully", "")
      }
    }

  private val deleteEventEndpoint: ServerEndpoint[Any, IO] = endpoint.post
    .in("events" / "delete")
    .in(cookie[Option[String]](config.cookieName))
    .in(formBody[Map[String, String]])
    .out(statusCode.and(header[String]("Location")).and(htmlBodyUtf8))
    .serverLogicSuccess[IO] { (sessionIdOpt, formData) =>
      IO.blocking {
        sessionIdOpt.flatMap(id => authService.authenticateSession(id).toOption) match
          case None =>
            (StatusCode.SeeOther, "/login", "")
          case Some(user) =>
            if user.role != RolePermissions.Admin then
              (StatusCode.Forbidden, "/events", "Only administrators can delete events.")
            else
              val eventName = formData.getOrElse("eventName", "").trim
              if eventName.isEmpty then
                (StatusCode.SeeOther, "/events?err=Cannot+delete+unnamed+event", "")
              else
                if store.deleteEvent(eventName) then
                  Ics205ActivityLogger.logUpdate(
                    username = user.username,
                    eventName = eventName,
                    action = Some("delete")
                  )
                  (StatusCode.SeeOther, s"/events?msg=Event+'${encode(eventName)}'+deleted+successfully", "")
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
    deleteEventEndpoint
  )
