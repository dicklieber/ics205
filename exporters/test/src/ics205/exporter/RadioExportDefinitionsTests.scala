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

object TestDiscoveredRadio extends RadioExport:
  val definition: RadioExportDefinition = StandardRadioFormats.base.copy(name = "Test Mock Radio")

class RadioExportDefinitionsTests extends munit.FunSuite:

  test("RadioExportDefinitions discovers implementations via ClassGraph automatically"):
    val definitions = new RadioExportDefinitions()
    val list = definitions.listDefinitions
    assert(list.nonEmpty, "Definitions list should not be empty")
    assert(list.contains("Kenwood TH-D75"), s"Expected 'Kenwood TH-D75' in $list")
    assert(list.contains("Yaesu FTM-500"), s"Expected 'Yaesu FTM-500' in $list")
    assert(list.contains("Yaesu FTM-510"), s"Expected 'Yaesu FTM-510' in $list")
    assert(list.contains("Icom ID-52Plus"), s"Expected 'Icom ID-52Plus' in $list")
    assert(list.contains("Test Mock Radio"), s"Expected 'Test Mock Radio' discovered in $list")
    assertEquals(list, list.sorted)

  test("RadioExportDefinitions.get retrieves definition by exact name"):
    val definitions = new RadioExportDefinitions()

    assertEquals(definitions.get("Kenwood TH-D75").name, "Kenwood TH-D75")
    assertEquals(definitions.get("Yaesu FTM-500").name, "Yaesu FTM-500")
    assertEquals(definitions.get("Yaesu FTM-510").name, "Yaesu FTM-510")
    assertEquals(definitions.get("Icom ID-52Plus").name, "Icom ID-52Plus")
    assertEquals(definitions.get("Test Mock Radio").name, "Test Mock Radio")

  test("RadioExportDefinitions.get throws IllegalArgumentException for unknown definition"):
    val definitions = new RadioExportDefinitions()
    intercept[IllegalArgumentException]:
      definitions.get("NonExistentRadio")
