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
import org.apache.logging.log4j.core.config.Configurator

import java.io.InputStream

object LoggingConfig extends LazyLogging:

  val configFileName: String = "log4j2.yaml"

  /**
   * Copies the bundled default log4j2.yaml resource to the destination directory
   * if it does not already exist. If the file already exists, it is NOT overwritten.
   *
   * @param dir the directory where log4j2.yaml should reside (typically FileHelper.directory)
   * @return Some(path) if the file exists or was successfully created, None otherwise
   */
  def ensureConfigFile(dir: os.Path): Option[os.Path] =
    val configFile = dir / configFileName
    if os.exists(configFile) then
      Some(configFile)
    else
      findDefaultResourceStream().flatMap { is =>
        try
          os.makeDir.all(dir)
          val bytes = is.readAllBytes()
          os.write(configFile, bytes)
          logger.info(s"Created default logging configuration at $configFile")
          Some(configFile)
        catch
          case ex: Exception =>
            logger.warn(s"Failed to create default logging configuration at $configFile: ${ex.getMessage}")
            None
        finally
          is.close()
      }

  private[util] def findDefaultResourceStream(): Option[InputStream] =
    Option(getClass.getResourceAsStream(s"/$configFileName"))
      .orElse(Option(getClass.getClassLoader.getResourceAsStream(configFileName)))
      .orElse(Option(Thread.currentThread().getContextClassLoader.getResourceAsStream(configFileName)))

  def findExternalConfig(dir: os.Path): Option[os.Path] =
    val configFile = dir / configFileName
    if os.exists(configFile) then Some(configFile) else None

  def init(dir: os.Path = FileHelper.directory): Option[os.Path] =
    ensureConfigFile(dir)
    val explicitProp = sys.props.get("log4j.configurationFile").orElse(sys.env.get("LOG4J_CONFIGURATION_FILE"))
    if explicitProp.isEmpty then
      findExternalConfig(dir).map { configFile =>
        System.setProperty("log4j.configurationFile", configFile.toString)
        Configurator.reconfigure(configFile.toNIO.toUri)
        logger.info(s"Loaded external logging configuration from $configFile")
        configFile
      }
    else
      None
