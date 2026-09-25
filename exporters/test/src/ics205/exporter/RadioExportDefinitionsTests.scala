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

package ics205.exporter

class RadioExportDefinitionsTests extends munit.FunSuite:

  test("RadioExportDefinitions discovers and preloads all definitions in resources"):
    val definitions = new RadioExportDefinitions()
    val list = definitions.listDefinitions
    assert(list.nonEmpty, "Definitions list should not be empty")
    assert(list.contains("Kenwood TH-D75"), s"Expected 'Kenwood TH-D75' in $list")
    assertEquals(list, list.sorted)

  test("RadioExportDefinitions.get retrieves definition by name or baseName"):
    val definitions = new RadioExportDefinitions()
    val byName = definitions.get("Kenwood TH-D75")
    assertEquals(byName.name, "Kenwood TH-D75")

    val byBase = definitions.get("TH-D75")
    assertEquals(byBase.name, "Kenwood TH-D75")

    val byFile = definitions.get("TH-D75.json")
    assertEquals(byFile.name, "Kenwood TH-D75")

  test("RadioExportDefinitions.get throws IllegalArgumentException for unknown definition"):
    val definitions = new RadioExportDefinitions()
    intercept[IllegalArgumentException]:
      definitions.get("NonExistentRadio")
