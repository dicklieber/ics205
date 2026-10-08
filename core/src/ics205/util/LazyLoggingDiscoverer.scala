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

package ics205.util

import com.typesafe.scalalogging.LazyLogging
import io.github.classgraph.ClassGraph

import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

object LazyLoggingDiscoverer extends LazyLogging:

  /**
   * Scans the classpath using ClassGraph to discover all concrete classes and objects
   * that implement [[com.typesafe.scalalogging.LazyLogging]].
   *
   * @param packages packages to accept during scan. If empty, scans all packages. Defaults to Seq("ics205").
   * @return sorted list of unique fully qualified class/logger names
   */
  def discoverLazyLoggers(packages: Seq[String] = Seq("ics205")): Seq[String] =
    val classGraph = new ClassGraph()
      .enableClassInfo()
      .ignoreClassVisibility()

    val scanner = if packages.isEmpty then classGraph else classGraph.acceptPackages(packages*)
    val scan = scanner.scan()

    try
      val target = classOf[LazyLogging].getName
      val classInfos = (scan.getClassesImplementing(target).asScala ++
        scan.getSubclasses(target).asScala).distinctBy(_.getName)

      val concrete = classInfos.filter(ci =>
        !ci.isAbstract && !ci.isInterface && !ci.isAnonymousInnerClass && !ci.isSynthetic
      )

      val discovered = concrete.map(_.getName).toSeq.distinct.sorted
      logger.debug(s"Discovered ${discovered.size} LazyLogging classes: ${discovered.mkString(", ")}")
      discovered
    catch
      case NonFatal(e) =>
        logger.error("Failed to discover LazyLogging classes via ClassGraph", e)
        Seq.empty
    finally
      scan.close()
