package ics205.exporter

import ics205.model.{ChannelField, Ics205Channel}
import io.circe.{Codec, DecodingFailure, HCursor, Json, JsonObject}
import io.circe.syntax.*

/**
 * Represents the definition for exporting radio data.
 *
 * This case class defines the structure of a radio export operation,
 * including the name of the export, the fields to be included in the export
 * output, and an optional builder for generating radio channel names.
 *
 * @param name                    The name representing this export definition.
 * @param fields                  A list of fields specifying the data to be included in the export.
 * @param radioChannelNameBuilder A component to build radio channel name, typically from several [[Ics205Channel]] fields. 
 * */
case class RadioExportDefinition(name: String,
                                 fields: List[Field],
                                 radioChannelNameBuilder: RadioChannelNameBuilder = new RadioChannelNameBuilderDefault())

object RadioExportDefinition:
  given Codec.AsObject[RadioExportDefinition] = Codec.AsObject.from(
    (c: HCursor) =>
      for
        name <- c.downField("name").as[String]
        fields <- c.downField("fields").as[List[Field]]
      yield RadioExportDefinition(name, fields),
    (red: RadioExportDefinition) =>
      JsonObject(
        "name" -> Json.fromString(red.name),
        "fields" -> Json.fromValues(red.fields.map(_.asJson))
      )
  )

enum FieldValue:
  case FromChannel(field: ChannelField)
  case Constant(text: String)

  def extract(channel: Ics205Channel, channelNameBuilder: RadioChannelNameBuilder): String =
    this match
      case FromChannel(field) =>
        if field == ChannelField.RadioChannelName then channelNameBuilder(channel)
        else field.value(channel)
      case Constant(text) =>
        text

/**
 * 
 * @param headerName in the CSV header row.
 * @param value what goes into that field in the CSV (either from a channel field or a constant string).
 */
case class Field(headerName: String,
                 value: FieldValue):
  def extract(channel: Ics205Channel, channelNameBuilder: RadioChannelNameBuilder): String =
    value.extract(channel, channelNameBuilder)

object Field:
  def apply(headerName: String, channelField: ChannelField): Field =
    Field(headerName, FieldValue.FromChannel(channelField))

  def apply(headerName: String, constant: String): Field =
    Field(headerName, FieldValue.Constant(constant))

  given Codec.AsObject[Field] = Codec.AsObject.from(
    (c: HCursor) =>
      for
        header <- c.downField("headerName").as[String]
        value <- c.downField("channelField").as[ChannelField].map(FieldValue.FromChannel.apply)
          .orElse(c.downField("constant").as[String].map(FieldValue.Constant.apply))
          .orElse(c.downField("literal").as[String].map(FieldValue.Constant.apply))
          .orElse(Left(DecodingFailure("Field must contain either 'channelField' or 'constant'", c.history)))
      yield Field(header, value),
    (f: Field) =>
      val base = JsonObject("headerName" -> Json.fromString(f.headerName))
      f.value match
        case FieldValue.FromChannel(cf) => base.add("channelField", cf.asJson)
        case FieldValue.Constant(str)   => base.add("constant", Json.fromString(str))
  )

trait RadioChannelNameBuilder:
  def apply(ics205Channel: Ics205Channel): String

class RadioChannelNameBuilderDefault() extends RadioChannelNameBuilder:
  private val maxLength = 16
  private val abbreviations = Map(
    "operations" -> "Ops", "operation" -> "Ops", "command" -> "Cmd",
    "medical" -> "Med", "emergency" -> "Emerg", "logistics" -> "Log",
    "communications" -> "Comms", "tactical" -> "Tac", "support" -> "Sup",
    "division" -> "Div", "branch" -> "Br", "group" -> "Grp",
    "administration" -> "Admin", "administrative" -> "Admin",
    "primary" -> "Pri", "secondary" -> "Sec", "alternate" -> "Alt",
    "north" -> "N", "south" -> "S", "east" -> "E", "west" -> "W",
    "central" -> "Ctr", "station" -> "Sta", "channel" -> "Ch"
  )
  private val word = "[A-Za-z]+".r

  private def normalize(value: String): String = value.trim.replaceAll("\\s+", " ")

  // Avoid leaving half a UTF-16 surrogate pair at the end of a truncated label.
  private def truncate(value: String, limit: Int): String =
    val result = value.take(limit).trim
    if result.nonEmpty && Character.isHighSurrogate(result.last) then result.dropRight(1) else result

  override def apply(ics205Channel: Ics205Channel): String =
    val name = normalize(ics205Channel.name)
    val assignment = normalize(ics205Channel.assignment)
    val separator = if name.nonEmpty then " " else ""
    val budget = maxLength - name.length - separator.length
    if assignment.isEmpty || budget <= 0 then truncate(name, maxLength)
    else
      val abbreviated = word.replaceAllIn(assignment, m =>
        abbreviations.getOrElse(m.matched.toLowerCase(java.util.Locale.ROOT), m.matched))
      val consonants = word.replaceAllIn(abbreviated, m =>
        m.matched.head.toString + m.matched.tail.replaceAll("(?i)[aeiou]", ""))
      // Only alphabetic runs become initials, retaining identifiers such as 16-20 or CMT1.
      val initials = word.replaceAllIn(abbreviated, m => m.matched.head.toString)
      val candidates = Seq(assignment, abbreviated, consonants, initials, initials.replace(" ", ""))
      val compact = candidates.find(_.length <= budget).getOrElse(truncate(candidates.last, budget))
      s"$name$separator$compact".trim
