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

import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.config.plugins.Plugin
import org.apache.logging.log4j.core.lookup.StrLookup

@Plugin(name = "fileHelper", category = StrLookup.CATEGORY)
class FileHelperLookup extends StrLookup:
  override def lookup(key: String): String =
    lookup(null, key)

  override def lookup(event: LogEvent, key: String): String =
    if key == null then
      FileHelper.directory.toString
    else
      key.toLowerCase match
        case "dir" | "directory" =>
          FileHelper.directory.toString
        case "logdir" | "logdirectory" | "log" =>
          FileHelper.logDirectory.toString
        case _ =>
          FileHelper.directory.toString
