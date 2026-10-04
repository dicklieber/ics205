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

package ics205.model

import io.circe.parser.parse
import io.circe.syntax.*
import java.time.LocalDateTime

class Ics205JsonTests extends munit.FunSuite:
  private val prepared = LocalDateTime.of(2026, 9, 20, 14, 5, 0)
  private val channel1 = Ics205Channel(
    id = "repeater-1",
    zoneGroup = Some("Local"),
    channelNumber = Some("1"),
    function = "Command",
    name = "Repeater",
    assignment = "Operations",
    frequency = RxWithOffset(mhz"146.94", mhz"-0.6"),
    mode = RadioMode.Fm,
    bandwidth = Bandwidth.Narrow,
    ctcss = Ctcss(Some(CtcssFrequency.Hz100_0), CtcssMode.Tone),
    remarks = "Monitor"
  )
  private val channel2 = Ics205Channel(
    id = "simplex-2",
    zoneGroup = None,
    channelNumber = Some("2"),
    function = "Tactical",
    name = "TAC 1",
    assignment = "Field teams",
    frequency = RxWithOffset(mhz"146.52"),
    mode = RadioMode.Digital,
    bandwidth = Bandwidth.Wide,
    ctcss = Ctcss(None, CtcssMode.None),
    remarks = "Simplex calling"
  )
  private val plan = Ics205(
    incidentName = "Wildfire Exercise",
    operationalPeriod = OperationalPeriod(Some(prepared), Some(prepared.plusHours(8))),
    channels = Seq(channel1, channel2),
    specialInstructions = "Check in every hour",
    preparedBy = Some(PreparedBy("Alex Leader", Some("W9ABC"))),
    prepared = prepared
  )

  test("toJson produces pretty JSON with 2-space indentation"):
    val jsonString = Ics205Json.toJson(plan)
    assert(jsonString.contains("  \"incidentName\": \"Wildfire Exercise\""))
    assert(jsonString.contains("  \"channels\": ["))
    assert(jsonString.contains("    \"id\": \"repeater-1\""))
    // Ensure valid JSON structure
    val parsed = parse(jsonString)
    assert(parsed.isRight)

  test("fromJson round-trips a complete Ics205 plan"):
    val jsonString = Ics205Json.toJson(plan)
    val decoded = Ics205Json.fromJson(jsonString)
    assertEquals(decoded, Right(plan))

  test("fromJson parses an Ics205 wrapped in an Ics205Event"):
    val event = Ics205Event("Wildfire Event", plan)
    val eventJson = event.asJson.noSpaces
    val decoded = Ics205Json.fromJson(eventJson)
    assertEquals(decoded, Right(plan))

  test("fromJson handles sparse plan with omitted optional fields"):
    val sparse = Ics205(
      incidentName = "Minimal",
      operationalPeriod = OperationalPeriod(),
      channels = Seq.empty,
      prepared = prepared
    )
    val jsonString = Ics205Json.toJson(sparse)
    val decoded = Ics205Json.fromJson(jsonString)
    assertEquals(decoded, Right(sparse))

  test("fromJson returns descriptive error when JSON is invalid syntax"):
    val result = Ics205Json.fromJson("not valid json {")
    assert(result.isLeft)
    assert(result.left.toOption.get.contains("Failed to parse JSON"))

  test("fromJson returns descriptive error when JSON is not an object"):
    val arrayResult = Ics205Json.fromJson("[1, 2, 3]")
    assert(arrayResult.isLeft)
    assert(arrayResult.left.toOption.get.contains("Invalid JSON format"))

    val stringResult = Ics205Json.fromJson("\"just a string\"")
    assert(stringResult.isLeft)
    assert(stringResult.left.toOption.get.contains("Invalid JSON format"))

  test("fromJson returns descriptive error when JSON structure is invalid for Ics205"):
    val result = Ics205Json.fromJson("""{"unknownField": 123}""")
    assert(result.isLeft)
    assert(result.left.toOption.get.contains("Failed to decode ICS 205"))

  test("toJson(event) produces pretty JSON and eventFromJson round-trips Ics205Event"):
    val event = Ics205Event("Field Day 2026", plan)
    val jsonString = Ics205Json.toJson(event)
    assert(jsonString.contains("  \"eventName\": \"Field Day 2026\""))
    assert(jsonString.contains("  \"ics205\": {"))
    val decoded = Ics205Json.eventFromJson(jsonString)
    assert(decoded.isRight)
    assertEquals(decoded.toOption.get.eventName, "Field Day 2026")
    assertEquals(decoded.toOption.get.ics205, plan)

  test("eventFromJson parses unwrapped Ics205 plan and sets eventName"):
    val jsonString = Ics205Json.toJson(plan)
    val decoded = Ics205Json.eventFromJson(jsonString)
    assert(decoded.isRight)
    assertEquals(decoded.toOption.get.eventName, "Wildfire Exercise")
    assertEquals(decoded.toOption.get.ics205, plan)

  test("eventFromJson handles empty event name fallback to incidentName or default"):
    val sparse = Ics205(
      incidentName = "",
      operationalPeriod = OperationalPeriod(),
      channels = Seq.empty,
      prepared = prepared
    )
    val jsonString = Ics205Json.toJson(sparse)
    val decoded = Ics205Json.eventFromJson(jsonString)
    assert(decoded.isRight)
    assertEquals(decoded.toOption.get.eventName, "Imported Event")

  test("eventFromJson returns error for invalid JSON syntax or non-object"):
    assert(Ics205Json.eventFromJson("not json").isLeft)
    assert(Ics205Json.eventFromJson("[1, 2]").isLeft)
