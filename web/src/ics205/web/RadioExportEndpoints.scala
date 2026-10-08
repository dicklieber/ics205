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
import ics205.auth.{AuthConfig, AuthenticatedUser, AuthenticationService, Role}
import ics205.exporter.{RadioExportDefinitions, RadioExporter}
import ics205.log.Ics205ActivityLogger
import ics205.model.Ics205Event
import ics205.store.Ics205Store
import ics205.web.auth.AuthSecurity
import jakarta.inject.{Inject, Singleton}
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint

@Singleton
class RadioExportEndpoints @Inject()(
  radioExporter: RadioExporter,
  definitions: RadioExportDefinitions,
  val store: Ics205Store,
  security: AuthSecurity
) extends ApiEndpoints with EventResolving:

  def this(
    radioExporter: RadioExporter,
    definitions: RadioExportDefinitions,
    store: Ics205Store,
    authService: AuthenticationService,
    config: AuthConfig
  ) = this(radioExporter, definitions, store, new AuthSecurity(authService, config))

  private val getExportRadioEndpoint: ServerEndpoint[Any, IO] = security.secureEndpoint
    .get
    .in("export" / "radio")
    .in(query[Option[String]]("event"))
    .in(query[Option[String]]("definition"))
    .in(query[Option[String]]("groupOrBank"))
    .in(query[Option[Boolean]]("includeHeader"))
    .in(query[Option[Boolean]]("appendExtra"))
    .in(query[Option[String]]("msg"))
    .in(query[Option[String]]("err"))
    .out(statusCode.and(header[Option[String]]("Location")).and(htmlBodyUtf8))
    .serverLogicSuccess { user => (eventQueryOpt, defOpt, groupOrBankOpt, incHeaderOpt, appendExtraOpt, msg, err) =>
      IO.blocking {
        val (currentEventOpt, authorizedEvents) = resolveEvent(eventQueryOpt, user)
        currentEventOpt match
          case None =>
            (StatusCode.SeeOther, Some("/events"), "")
          case Some(currentEvent) =>
            if !currentEvent.canView(user) && user.role != Role.Admin then
              (StatusCode.Forbidden, None, "You do not have permission to view this radio plan.")
            else
              val plan = currentEvent.ics205
              val allDefs = definitions.all
              val incHeader = incHeaderOpt.getOrElse(true)
              val appendExtra = appendExtraOpt.getOrElse(false)
              val exportPlan = if appendExtra then plan.withExtraAppendedToNames else plan
              val (selectedDef, csvOpt, errorOpt) = defOpt match
                case Some(defName) =>
                  try
                    val csv = radioExporter.generateCsv(defName, exportPlan, incHeader, groupOrBankOpt)
                    Ics205ActivityLogger.logCsvExport(
                      username = user.user.username,
                      eventName = currentEvent.eventName,
                      radio = defName,
                      incidentName = Option(plan.incidentName).filter(_.nonEmpty),
                      channelCount = Some(plan.channels.size),
                      includeHeader = Some(incHeader),
                      groupOrBank = groupOrBankOpt,
                      download = Some(false)
                    )
                    (Some(defName), Some(csv), err)
                  catch
                    case ex: Exception =>
                      (Some(defName), None, Some(ex.getMessage))
                case None =>
                  (allDefs.headOption.map(_.name), None, err)

              val html = RadioExportPage.renderDefinitions(
                currentUser = user,
                plan = plan,
                definitions = allDefs,
                selectedDefinition = selectedDef,
                groupOrBank = groupOrBankOpt,
                includeHeader = incHeader,
                appendExtra = appendExtra,
                generatedCsv = csvOpt,
                message = msg,
                error = errorOpt,
                currentEventName = Some(currentEvent.eventName),
                availableEvents = authorizedEvents.map(_.eventName)
              )
              (StatusCode.Ok, None, html)
      }
    }

  private val postExportRadioEndpoint: ServerEndpoint[Any, IO] = security.secureEndpoint
    .post
    .in("export" / "radio")
    .in(query[Option[String]]("event"))
    .in(formBody[Map[String, String]])
    .out(statusCode.and(header[Option[String]]("Location")).and(header[String]("Content-Type")).and(header[Option[String]]("Content-Disposition")).and(stringBody))
    .serverLogicSuccess { user => (eventQueryOpt, formData) =>
      IO.blocking {
        val targetName = formData.get("eventName").filter(_.nonEmpty)
          .orElse(eventQueryOpt.filter(_.nonEmpty))
        val (currentEventOpt, authorizedEvents) = resolveEvent(targetName, user)

        currentEventOpt match
          case None =>
            (StatusCode.SeeOther, Some("/events"), "text/html; charset=utf-8", None, "")
          case Some(currentEvent) =>
            if !currentEvent.canView(user) && user.role != Role.Admin then
              (StatusCode.Forbidden, None, "text/html; charset=utf-8", None, "You do not have permission to view this radio plan.")
            else
              val plan = currentEvent.ics205
              val allDefs = definitions.all
              val defName = formData.getOrElse("definition", allDefs.headOption.map(_.name).getOrElse("")).trim
              val incHeader = formData.get("includeHeader").contains("true")
              val groupOrBankOpt = formData.get("groupOrBank").map(_.trim).filter(_.nonEmpty)
              val appendExtra = formData.get("appendExtra").contains("true")
              val exportPlan = if appendExtra then plan.withExtraAppendedToNames else plan

              if defName.isEmpty then
                val html = RadioExportPage.renderDefinitions(
                  currentUser = user,
                  plan = plan,
                  definitions = allDefs,
                  selectedDefinition = None,
                  groupOrBank = groupOrBankOpt,
                  includeHeader = incHeader,
                  appendExtra = appendExtra,
                  generatedCsv = None,
                  error = Some("Please select a radio export definition."),
                  currentEventName = Some(currentEvent.eventName),
                  availableEvents = authorizedEvents.map(_.eventName)
                )
                (StatusCode.Ok, None, "text/html; charset=utf-8", None, html)
              else
                try
                  val csv = radioExporter.generateCsv(defName, exportPlan, incHeader, groupOrBankOpt)
                  val isDownload = formData.get("download").contains("true")
                  Ics205ActivityLogger.logCsvExport(
                    username = user.user.username,
                    eventName = currentEvent.eventName,
                    radio = defName,
                    incidentName = Option(plan.incidentName).filter(_.nonEmpty),
                    channelCount = Some(plan.channels.size),
                    includeHeader = Some(incHeader),
                    groupOrBank = groupOrBankOpt,
                    download = Some(isDownload)
                  )
                  val html = RadioExportPage.renderDefinitions(
                    currentUser = user,
                    plan = plan,
                    definitions = allDefs,
                    selectedDefinition = Some(defName),
                    groupOrBank = groupOrBankOpt,
                    includeHeader = incHeader,
                    appendExtra = appendExtra,
                    generatedCsv = Some(csv),
                    currentEventName = Some(currentEvent.eventName),
                    availableEvents = authorizedEvents.map(_.eventName)
                  )
                  if isDownload then
                    val exportName = definitions.get(defName).name
                    val filename = s"${currentEvent.eventName}_${exportName}.csv"
                      .replaceAll("""[\\/:*?"<>|\p{Cntrl}]""", "_")
                    (StatusCode.Ok, None, "text/csv; charset=utf-8", Some(s"""attachment; filename="$filename""""), csv)
                  else
                    (StatusCode.Ok, None, "text/html; charset=utf-8", None, html)
                catch
                  case ex: Exception =>
                    val html = RadioExportPage.renderDefinitions(
                      currentUser = user,
                      plan = plan,
                      definitions = allDefs,
                      selectedDefinition = Some(defName),
                      groupOrBank = groupOrBankOpt,
                      includeHeader = incHeader,
                      appendExtra = appendExtra,
                      generatedCsv = None,
                      error = Some(s"Failed to generate CSV: ${ex.getMessage}"),
                      currentEventName = Some(currentEvent.eventName),
                      availableEvents = authorizedEvents.map(_.eventName)
                    )
                    (StatusCode.Ok, None, "text/html; charset=utf-8", None, html)
      }
    }

  private val getExportAliasEndpoint: ServerEndpoint[Any, IO] = security.secureEndpoint
    .get
    .in("export")
    .out(statusCode.and(header[Option[String]]("Location")).and(stringBody))
    .serverLogicSuccess { _ => _ =>
      IO.pure((StatusCode.SeeOther, Some("/export/radio"), ""))
    }

  override val endpoints: List[ServerEndpoint[Any, IO]] = List(
    getExportRadioEndpoint,
    postExportRadioEndpoint,
    getExportAliasEndpoint
  )
