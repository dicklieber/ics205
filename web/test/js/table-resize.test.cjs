const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');

// Minimal DOM harness: exercise events and grouped headers without a browser dependency.
class Element {
  constructor(text = '', width = 100) {
    this.textContent = text;
    this.width = width;
    this.children = [];
    this.dataset = {};
    this.colSpan = this.rowSpan = 1;
    this.events = {};
    this.attributes = {};
    const classes = new Set();
    this.classList = {add: c => classes.add(c), remove: c => classes.delete(c), contains: c => classes.has(c)};
    this.style = {setProperty: (k, v) => this.attributes[k] = v, removeProperty: k => delete this.attributes[k]};
  }
  append(child) { this.children.push(child); child.parent = this; }
  insertBefore(child) { this.append(child); }
  remove() { this.parent.children = this.parent.children.filter(c => c !== this); }
  setAttribute(k, v) { this.attributes[k] = v; }
  getBoundingClientRect() { return {width: this.width}; }
  addEventListener(k, fn) { (this.events[k] ||= new Set()).add(fn); }
  removeEventListener(k, fn) { this.events[k].delete(fn); }
  dispatchEvent(event) { this.events[event.type]?.forEach(fn => fn(event)); }
  setPointerCapture() {}
  fire(type, values = {}) { this.dispatchEvent({type, preventDefault() {}, stopPropagation() {}, ...values}); }
}
function setup(tables, lists = []) {
  const root = new Element();
  const document = {
    readyState: 'complete', documentElement: root, body: root,
    createElement: () => new Element(),
    querySelectorAll: selector => selector === 'table' ? tables : lists
  };
  let scan;
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../../resources/js/table-resize.js'), 'utf8'), {
    document, Event: class { constructor(type) { this.type = type; } },
    MutationObserver: class { constructor(fn) { scan = fn; } observe() {} }
  });
  return {root, scan};
}
function table(rows) { const t = new Element(); t.tHead = {rows: rows.map(cells => ({cells}))}; return t; }

test('grouped headers map to leaf columns; keyboard resize preserves total and reset removes overrides', () => {
  const a = new Element('Name'); a.rowSpan = 2;
  const group = new Element('Radio'); group.colSpan = 2;
  const b = new Element('RX'), c = new Element('TX');
  const t = table([[a, group], [b, c]]);
  const {scan} = setup([t]);
  assert.equal(group.children.length, 0);
  assert.equal(a.children.length, 1);
  assert.equal(b.children.length, 1);
  assert.equal(c.children.length, 0);
  scan();
  assert.equal(a.children.length, 1, 'initialization is idempotent');
  b.children[0].fire('keydown', {key: 'ArrowRight'});
  const cols = t.children[0].children;
  assert.equal(parseFloat(cols[1].attributes['--column-width']), 35);
  assert.ok(Math.abs(cols.reduce((n, col) => n + parseFloat(col.attributes['--column-width']), 0) - 100) < 0.001);
  b.children[0].fire('keydown', {key: 'Home'});
  assert.equal(t.children.length, 0);
  assert.equal(t.classList.contains('columns-resized'), false);
});

test('pointer drag clamps column widths and cleans up after cancellation', () => {
  const a = new Element('A'), b = new Element('B');
  const t = table([[a, b]]);
  const {root} = setup([t]);
  const grip = a.children[0];
  grip.fire('pointerdown', {button: 0, pointerId: 1, clientX: 100});
  grip.fire('pointermove', {clientX: 1000});
  assert.equal(parseFloat(t.children[0].children[1].attributes['--column-width']), 18);
  grip.fire('pointercancel');
  assert.equal(root.classList.contains('resizing-columns'), false);
  assert.equal(grip.events.pointermove.size, 0);
  grip.fire('dblclick');
  assert.equal(t.children.length, 0);
});

test('About dividers resize the whole list; inserted tables are initialized', () => {
  const label = new Element('Long label', 100), list = new Element('', 400);
  list.querySelectorAll = () => [label];
  const tables = [];
  const {scan} = setup(tables, [list]);
  label.children[0].fire('keydown', {key: 'ArrowRight', shiftKey: true});
  assert.equal(list.attributes['--label-width'], '31.25%');
  label.children[0].fire('dblclick');
  assert.equal(list.attributes['--label-width'], undefined);
  const a = new Element('New'), b = new Element('Table');
  tables.push(table([[a, b]]));
  scan();
  assert.equal(a.children.length, 1);
});
