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

import ics205.auth.{AuthenticatedUser, RolePermissions, Session, User}
import ics205.model.{Frequency, Ics205, Ics205Channel, OperationalPeriod, RadioMode, RxWithOffset}
import java.time.Instant

class RadioExportPageTests extends munit.FunSuite:

  val sampleUser = AuthenticatedUser(
    username = "johndoe",
    role = RolePermissions.Admin,
    id = "u1"
  )

  val samplePlan = Ics205(
    incidentName = "Forest Fire 2026",
    operationalPeriod = OperationalPeriod(),
    channels = Seq(
      Ics205Channel(
        id = "1",
        function = "Tac 1",
        name = "TAC1",
        assignment = "Division Alpha",
        frequency = RxWithOffset(Frequency(BigDecimal("146.520")), Frequency(BigDecimal("0.600"))),
        mode = RadioMode.Fm
      )
    )
  )

  test("RadioExportPage renders definitions and controls"):
    val html = RadioExportPage.render(
      currentUser = sampleUser,
      plan = samplePlan,
      definitions = Seq("Kenwood TH-D75", "AnyTone D878UV"),
      selectedDefinition = Some("Kenwood TH-D75"),
      includeHeader = true,
      generatedCsv = None
    )

    assert(html.contains("Export Radio CSV"))
    assert(html.contains("Forest Fire 2026"))
    assert(html.contains("johndoe"))
    assert(html.contains("Kenwood TH-D75"))
    assert(html.contains("AnyTone D878UV"))
    assert(html.contains("Include Header Row"))
    assert(html.contains("checked"))
    assert(html.contains("Generate CSV"))
    assert(!html.contains("id=\"output-card\""))

  test("RadioExportPage renders generated CSV and copy button when CSV is present"):
    val sampleCsv = "Header1,Header2\nVal1,Val2"
    val html = RadioExportPage.render(
      currentUser = sampleUser,
      plan = samplePlan,
      definitions = Seq("Kenwood TH-D75"),
      selectedDefinition = Some("Kenwood TH-D75"),
      includeHeader = true,
      generatedCsv = Some(sampleCsv)
    )

    assert(html.contains("id=\"output-card\""))
    assert(html.contains("id=\"csv-output\""))
    assert(html.contains("Header1,Header2"))
    assert(html.contains("Val1,Val2"))
    assert(html.contains("Copy to Clipboard"))
    assert(html.contains("copyCsvToClipboard()"))
