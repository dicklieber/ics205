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
import ics205.BuildInfo
import io.circe.parser.*
import io.circe.syntax.*
import io.circe.{Decoder, Encoder, Printer}

import java.nio.file.NoSuchFileException
import jakarta.inject.Inject

object FileHelper:
  def appHome(appName: String = BuildInfo.appName, productName: String = BuildInfo.productName): os.Path =
    val osName = System.getProperty("os.name", "").toLowerCase

    if osName.contains("win") then
      os.home / "AppData" / "Local" / appName
    else if osName.contains("mac") then
      os.home / "Library" / "Application Support" / appName
    else
      os.Path(s"/home/$productName/data")

  def configHome(appName: String = BuildInfo.appName, productName: String = BuildInfo.productName): os.Path =
    val osName = System.getProperty("os.name", "").toLowerCase

    if osName.contains("win") then
      os.home / "AppData" / "Local" / appName / "config"
    else if osName.contains("mac") then
      os.home / "Library" / "Application Support" / appName / "config"
    else
      os.Path(s"/home/$productName/config")

  def isTestExecution: Boolean =
    sys.props.get("ics205.test").contains("true") ||
    sys.props.contains("munit.suite") ||
    Thread.currentThread().getStackTrace.exists { elem =>
      val name = elem.getClassName
      name.startsWith("munit.") ||
      name.startsWith("org.junit.") ||
      name.startsWith("org.scalameta.munit.") ||
      name.contains(".munit.") ||
      name.contains("TestRunner") ||
      name.contains("mill.testrunner")
    }

  def defaultDirectory(appName: String = BuildInfo.appName, productName: String = BuildInfo.productName): os.Path =
    if isTestExecution then
      os.temp.dir(prefix = "ics205-test-")
    else
      appHome(appName, productName)

  def defaultConfigDirectory(appName: String = BuildInfo.appName, productName: String = BuildInfo.productName): os.Path =
    if isTestExecution then
      os.temp.dir(prefix = "ics205-test-config-")
    else
      configHome(appName, productName)

  private var activeDir: Option[os.Path] = None
  private var activeConfigDir: Option[os.Path] = None

  def setDirectory(dir: os.Path): Unit =
    activeDir = Some(dir)

  def directory: os.Path =
    activeDir.getOrElse(defaultDirectory())

  def setConfigDirectory(dir: os.Path): Unit =
    activeConfigDir = Some(dir)

  def configDirectory: os.Path =
    activeConfigDir.getOrElse(defaultConfigDirectory())

  def logDirectory: os.Path =
    val dir = directory / "log"
    os.makeDir.all(dir)
    dir

  /**
   * Creates a ZIP archive of all files and directories inside the given directory (defaulting to [[FileHelper.directory]]).
   *
   * @param dir the directory to zip (defaults to [[FileHelper.directory]])
   * @return byte array containing the ZIP archive
   */
  def zipDirectory(dir: os.Path = directory): Array[Byte] =
    val baos = new java.io.ByteArrayOutputStream()
    val zos = new java.util.zip.ZipOutputStream(baos)
    try
      if os.exists(dir) then
        val allPaths = os.walk(dir)
        for path <- allPaths do
          val relPath = path.relativeTo(dir).segments.mkString("/")
          if os.isDir(path) then
            if relPath.nonEmpty then
              val entry = new java.util.zip.ZipEntry(s"$relPath/")
              entry.setTime(java.nio.file.Files.getLastModifiedTime(path.toNIO).toMillis)
              zos.putNextEntry(entry)
              zos.closeEntry()
          else
            val entry = new java.util.zip.ZipEntry(relPath)
            entry.setTime(java.nio.file.Files.getLastModifiedTime(path.toNIO).toMillis)
            zos.putNextEntry(entry)
            val is = java.nio.file.Files.newInputStream(path.toNIO)
            try is.transferTo(zos)
            finally is.close()
            zos.closeEntry()
      zos.finish()
      zos.flush()
      baos.toByteArray
    finally
      zos.close()

/** A utility class for handling file-related operations, such as reading and writing JSON-encoded
  * data to files, and managing application-specific directory paths.
  */
class FileHelper(customDir: Option[os.Path] = None, customConfigDir: Option[os.Path] = None) extends LazyLogging:

  @Inject() def this() = this(None, None)

  def this(customPath: os.Path) = this(Some(customPath), None)

  def this(customPath: os.Path, customConfigPath: os.Path) = this(Some(customPath), Some(customConfigPath))

  /** One application-owned directory tree for all ICS-205 files.
    *
    * Platform conventions used here:
    *   - Windows: %LOCALAPPDATA%\ICS-205
    *   - macOS:   ~/Library/Application Support/ICS-205
    *   - Linux:   /home/ics205/data
    *
    * In test execution, an isolated directory is used so unit tests never touch
    * the production directory.
    */
  val directory: os.Path = customDir.getOrElse(FileHelper.defaultDirectory())
  FileHelper.activeDir = Some(directory)
  val configDirectory: os.Path = customConfigDir.orElse(customDir.map(_ / "config")).getOrElse(FileHelper.defaultConfigDirectory())
  FileHelper.activeConfigDir = Some(configDirectory)
  val logDirectory: os.Path =
    val dir = directory / "log"
    os.makeDir.all(dir)
    dir
  logger.info(s"Data directory: $directory, Config directory: $configDirectory")

  def loadOrDefault[T: Decoder](fileName: String)(default: => T): T =

    val path = directory / fileName
    try
      val sJson: String = os.read(path)
      val r: T = parse(sJson).flatMap(_.as[T]).fold(
        err =>
          logger.error("Failed to parse/decode JSON", err, "File" -> fileName)
          default
        ,
        identity
      )
      r
    catch
      case _: NoSuchFileException =>
        logger.debug("File not found, using default", "File" -> fileName)
        default
      case e: Exception =>
        logger.error("Failed to read file", e, "File" -> fileName)
        default

  def save[T: Encoder](fileName: String, value: T): Unit =
    val path = directory / fileName
    val json = value.asJson.printWith(Printer.indented("  ").copy(dropNullValues = true))
    os.write.over(path, json, createFolders = true)

  def remove(fileName: String): Unit =
    val path = directory / fileName
    os.remove(path)

  /**
   * Creates a ZIP archive of all files and directories inside the given directory (defaulting to this instance's [[directory]]).
   *
   * @param dir the directory to zip (defaults to this instance's [[directory]])
   * @return byte array containing the ZIP archive
   */
  def zipDirectory(dir: os.Path = directory): Array[Byte] =
    FileHelper.zipDirectory(dir)
