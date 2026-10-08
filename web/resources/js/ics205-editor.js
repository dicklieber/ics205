(() => {
  const form = document.getElementById('plan-form');
  const rows = document.getElementById('channel-rows');
  const status = document.getElementById('status');
  const paste = document.getElementById('paste-row');
  const clipboardStatus = document.getElementById('clipboard-status');
  const renumberBtns = document.querySelectorAll('.renumber-channels-btn, #renumber-channels');
  const renumberDialog = document.getElementById('channel-numbers-dialog');
  const renumberForm = document.getElementById('channel-numbers-form');
  const startingNumberInput = document.getElementById('starting-channel-number');
  const cancelBtn = document.getElementById('channel-numbers-cancel');
  let copiedChannel = null;
  let dirty = form.dataset.unsaved === 'true';
  function changed() { dirty = true; status.textContent = 'Unsaved changes'; }
  function refresh() {
    const list = [...rows.children];
    rows.closest('table').style.setProperty('--row-number-width', String(list.length).length + 'ch');
    list.forEach((row, index) => {
      row.querySelector('.row-number').textContent = index + 1;
      row.querySelectorAll('[data-field]').forEach(control => {
        control.name = 'row.' + index + '.' + control.dataset.field;
      });
      const isOther = row.querySelector('[data-field=mode]').value === 'Other';
      ['rx', 'offset'].forEach(field => {
        const control = row.querySelector('[data-field=' + field + ']');
        control.type = isOther ? 'text' : 'number';
        control.required = !isOther;
      });
      const frequency = row.querySelector('[data-field=ctcssFrequency]');
      const enabled = row.querySelector('[data-field=ctcssMode]').value !== 'None';
      frequency.hidden = !enabled;
      frequency.disabled = !enabled;
      frequency.required = enabled && !isOther;
      row.querySelector('[data-action=up]').disabled = index === 0;
      row.querySelector('[data-action=down]').disabled = index === list.length - 1;
      row.querySelectorAll('[data-action]').forEach(button => {
        button.setAttribute('aria-label', button.dataset.action + ' channel ' + (index + 1));
      });
    });
    document.getElementById('row-count').value = list.length;
    document.getElementById('row-status').textContent = list.length + (list.length === 1 ? ' channel' : ' channels');
  }
  function insertChannel(values = {}) {
    const row = document.getElementById('channel-template').content.firstElementChild.cloneNode(true);
    row.querySelectorAll('[data-field]').forEach(control => {
      if (control.dataset.field !== 'id' && Object.hasOwn(values, control.dataset.field)) {
        control.value = values[control.dataset.field];
      }
    });
    row.querySelector('[data-field=id]').value = crypto.randomUUID();
    rows.append(row);
    refresh();
    changed();
    row.querySelector('[data-field=name]').focus();
  }
  document.getElementById('add-row').addEventListener('click', () => insertChannel());
  paste.addEventListener('click', () => {
    if (!copiedChannel) return;
    insertChannel(copiedChannel);
    clipboardStatus.textContent = 'Channel pasted at the end. Save plan to keep it.';
  });
  function applyChannelNumbers(startNum) {
    const list = [...rows.children];
    list.forEach((row, index) => {
      const control = row.querySelector('[data-field=channelNumber]');
      if (control) {
        control.value = String(startNum + index);
      }
    });
    changed();
  }
  if (renumberDialog) {
    renumberBtns.forEach(btn => {
      btn.addEventListener('click', () => {
        if (typeof renumberDialog.showModal === 'function') {
          renumberDialog.showModal();
          if (startingNumberInput) {
            startingNumberInput.focus();
            startingNumberInput.select();
          }
        } else {
          const val = prompt('Starting channel number:', startingNumberInput ? startingNumberInput.value : '1');
          if (val !== null) {
            const startNum = parseInt(val, 10);
            if (!isNaN(startNum)) {
              applyChannelNumbers(startNum);
            }
          }
        }
      });
    });
  }
  if (renumberForm) {
    renumberForm.addEventListener('submit', event => {
      event.preventDefault();
      if (startingNumberInput) {
        const startNum = parseInt(startingNumberInput.value, 10);
        if (!isNaN(startNum)) {
          applyChannelNumbers(startNum);
        }
      }
      if (typeof renumberDialog.close === 'function') {
        renumberDialog.close();
      }
    });
  }
  if (cancelBtn && renumberDialog) {
    cancelBtn.addEventListener('click', () => {
      if (typeof renumberDialog.close === 'function') {
        renumberDialog.close();
      }
    });
  }
  rows.addEventListener('click', event => {
    const button = event.target.closest('[data-action]');
    if (!button) return;
    const row = button.closest('tr');
    const action = button.dataset.action;
    if (action === 'copy') {
      copiedChannel = Object.fromEntries(
        [...row.querySelectorAll('[data-field]')]
          .filter(control => control.dataset.field !== 'id')
          .map(control => [control.dataset.field, control.value])
      );
      paste.disabled = false;
      clipboardStatus.textContent = 'Channel copied. Use Paste channel to add a copy.';
      return;
    }
    if (action === 'up' && row.previousElementSibling) rows.insertBefore(row, row.previousElementSibling);
    if (action === 'down' && row.nextElementSibling) rows.insertBefore(row.nextElementSibling, row);
    if (action === 'delete') {
      const next = row.nextElementSibling || row.previousElementSibling;
      row.remove();
      (next ? next.querySelector('[data-field=name]') : document.getElementById('add-row')).focus();
    } else row.querySelector('[data-field=name]').focus();
    refresh();
    changed();
  });
  const importBtn = document.getElementById('import-json');
  const fileInput = document.getElementById('import-json-file');
  const clientError = document.getElementById('client-error');

  function showError(msg) {
    if (clientError) {
      clientError.textContent = msg;
      clientError.style.display = 'block';
      clientError.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    } else {
      alert(msg);
    }
    if (status) {
      status.textContent = '';
    }
  }

  function hideError() {
    if (clientError) {
      clientError.textContent = '';
      clientError.style.display = 'none';
    }
  }

  if (importBtn && fileInput) {
    importBtn.addEventListener('click', () => {
      fileInput.click();
    });

    fileInput.addEventListener('change', event => {
      const file = event.target.files && event.target.files[0];
      if (!file) return;

      const reader = new FileReader();
      reader.onload = e => {
        try {
          let json;
          try {
            json = JSON.parse(e.target.result);
          } catch (parseErr) {
            showError('Failed to parse JSON file: ' + parseErr.message);
            return;
          }

          if (!json || typeof json !== 'object' || Array.isArray(json)) {
            showError('Invalid JSON format: expected an object representing an ICS 205 plan or event.');
            return;
          }

          const plan = (json.ics205 && typeof json.ics205 === 'object' && !Array.isArray(json.ics205)) ? json.ics205 : json;

          if (plan.channels !== undefined && !Array.isArray(plan.channels)) {
            showError('Invalid ICS 205 data: "channels" must be an array.');
            return;
          }

          if (plan.incidentName === undefined && plan.channels === undefined && !plan.formatVersion) {
            showError('Invalid ICS 205 data: file does not contain ICS 205 plan data.');
            return;
          }

          hideError();

          // Populate top-level fields
          const incidentNameInput = form.querySelector('[name=incidentName]');
          if (incidentNameInput && plan.incidentName !== undefined) {
            incidentNameInput.value = plan.incidentName || '';
          }

          const preparedInput = form.querySelector('[name=prepared]');
          if (preparedInput && plan.prepared !== undefined) {
            preparedInput.value = plan.prepared || '';
          }

          const fromInput = form.querySelector('[name=from]');
          if (fromInput) {
            const fromVal = plan.operationalPeriod?.from || plan.from || '';
            fromInput.value = fromVal;
          }

          const toInput = form.querySelector('[name=to]');
          if (toInput) {
            const toVal = plan.operationalPeriod?.to || plan.to || '';
            toInput.value = toVal;
          }

          const specialInstructionsTextarea = form.querySelector('[name=specialInstructions]');
          if (specialInstructionsTextarea && plan.specialInstructions !== undefined) {
            specialInstructionsTextarea.value = plan.specialInstructions || '';
          }

          const preparedByInput = form.querySelector('[name=preparedBy]');
          if (preparedByInput) {
            const prepName = plan.preparedBy?.name ?? (typeof plan.preparedBy === 'string' ? plan.preparedBy : '') ?? '';
            preparedByInput.value = prepName;
          }

          const callsignInput = form.querySelector('[name=callsign]');
          if (callsignInput) {
            const callsignVal = plan.preparedBy?.callsign ?? plan.callsign ?? '';
            callsignInput.value = callsignVal;
          }

          // Replace channel rows
          rows.replaceChildren();
          const rawChannels = Array.isArray(plan.channels) ? plan.channels : [];
          // Exported JSON encodes enum cases as objects, e.g. {"Fm": {}}; selects need the bare case name.
          const enumName = (v, fallback) => {
            if (v && typeof v === 'object') return Object.keys(v)[0] ?? fallback;
            return v || fallback;
          };
          rawChannels.forEach(ch => {
            const rxFreq = ch.frequency?.rxFrequency?.mhz ?? ch.frequency?.rx?.mhz ?? ch.frequency?.rxFrequency ?? ch.frequency?.rx ?? ch.rx ?? '';
            const offsetFreq = ch.frequency?.offset?.mhz ?? ch.frequency?.offset ?? ch.offset ?? '0';
            const ctcssFreq = ch.ctcss?.frequency?.hz ?? ch.ctcss?.frequency ?? ch.ctcssFrequency ?? '';
            const ctcssMode = enumName(ch.ctcss?.mode ?? ch.ctcssMode, 'None');

            const rowValues = {
              id: ch.id || crypto.randomUUID(),
              zoneGroup: ch.zoneGroup || '',
              channelNumber: ch.channelNumber || '',
              function: ch.function || '',
              name: ch.name || '',
              extra: ch.extra || '',
              assignment: ch.assignment || '',
              rx: String(rxFreq),
              offset: String(offsetFreq),
              bandwidth: enumName(ch.bandwidth, 'Wide'),
              ctcssMode: ctcssMode,
              ctcssFrequency: String(ctcssFreq),
              mode: enumName(ch.mode, 'Fm'),
              remarks: ch.remarks || ''
            };

            const row = document.getElementById('channel-template').content.firstElementChild.cloneNode(true);
            row.querySelectorAll('[data-field]').forEach(control => {
              const field = control.dataset.field;
              if (Object.hasOwn(rowValues, field)) {
                control.value = rowValues[field];
              }
              // Tone option values look like "100.0" while JSON numbers print as "100"; match numerically.
              if (field === 'ctcssFrequency' && control.value === '' && rowValues.ctcssFrequency !== '') {
                const hz = Number(rowValues.ctcssFrequency);
                const match = [...control.options].find(o => o.value !== '' && Number(o.value) === hz);
                if (match) control.value = match.value;
              }
            });
            rows.append(row);
          });

          refresh();
          changed();
          status.textContent = 'Unsaved changes (imported ' + file.name + ')';
          const eventIdInput = form.querySelector('[name=eventId]');
          const eventNameInput = form.querySelector('[name=eventName]');
          const currentEventName = (eventNameInput && eventNameInput.value) ? eventNameInput.value : (eventIdInput ? eventIdInput.value : '');
          fetch('/import/log', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              eventName: currentEventName || plan.incidentName || '',
              incidentName: plan.incidentName || '',
              channelCount: rawChannels.length,
              fileName: file.name,
              format: 'json'
            })
          }).catch(() => {});
        } catch (err) {
          showError('Error processing ICS 205 import: ' + err.message);
        } finally {
          fileInput.value = '';
        }
      };
      reader.onerror = () => {
        showError('Failed to read file: ' + file.name);
        fileInput.value = '';
      };
      reader.readAsText(file);
    });
  }
  form.addEventListener('input', changed);
  form.addEventListener('change', () => { refresh(); changed(); });
  let invalidReported = false;
  form.addEventListener('invalid', event => {
    if (invalidReported) return;
    invalidReported = true;
    setTimeout(() => { invalidReported = false; }, 0);
    const control = event.target;
    const row = control.closest('.channel-row');
    const where = row ? 'Channel ' + row.querySelector('.row-number').textContent + ', ' : '';
    const caption = control.getAttribute('aria-label') || control.name;
    showError('Not saved: ' + where + caption + ': ' + control.validationMessage);
    if (control.hidden) control.hidden = false;
    control.scrollIntoView({ behavior: 'smooth', block: 'center', inline: 'center' });
  }, true);
  form.addEventListener('submit', () => {
    hideError();
    refresh();
    dirty = false;
  });
  window.addEventListener('beforeunload', event => {
    if (dirty) { event.preventDefault(); event.returnValue = ''; }
  });
  refresh();
})();
