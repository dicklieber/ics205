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
import io.github.classgraph.ClassGraph
import jakarta.inject.{Inject, Singleton}
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

@Singleton
class RadioExportDefinitions @Inject()() extends LazyLogging:

  val all: Seq[RadioExportDefinition] =
    RadioExportDefinitions.discoverDefinitions()

  private val byName: Map[String, RadioExportDefinition] =
    all.map(d => d.name -> d).toMap

  def listDefinitions: Seq[String] =
    all.map(_.name).distinct.sorted

  def get(name: String): RadioExportDefinition =
    byName.getOrElse(name, throw new IllegalArgumentException(s"Unknown radio export definition: '$name'"))

object RadioExportDefinitions extends LazyLogging:

  def discoverDefinitions(packages: Seq[String] = Seq("ics205")): Seq[RadioExportDefinition] =
    val scan = new ClassGraph()
      .enableClassInfo()
      .ignoreClassVisibility()
      .acceptPackages(packages*)
      .scan()

    try
      val targetTrait = classOf[RadioExportDefinitionProvider].getName
      val classInfos = (scan.getClassesImplementing(targetTrait).asScala ++
        scan.getSubclasses(targetTrait).asScala).distinctBy(_.getName)

      val concreteInfos = classInfos.filter(ci => !ci.isAbstract && !ci.isInterface)

      val definitions = concreteInfos.flatMap { ci =>
        try
          val cls = Class.forName(ci.getName)
          val instanceOpt: Option[RadioExportDefinitionProvider] =
            if ci.getName.endsWith("$") then
              try
                val moduleField = cls.getField("MODULE$")
                Some(moduleField.get(null).asInstanceOf[RadioExportDefinitionProvider])
              catch
                case NonFatal(e) =>
                  logger.warn(s"Failed to access MODULE$$ on ${ci.getName}", e)
                  None
            else
              try
                val companionClass = Class.forName(ci.getName + "$")
                val moduleField = companionClass.getField("MODULE$")
                Some(moduleField.get(null).asInstanceOf[RadioExportDefinitionProvider])
              catch
                case _: ClassNotFoundException | _: NoSuchFieldException =>
                  try
                    Some(cls.getDeclaredConstructor().newInstance().asInstanceOf[RadioExportDefinitionProvider])
                  catch
                    case NonFatal(e) =>
                      logger.warn(s"Failed to instantiate ${ci.getName}", e)
                      None

          instanceOpt.map(_.definition)
        catch
          case NonFatal(e) =>
            logger.warn(s"Failed to load radio export class ${ci.getName}", e)
            None
      }.toSeq.distinctBy(_.name).sortBy(_.name)

      logger.info(s"Discovered ${definitions.size} radio export definitions: ${definitions.map(_.name).mkString(", ")}")
      definitions
    finally
      scan.close()
