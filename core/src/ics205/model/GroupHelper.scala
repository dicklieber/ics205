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

import ics205.auth.User

case class GroupInfo(
  name: String,
  users: Seq[User],
  events: Seq[Ics205Event]
)

object GroupHelper:
  val defaultGroup: String = "Default"

  /**
   * Corrects group name capitalization to Caps words (Title Case).
   * E.g. "hello world" or "HELLO WORLD" will be converted to "Hello World".
   */
  def formatGroupName(name: String): String =
    val trimmed = name.trim
    if trimmed.isEmpty then ""
    else
      trimmed
        .split("\\s+")
        .filter(_.nonEmpty)
        .map(word => word.toLowerCase(java.util.Locale.ROOT).capitalize)
        .mkString(" ")

  def cleanGroupName(name: String): String =
    val formatted = formatGroupName(name)
    if formatted.isEmpty then defaultGroup else formatted

  def knownGroups(events: Seq[Ics205Event], users: Seq[User]): Set[String] =
    val fromEvents = events.map(_.group).filter(_.trim.nonEmpty)
    val fromUsers = users.flatMap(_.groups).filter(_.trim.nonEmpty)
    (fromEvents ++ fromUsers :+ defaultGroup).map(formatGroupName).filter(_.nonEmpty).toSet

  def groupInfos(events: Seq[Ics205Event], users: Seq[User]): Seq[GroupInfo] =
    val groups = knownGroups(events, users).toSeq.sortBy(_.toLowerCase(java.util.Locale.ROOT))
    val sortedUsers = users.sortBy(_.username.toLowerCase(java.util.Locale.ROOT))
    val sortedEvents = events.sortBy(e => (e.eventName.toLowerCase(java.util.Locale.ROOT), e.eventName))
    groups.map { groupName =>
      GroupInfo(
        name = groupName,
        users = sortedUsers.filter(_.groups.contains(groupName)),
        events = sortedEvents.filter(_.group.equalsIgnoreCase(groupName))
      )
    }
