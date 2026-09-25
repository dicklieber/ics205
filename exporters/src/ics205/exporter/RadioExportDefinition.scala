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
  override def apply(ics205Channel: Ics205Channel): String =
    s"${ics205Channel.name} ${ics205Channel.assignment}"