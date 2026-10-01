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
import ics205.auth.{AuthConfig, AuthenticatedUser, AuthenticationService, RolePermissions}
import ics205.exporter.{RadioExportDefinitions, RadioExporter}
import ics205.model.Ics205Event
import ics205.store.Ics205Store
import jakarta.inject.{Inject, Singleton}
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint

@Singleton
class RadioExportEndpoints @Inject()(
  radioExporter: RadioExporter,
  definitions: RadioExportDefinitions,
  store: Ics205Store,
  authService: AuthenticationService,
  config: AuthConfig
) extends ApiEndpoints:

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

  private val getExportRadioEndpoint: ServerEndpoint[Any, IO] = endpoint.get
    .in("export" / "radio")
    .in(cookie[Option[String]](config.cookieName))
    .in(cookie[Option[String]]("ics205_event"))
    .in(query[Option[String]]("event"))
    .in(query[Option[String]]("definition"))
    .in(query[Option[Boolean]]("includeHeader"))
    .in(query[Option[String]]("msg"))
    .in(query[Option[String]]("err"))
    .out(statusCode.and(header[Option[String]]("Location")).and(htmlBodyUtf8))
    .serverLogicSuccess[IO] { (sessionIdOpt, eventCookieOpt, eventQueryOpt, defOpt, incHeaderOpt, msg, err) =>
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
                  val plan = currentEvent.ics205
                  val defsList = definitions.listDefinitions
                  val incHeader = incHeaderOpt.getOrElse(true)
                  val (selectedDef, csvOpt, errorOpt) = defOpt match
                    case Some(defName) =>
                      try
                        val csv = radioExporter.generateCsv(defName, plan, incHeader)
                        (Some(defName), Some(csv), err)
                      catch
                        case ex: Exception =>
                          (Some(defName), None, Some(ex.getMessage))
                    case None =>
                      (defsList.headOption, None, err)

                  val html = RadioExportPage.render(
                    currentUser = user,
                    plan = plan,
                    definitions = defsList,
                    selectedDefinition = selectedDef,
                    includeHeader = incHeader,
                    generatedCsv = csvOpt,
                    message = msg,
                    error = errorOpt,
                    currentEventName = Some(currentEvent.eventName),
                    availableEvents = authorizedEvents.map(_.eventName)
                  )
                  (StatusCode.Ok, None, html)
      }
    }

  private val postExportRadioEndpoint: ServerEndpoint[Any, IO] = endpoint.post
    .in("export" / "radio")
    .in(cookie[Option[String]](config.cookieName))
    .in(cookie[Option[String]]("ics205_event"))
    .in(query[Option[String]]("event"))
    .in(formBody[Map[String, String]])
    .out(statusCode.and(header[Option[String]]("Location")).and(header[String]("Content-Type")).and(header[Option[String]]("Content-Disposition")).and(stringBody))
    .serverLogicSuccess[IO] { (sessionIdOpt, eventCookieOpt, eventQueryOpt, formData) =>
      IO.blocking {
        sessionIdOpt.flatMap(id => authService.authenticateSession(id).toOption) match
          case None =>
            (StatusCode.SeeOther, Some("/login"), "text/html; charset=utf-8", None, "")
          case Some(user) =>
            val targetName = formData.get("eventName").filter(_.nonEmpty)
              .orElse(eventQueryOpt.filter(_.nonEmpty))
              .orElse(eventCookieOpt.filter(_.nonEmpty))
            val (currentEventOpt, authorizedEvents) = resolveEvent(targetName, eventCookieOpt, user)

            currentEventOpt match
              case None =>
                (StatusCode.SeeOther, Some("/events"), "text/html; charset=utf-8", None, "")
              case Some(currentEvent) =>
                if !currentEvent.canView(user) && user.role != RolePermissions.Admin then
                  (StatusCode.Forbidden, None, "text/html; charset=utf-8", None, "You do not have permission to view this radio plan.")
                else
                  val plan = currentEvent.ics205
                  val defsList = definitions.listDefinitions
                  val defName = formData.getOrElse("definition", defsList.headOption.getOrElse("")).trim
                  val incHeader = formData.get("includeHeader").contains("true")

                  if defName.isEmpty then
                    val html = RadioExportPage.render(
                      currentUser = user,
                      plan = plan,
                      definitions = defsList,
                      selectedDefinition = None,
                      includeHeader = incHeader,
                      generatedCsv = None,
                      error = Some("Please select a radio export definition."),
                      currentEventName = Some(currentEvent.eventName),
                      availableEvents = authorizedEvents.map(_.eventName)
                    )
                    (StatusCode.Ok, None, "text/html; charset=utf-8", None, html)
                  else
                    try
                      val csv = radioExporter.generateCsv(defName, plan, incHeader)
                      val html = RadioExportPage.render(
                        currentUser = user,
                        plan = plan,
                        definitions = defsList,
                        selectedDefinition = Some(defName),
                        includeHeader = incHeader,
                        generatedCsv = Some(csv),
                        currentEventName = Some(currentEvent.eventName),
                        availableEvents = authorizedEvents.map(_.eventName)
                      )
                      if formData.get("download").contains("true") then
                        val exportName = definitions.get(defName).name
                        val filename = s"${currentEvent.eventName}_${exportName}.csv"
                          .replaceAll("""[\\/:*?"<>|\p{Cntrl}]""", "_")
                        (StatusCode.Ok, None, "text/csv; charset=utf-8", Some(s"""attachment; filename="$filename""""), csv)
                      else
                        (StatusCode.Ok, None, "text/html; charset=utf-8", None, html)
                    catch
                      case ex: Exception =>
                        val html = RadioExportPage.render(
                          currentUser = user,
                          plan = plan,
                          definitions = defsList,
                          selectedDefinition = Some(defName),
                          includeHeader = incHeader,
                          generatedCsv = None,
                          error = Some(s"Failed to generate CSV: ${ex.getMessage}"),
                          currentEventName = Some(currentEvent.eventName),
                          availableEvents = authorizedEvents.map(_.eventName)
                        )
                        (StatusCode.Ok, None, "text/html; charset=utf-8", None, html)
      }
    }

  private val getExportAliasEndpoint: ServerEndpoint[Any, IO] = endpoint.get
    .in("export")
    .out(statusCode.and(header[Option[String]]("Location")).and(stringBody))
    .serverLogicSuccess[IO] { _ =>
      IO.pure((StatusCode.SeeOther, Some("/export/radio"), ""))
    }

  override val endpoints: List[ServerEndpoint[Any, IO]] = List(
    getExportRadioEndpoint,
    postExportRadioEndpoint,
    getExportAliasEndpoint
  )
