/* Shared column resizing for tables and the About dialog's definition lists. */
(() => {
  'use strict';
  const minimum = 36;

  function handle(parent, label, begin, reset) {
    parent.classList.add('column-resize-cell');
    const grip = document.createElement('span');
    grip.className = 'column-resize-handle';
    grip.tabIndex = 0;
    grip.setAttribute('role', 'separator');
    grip.setAttribute('aria-orientation', 'vertical');
    grip.setAttribute('aria-label', `Resize ${label.trim()} column`);
    grip.title = 'Drag to resize; arrow keys adjust; double-click or Home resets';
    parent.append(grip);
    grip.addEventListener('click', event => event.stopPropagation());
    grip.addEventListener('dblclick', reset);
    grip.addEventListener('keydown', event => {
      if (event.key === 'Home') { event.preventDefault(); reset(); return; }
      if (!['ArrowLeft', 'ArrowRight'].includes(event.key)) return;
      event.preventDefault();
      begin()((event.key === 'ArrowRight' ? 1 : -1) * (event.shiftKey ? 25 : 5));
    });
    grip.addEventListener('pointerdown', event => {
      if (event.button !== 0) return;
      event.preventDefault();
      event.stopPropagation();
      const update = begin();
      const start = event.clientX;
      grip.setPointerCapture(event.pointerId);
      document.documentElement.classList.add('resizing-columns');
      const move = next => update(next.clientX - start);
      const finish = () => {
        grip.removeEventListener('pointermove', move);
        grip.removeEventListener('pointerup', finish);
        grip.removeEventListener('pointercancel', finish);
        grip.removeEventListener('lostpointercapture', finish);
        document.documentElement.classList.remove('resizing-columns');
      };
      grip.addEventListener('pointermove', move);
      grip.addEventListener('pointerup', finish);
      grip.addEventListener('pointercancel', finish);
      grip.addEventListener('lostpointercapture', finish);
    });
  }

  function tableResize(table) {
    if (table.dataset.columnResizeReady) return;
    const rows = Array.from(table.tHead ? table.tHead.rows : []).filter(row => row.cells.length);
    if (!rows.length) return;
    table.dataset.columnResizeReady = 'true';
    // Map leaf headers to physical columns, including grouped/row-spanning headers.
    const occupied = [];
    const leaves = [];
    rows.forEach((row, r) => {
      occupied[r] ||= [];
      let c = 0;
      Array.from(row.cells).forEach(cell => {
        while (occupied[r][c]) c++;
        for (let y = r; y < r + (cell.rowSpan || rows.length - r); y++) {
          occupied[y] ||= [];
          for (let x = c; x < c + cell.colSpan; x++) occupied[y][x] = true;
        }
        if (cell.colSpan === 1) leaves[c] = cell;
        c += cell.colSpan;
      });
    });
    const count = occupied[0].length;
    if (count < 2 || Array.from({length: count}, (_, i) => !leaves[i]).some(Boolean)) return;
    let group;
    function reset() {
      group?.remove();
      group = undefined;
      table.classList.remove('columns-resized');
    }
    function begin(index) {
      const widths = leaves.map(cell => cell.getBoundingClientRect().width);
      const total = widths.reduce((a, b) => a + b, 0);
      if (!group) {
        group = document.createElement('colgroup');
        group.className = 'resize-columns';
        widths.forEach(() => group.append(document.createElement('col')));
        table.insertBefore(group, table.tHead);
      }
      table.classList.add('columns-resized');
      const apply = () => widths.forEach((width, i) => {
        group.children[i].style.setProperty('--column-width', `${width / total * 100}%`);
      });
      apply();
      const left = widths[index], right = widths[index + 1];
      return delta => {
        const limit = Math.min(minimum, (left + right) / 2);
        const bounded = Math.max(limit - left, Math.min(right - limit, delta));
        widths[index] = left + bounded;
        widths[index + 1] = right - bounded;
        apply();
        table.dispatchEvent(new Event('columnresize'));
      };
    }
    leaves.slice(0, -1).forEach((cell, index) => {
      handle(cell, cell.textContent, () => begin(index), reset);
    });
  }

  function definitionResize(list) {
    if (list.dataset.columnResizeReady) return;
    list.dataset.columnResizeReady = 'true';
    list.querySelectorAll(':scope > .about-field > dt').forEach(label => {
      handle(label, 'labels', () => {
        const width = list.getBoundingClientRect().width;
        const initial = label.getBoundingClientRect().width;
        return delta => list.style.setProperty('--label-width',
          `${Math.max(10, Math.min(90, (initial + delta) / width * 100))}%`);
      }, () => list.style.removeProperty('--label-width'));
    });
  }

  function scan() {
    document.querySelectorAll('table').forEach(tableResize);
    document.querySelectorAll('.about-dl').forEach(definitionResize);
  }
  function start() {
    scan();
    // Newly inserted tables are enhanced too; added editor rows inherit column widths.
    new MutationObserver(scan).observe(document.body, {childList: true, subtree: true});
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start);
  else start();
})();
