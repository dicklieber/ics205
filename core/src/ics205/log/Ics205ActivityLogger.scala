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

package ics205.log

import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
import io.circe.syntax.*

import java.time.Instant

object Ics205ActivityLogger extends LazyLogging:

  def logImport(
    username: String,
    eventName: String,
    incidentName: Option[String] = None,
    channelCount: Option[Int] = None,
    fileName: Option[String] = None,
    format: Option[String] = Some("json"),
    timestamp: Instant = Instant.now()
  ): Json =
    val json = Json.obj(
      "activity" -> "import".asJson,
      "username" -> username.asJson,
      "eventName" -> eventName.asJson,
      "incidentName" -> incidentName.asJson,
      "channelCount" -> channelCount.asJson,
      "fileName" -> fileName.asJson,
      "format" -> format.asJson,
      "timestamp" -> timestamp.toString.asJson
    ).dropNullValues
    logger.info(json.noSpaces)
    json

  def logExport(
    username: String,
    eventName: String,
    format: String,
    incidentName: Option[String] = None,
    channelCount: Option[Int] = None,
    timestamp: Instant = Instant.now()
  ): Json =
    val json = Json.obj(
      "activity" -> "export".asJson,
      "username" -> username.asJson,
      "eventName" -> eventName.asJson,
      "format" -> format.asJson,
      "incidentName" -> incidentName.asJson,
      "channelCount" -> channelCount.asJson,
      "timestamp" -> timestamp.toString.asJson
    ).dropNullValues
    logger.info(json.noSpaces)
    json

  def logUpdate(
    username: String,
    eventName: String,
    incidentName: Option[String] = None,
    channelCount: Option[Int] = None,
    action: Option[String] = None,
    details: Option[Json] = None,
    timestamp: Instant = Instant.now()
  ): Json =
    val json = Json.obj(
      "activity" -> "update".asJson,
      "username" -> username.asJson,
      "eventName" -> eventName.asJson,
      "incidentName" -> incidentName.asJson,
      "channelCount" -> channelCount.asJson,
      "action" -> action.asJson,
      "details" -> details.asJson,
      "timestamp" -> timestamp.toString.asJson
    ).dropNullValues
    logger.info(json.noSpaces)
    json

  def logCsvExport(
    username: String,
    eventName: String,
    radio: String,
    incidentName: Option[String] = None,
    channelCount: Option[Int] = None,
    includeHeader: Option[Boolean] = None,
    groupOrBank: Option[String] = None,
    download: Option[Boolean] = None,
    timestamp: Instant = Instant.now()
  ): Json =
    val json = Json.obj(
      "activity" -> "csv_export".asJson,
      "username" -> username.asJson,
      "eventName" -> eventName.asJson,
      "radio" -> radio.asJson,
      "incidentName" -> incidentName.asJson,
      "channelCount" -> channelCount.asJson,
      "includeHeader" -> includeHeader.asJson,
      "groupOrBank" -> groupOrBank.asJson,
      "download" -> download.asJson,
      "timestamp" -> timestamp.toString.asJson
    ).dropNullValues
    logger.info(json.noSpaces)
    json
