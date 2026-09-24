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

class ChannelFieldsTests extends munit.FunSuite:
  test("ChannelFields extracts all field values correctly"):
    val channel = Ics205Channel(
      id = "1",
      zoneGroup = Some("Zone 1"),
      channelNumber = Some("CH-01"),
      function = "Tac 1",
      name = "TAC1",
      assignment = "Operations",
      frequency = RxWithOffset(mhz"146.520", mhz"0.600"),
      mode = RadioMode.Fm,
      bandwidth = Some(Bandwidth.Wide),
      ctcss = Ctcss(Some(CtcssFrequency.Hz100_0), CtcssMode.Tone),
      remarks = "Primary tactical"
    )

    val fields = ChannelFields(channel)
    assertEquals(fields(ChannelField.Id), "1")
    assertEquals(fields(ChannelField.ZoneGroup), "Zone 1")
    assertEquals(fields(ChannelField.ChannelNumber), "CH-01")
    assertEquals(fields(ChannelField.Function), "Tac 1")
    assertEquals(fields(ChannelField.Name), "TAC1")
    assertEquals(fields(ChannelField.Assignment), "Operations")
    assertEquals(fields(ChannelField.RadioChannelName), "TAC1")
    assertEquals(fields(ChannelField.Rx), "146.520")
    assertEquals(fields(ChannelField.Tx), "147.120")
    assertEquals(fields(ChannelField.Offset), "0.600")
    assertEquals(fields(ChannelField.Mode), "Fm")
    assertEquals(fields(ChannelField.Bandwidth), "Wide")
    assertEquals(fields(ChannelField.CtcssFrequency), "100.0")
    assertEquals(fields(ChannelField.CtcssMode), "Tone")
    assertEquals(fields(ChannelField.Remarks), "Primary tactical")

    assertEquals(ChannelField.Id.value(channel), "1")
    assertEquals(ChannelField.Name.value(channel), "TAC1")

    assertEquals(fields.all.size, ChannelField.values.length)
