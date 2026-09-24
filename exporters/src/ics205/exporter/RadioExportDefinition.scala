package ics205.exporter

import ics205.model.{ChannelField, Ics205Channel}

/**
 * Represents the definition for exporting radio data.
 *
 * This case class defines the structure of a radio export operation,
 * including the name of the export, the fields to be included in the export
 * output, and an optional builder for generating radio channel names.
 *
 * @param name                    The name representing this export definition.
 * @param fields                  A list of fields specifying the data to be included in the export.
 * @param radioChannelNameBuilder A component to build radio channel names, with a default configuration provided. */
case class RadioExportDefinition(name: String,
                                 fields: List[Field],
                                 radioChannelNameBuilder: RadioChannelNameBuilder = RadioChannelNameBuilder())

case class Field(headerName: String,
                 channelField: ChannelField)

case class RadioChannelNameBuilder(fields: List[ChannelField] = List.empty,
                                   maxLength: Int = 16):
  def apply(ics205Channel: Ics205Channel): RadioChannelNameBuilder = RadioChannelNameBuilder()