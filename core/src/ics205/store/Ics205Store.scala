package ics205.store

import ics205.util.FileHelper
import ics205.model.{Ics205, OperationalPeriod}

import java.time.LocalDateTime
import jakarta.inject.{Inject, Singleton}

@Singleton
class Ics205Store @Inject()(fileHelper: FileHelper):
  private val fileName = "ics205.json"

  private var current: Ics205 = fileHelper.loadOrDefault[Ics205](fileName) {
    val now = LocalDateTime.now()
    Ics205(
      incidentName = "",
      operationalPeriod = OperationalPeriod(now, now),
      channels = Seq.empty
    )
  }

  def ics205(): Ics205 = synchronized { current }

  def save(value: Ics205): Unit = synchronized {
    fileHelper.save(fileName, value)
    current = value
  }
