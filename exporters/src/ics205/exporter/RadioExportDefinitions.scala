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
import io.circe.parser.decode
import io.github.classgraph.ClassGraph
import jakarta.inject.{Inject, Singleton}

import scala.jdk.CollectionConverters.*

@Singleton
class RadioExportDefinitions @Inject()() extends LazyLogging:

  private val (byName, byBaseName) = loadAll()

  def listDefinitions: Seq[String] =
    byName.keys.toSeq.sorted

  def get(name: String): RadioExportDefinition =
    byName.get(name)
      .orElse(byBaseName.get(name))
      .orElse(byBaseName.get(resolveBaseName(name)))
      .getOrElse(throw new IllegalArgumentException(s"Unknown radio export definition: '$name'"))

  private def resolveBaseName(name: String): String =
    val stripped = if name.startsWith("/") then name.substring(1) else name
    if stripped.endsWith(".json") then stripped.stripSuffix(".json") else stripped

  private def loadAll(): (Map[String, RadioExportDefinition], Map[String, RadioExportDefinition]) =
    val scan = new ClassGraph().scan()
    try
      val jsonResources = scan.getResourcesWithExtension("json").asScala
      val loaded = jsonResources.flatMap { res =>
        val content = res.getContentAsString
        val baseName = res.getPath.split('/').last.stripSuffix(".json")
        decode[RadioExportDefinition](content) match
          case Right(definition) =>
            Some((definition.name -> definition, baseName -> definition))
          case Left(_) =>
            None
      }.toList

      val namesMap = loaded.map(_._1).toMap
      logger.debug(s"namesMap: $namesMap")
      val baseNamesMap = loaded.map(_._2).toMap
      logger.debug(s"baseNamesMap: ${baseNamesMap}")

      (namesMap, baseNamesMap)
    finally
      scan.close()
