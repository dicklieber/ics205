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
import ics205.auth.{AuthConfig, AuthenticationService}
import ics205.exporter.{RadioExportDefinitions, RadioExporter}
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

  private val getExportRadioEndpoint: ServerEndpoint[Any, IO] = endpoint.get
    .in("export" / "radio")
    .in(cookie[Option[String]](config.cookieName))
    .in(query[Option[String]]("definition"))
    .in(query[Option[Boolean]]("includeHeader"))
    .in(query[Option[String]]("msg"))
    .in(query[Option[String]]("err"))
    .out(statusCode.and(header[Option[String]]("Location")).and(htmlBodyUtf8))
    .serverLogicSuccess[IO] { (sessionIdOpt, defOpt, incHeaderOpt, msg, err) =>
      IO.blocking {
        sessionIdOpt.flatMap(id => authService.authenticateSession(id).toOption) match
          case None =>
            (StatusCode.SeeOther, Some("/login"), "")
          case Some(user) =>
            val plan = store.ics205()
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
              error = errorOpt
            )
            (StatusCode.Ok, None, html)
      }
    }

  private val postExportRadioEndpoint: ServerEndpoint[Any, IO] = endpoint.post
    .in("export" / "radio")
    .in(cookie[Option[String]](config.cookieName))
    .in(formBody[Map[String, String]])
    .out(statusCode.and(header[Option[String]]("Location")).and(htmlBodyUtf8))
    .serverLogicSuccess[IO] { (sessionIdOpt, formData) =>
      IO.blocking {
        sessionIdOpt.flatMap(id => authService.authenticateSession(id).toOption) match
          case None =>
            (StatusCode.SeeOther, Some("/login"), "")
          case Some(user) =>
            val plan = store.ics205()
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
                error = Some("Please select a radio export definition.")
              )
              (StatusCode.Ok, None, html)
            else
              try
                val csv = radioExporter.generateCsv(defName, plan, incHeader)
                val html = RadioExportPage.render(
                  currentUser = user,
                  plan = plan,
                  definitions = defsList,
                  selectedDefinition = Some(defName),
                  includeHeader = incHeader,
                  generatedCsv = Some(csv)
                )
                (StatusCode.Ok, None, html)
              catch
                case ex: Exception =>
                  val html = RadioExportPage.render(
                    currentUser = user,
                    plan = plan,
                    definitions = defsList,
                    selectedDefinition = Some(defName),
                    includeHeader = incHeader,
                    generatedCsv = None,
                    error = Some(s"Failed to generate CSV: ${ex.getMessage}")
                  )
                  (StatusCode.Ok, None, html)
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
