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

import io.circe.parser.*
import java.time.Instant

class Ics205ActivityLoggerTests extends munit.FunSuite:

  test("logImport produces valid JSON containing user and import activity details"):
    val now = Instant.parse("2026-10-02T12:00:00Z")
    val json = Ics205ActivityLogger.logImport(
      username = "alice",
      eventName = "Field Day",
      incidentName = Some("Field Day 2026"),
      channelCount = Some(15),
      fileName = Some("fieldday.json"),
      format = Some("json"),
      timestamp = now
    )

    val jsonStr = json.noSpaces
    val parsed = parse(jsonStr).toOption.get
    val cursor = parsed.hcursor

    assertEquals(cursor.get[String]("activity"), Right("import"))
    assertEquals(cursor.get[String]("username"), Right("alice"))
    assertEquals(cursor.get[String]("eventName"), Right("Field Day"))
    assertEquals(cursor.get[String]("incidentName"), Right("Field Day 2026"))
    assertEquals(cursor.get[Int]("channelCount"), Right(15))
    assertEquals(cursor.get[String]("fileName"), Right("fieldday.json"))
    assertEquals(cursor.get[String]("format"), Right("json"))
    assertEquals(cursor.get[String]("timestamp"), Right("2026-10-02T12:00:00Z"))

  test("logExport produces valid JSON containing user, format, and export details"):
    val now = Instant.parse("2026-10-02T12:00:00Z")
    val json = Ics205ActivityLogger.logExport(
      username = "bob",
      eventName = "Wildfire",
      format = "json",
      incidentName = Some("Wildfire Response"),
      channelCount = Some(8),
      timestamp = now
    )

    val parsed = parse(json.noSpaces).toOption.get
    val cursor = parsed.hcursor

    assertEquals(cursor.get[String]("activity"), Right("export"))
    assertEquals(cursor.get[String]("username"), Right("bob"))
    assertEquals(cursor.get[String]("eventName"), Right("Wildfire"))
    assertEquals(cursor.get[String]("format"), Right("json"))
    assertEquals(cursor.get[String]("incidentName"), Right("Wildfire Response"))
    assertEquals(cursor.get[Int]("channelCount"), Right(8))
    assertEquals(cursor.get[String]("timestamp"), Right("2026-10-02T12:00:00Z"))

  test("logUpdate produces valid JSON containing user and update details"):
    val now = Instant.parse("2026-10-02T12:00:00Z")
    val json = Ics205ActivityLogger.logUpdate(
      username = "charlie",
      eventName = "Marathon",
      incidentName = Some("City Marathon"),
      channelCount = Some(24),
      action = Some("save"),
      timestamp = now
    )

    val parsed = parse(json.noSpaces).toOption.get
    val cursor = parsed.hcursor

    assertEquals(cursor.get[String]("activity"), Right("update"))
    assertEquals(cursor.get[String]("username"), Right("charlie"))
    assertEquals(cursor.get[String]("eventName"), Right("Marathon"))
    assertEquals(cursor.get[String]("incidentName"), Right("City Marathon"))
    assertEquals(cursor.get[Int]("channelCount"), Right(24))
    assertEquals(cursor.get[String]("action"), Right("save"))
    assertEquals(cursor.get[String]("timestamp"), Right("2026-10-02T12:00:00Z"))

  test("logCsvExport produces valid JSON containing user, radio, and export details"):
    val now = Instant.parse("2026-10-02T12:00:00Z")
    val json = Ics205ActivityLogger.logCsvExport(
      username = "dave",
      eventName = "Severe Weather",
      radio = "Chirp",
      incidentName = Some("Storm 2026"),
      channelCount = Some(10),
      includeHeader = Some(true),
      groupOrBank = Some("Bank 1"),
      download = Some(true),
      timestamp = now
    )

    val parsed = parse(json.noSpaces).toOption.get
    val cursor = parsed.hcursor

    assertEquals(cursor.get[String]("activity"), Right("csv_export"))
    assertEquals(cursor.get[String]("username"), Right("dave"))
    assertEquals(cursor.get[String]("eventName"), Right("Severe Weather"))
    assertEquals(cursor.get[String]("radio"), Right("Chirp"))
    assertEquals(cursor.get[String]("incidentName"), Right("Storm 2026"))
    assertEquals(cursor.get[Int]("channelCount"), Right(10))
    assertEquals(cursor.get[Boolean]("includeHeader"), Right(true))
    assertEquals(cursor.get[String]("groupOrBank"), Right("Bank 1"))
    assertEquals(cursor.get[Boolean]("download"), Right(true))
    assertEquals(cursor.get[String]("timestamp"), Right("2026-10-02T12:00:00Z"))
