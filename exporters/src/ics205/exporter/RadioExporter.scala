package ics205.exporter

import ics205.model.RadioPlan

trait RadioExporter:
  def doExport(plan: RadioPlan): String
