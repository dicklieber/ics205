package ics205.exporter

import ics205.model.{ChannelField, Frequency, Ics205Channel, RadioMode, RxWithOffset}
import io.circe.parser.decode
import io.circe.syntax.*

class RadioExportDefinitionTests extends munit.FunSuite:
  val sampleChannel = Ics205Channel(
    id = "1",
    zoneGroup = Some("Zone 1"),
    channelNumber = Some("CH-01"),
    function = "Tac 1",
    name = "TAC1",
    assignment = "Operations",
    frequency = RxWithOffset(Frequency(BigDecimal("146.520")), Frequency(BigDecimal("0.600"))),
    mode = RadioMode.Fm
  )

  test("Field with ChannelField round trips to and from JSON and extracts value"):
    val field = Field("Frequency", ChannelField.Rx)
    val json = field.asJson.noSpaces
    val decoded = decode[Field](json)
    assertEquals(decoded, Right(field))
    assertEquals(field.extract(sampleChannel, new RadioChannelNameBuilderDefault()), "146.520")

  test("Field with Constant round trips to and from JSON and extracts constant text"):
    val field = Field("Step", "5 kHz")
    val json = field.asJson.noSpaces
    val decoded = decode[Field](json)
    assertEquals(decoded, Right(field))
    assertEquals(field.extract(sampleChannel, new RadioChannelNameBuilderDefault()), "5 kHz")

  test("Field decodes 'literal' alias as Constant"):
    val json = """{"headerName": "Lockout", "literal": "Scan"}"""
    val decoded = decode[Field](json)
    assertEquals(decoded, Right(Field("Lockout", "Scan")))

  test("Field decoding fails when neither channelField nor constant is present"):
    val json = """{"headerName": "Invalid"}"""
    val decoded = decode[Field](json)
    assert(decoded.isLeft)

  test("Field extracts RadioChannelName via RadioChannelNameBuilder"):
    val field = Field("Name", ChannelField.RadioChannelName)
    val customBuilder = new RadioChannelNameBuilder:
      override def apply(ics205Channel: Ics205Channel): String = s"${ics205Channel.name}-${ics205Channel.assignment}"
    assertEquals(field.extract(sampleChannel, customBuilder), "TAC1-Operations")

  test("TH-D75.json resource decodes to valid RadioExportDefinition"):
    val source = scala.io.Source.fromResource("TH-D75.json")
    val json = try source.mkString finally source.close()
    val decoded = decode[RadioExportDefinition](json)
    assert(decoded.isRight, s"Failed to decode TH-D75.json: $decoded")
    val defn = decoded.toOption.get
    assertEquals(defn.name, "Kenwood TH-D75")
    assertEquals(defn.fields.length, 24)
    assertEquals(defn.fields.head.headerName, "Channel Number")
    assertEquals(defn.fields.head.value, FieldValue.FromChannel(ChannelField.ChannelNumber))

  test("FTM-510.json resource decodes to valid RadioExportDefinition"):
    val source = scala.io.Source.fromResource("FTM-510.json")
    val json = try source.mkString finally source.close()
    val decoded = decode[RadioExportDefinition](json)
    assert(decoded.isRight, s"Failed to decode FTM-510.json: $decoded")
    val defn = decoded.toOption.get
    assertEquals(defn.name, "Yaesu FTM-510")
    assertEquals(defn.fields.length, 21)
    assertEquals(defn.fields.head.headerName, "Channel Number")
    assertEquals(defn.fields.head.value, FieldValue.FromChannel(ChannelField.ChannelNumber))

  test("ID-52Plus.json resource decodes to valid RadioExportDefinition"):
    val source = scala.io.Source.fromResource("ID-52Plus.json")
    val json = try source.mkString finally source.close()
    val decoded = decode[RadioExportDefinition](json)
    assert(decoded.isRight, s"Failed to decode ID-52Plus.json: $decoded")
    val defn = decoded.toOption.get
    assertEquals(defn.name, "Icom ID-52Plus")
    assertEquals(defn.fields.length, 24)
    assertEquals(defn.fields.head.headerName, "Channel Number")
    assertEquals(defn.fields.head.value, FieldValue.FromChannel(ChannelField.ChannelNumber))
