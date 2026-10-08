(() => {
  function getEditorIcs205() {
    const planForm = document.getElementById('plan-form');
    const incidentName = planForm ? (planForm.querySelector('[name=incidentName]')?.value || '') : '';
    const prepared = planForm ? (planForm.querySelector('[name=prepared]')?.value || new Date().toISOString().slice(0, 19)) : new Date().toISOString().slice(0, 19);
    const from = planForm ? (planForm.querySelector('[name=from]')?.value || null) : null;
    const to = planForm ? (planForm.querySelector('[name=to]')?.value || null) : null;
    const specialInstructions = planForm ? (planForm.querySelector('[name=specialInstructions]')?.value || '') : '';
    const preparedByName = planForm ? (planForm.querySelector('[name=preparedBy]')?.value || '') : '';
    const callsign = planForm ? (planForm.querySelector('[name=callsign]')?.value || null) : null;

    const rows = document.getElementById('channel-rows');
    const channelRows = rows ? [...rows.children] : [];
    const channels = channelRows.map(row => {
      const id = row.querySelector('[data-field=id]')?.value || crypto.randomUUID();
      const zoneGroup = row.querySelector('[data-field=zoneGroup]')?.value || null;
      const channelNumber = row.querySelector('[data-field=channelNumber]')?.value || null;
      const func = row.querySelector('[data-field=function]')?.value || '';
      const name = row.querySelector('[data-field=name]')?.value || '';
      const extra = row.querySelector('[data-field=extra]')?.value || '';
      const assignment = row.querySelector('[data-field=assignment]')?.value || '';
      const rxVal = row.querySelector('[data-field=rx]')?.value;
      const offsetVal = row.querySelector('[data-field=offset]')?.value;
      const mode = row.querySelector('[data-field=mode]')?.value || 'Fm';
      const bandwidth = row.querySelector('[data-field=bandwidth]')?.value || 'Wide';
      const ctcssMode = row.querySelector('[data-field=ctcssMode]')?.value || 'None';
      const ctcssFreqVal = row.querySelector('[data-field=ctcssFrequency]')?.value;
      const remarks = row.querySelector('[data-field=remarks]')?.value || '';

      const rx = (rxVal !== undefined && rxVal !== '' && !isNaN(Number(rxVal))) ? Number(rxVal) : 0;
      const offset = (offsetVal !== undefined && offsetVal !== '' && !isNaN(Number(offsetVal))) ? Number(offsetVal) : 0;
      const ctcssFreq = (ctcssMode !== 'None' && ctcssFreqVal && !isNaN(Number(ctcssFreqVal))) ? Number(ctcssFreqVal) : null;

      const channelObj = {
        function: func,
        name: name,
        assignment: assignment,
        frequency: {
          rxFrequency: { mhz: rx },
          offset: { mhz: offset }
        },
        mode: mode,
        bandwidth: bandwidth,
        ctcss: {
          frequency: ctcssFreq,
          mode: ctcssMode
        },
        remarks: remarks,
        extra: extra,
        id: id
      };
      if (zoneGroup) channelObj.zoneGroup = zoneGroup;
      if (channelNumber) channelObj.channelNumber = channelNumber;
      return channelObj;
    });

    const obj = {
      formatVersion: '1.0',
      incidentName: incidentName,
      operationalPeriod: {},
      channels: channels,
      specialInstructions: specialInstructions,
      prepared: prepared
    };
    if (from) obj.operationalPeriod.from = from;
    if (to) obj.operationalPeriod.to = to;
    if (preparedByName || callsign) {
      obj.preparedBy = { name: preparedByName };
      if (callsign) obj.preparedBy.callsign = callsign;
    }
    return obj;
  }

  const exportBtn = document.getElementById('export-json');
  if (exportBtn) {
    exportBtn.addEventListener('click', () => {
      const plan = getEditorIcs205();
      const prettyJson = JSON.stringify(plan, null, 2);
      const blob = new Blob([prettyJson], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      const rawName = plan.incidentName ? plan.incidentName.trim() : '';
      const sanitized = rawName.replace(/[\\/:*?"<>|]/g, '_');
      a.download = (sanitized.length > 0 ? sanitized : 'ics205') + '.json';
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
      const eventIdInput = document.querySelector('[name=eventId]');
      const eventNameInput = document.querySelector('[name=eventName]');
      const currentEventName = (eventNameInput && eventNameInput.value) ? eventNameInput.value : (eventIdInput ? eventIdInput.value : '');
      fetch('/export/log', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          eventName: currentEventName || plan.incidentName || '',
          incidentName: plan.incidentName || '',
          channelCount: Array.isArray(plan.channels) ? plan.channels.length : 0,
          format: 'json'
        })
      }).catch(() => {});
    });
  }
})();
