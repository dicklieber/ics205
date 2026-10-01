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

import com.typesafe.scalalogging.LazyLogging
import jakarta.inject.{Inject, Singleton}

@Singleton
class RadioExportDefinitions @Inject()() extends LazyLogging:

  val all: Seq[RadioExportDefinition] = Seq(
    KenwoodTHD75.definition,
    YaesuFTM500.definition,
    YaesuFTM510.definition,
    IcomID52Plus.definition
  )

  private val byName: Map[String, RadioExportDefinition] =
    all.map(d => d.name -> d).toMap

  def listDefinitions: Seq[String] =
    all.map(_.name).distinct.sorted

  def get(name: String): RadioExportDefinition =
    byName.getOrElse(name, throw new IllegalArgumentException(s"Unknown radio export definition: '$name'"))
