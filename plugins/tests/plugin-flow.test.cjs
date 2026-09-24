const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');

// Minimal DOM double for testing event/data flow; visual checks still run in R7.
class Element {
    constructor(tag = 'div') {
        this.tag = tag; this.children = []; this.listeners = {}; this.value = ''; this.textContent = ''; this.disabled = false; this.scrollTop = 0;
        const classes = new Set();
        this.classList = { add: c => classes.add(c), remove: c => classes.delete(c), contains: c => classes.has(c) };
    }
    append(...children) { this.children.push(...children); }
    replaceChildren(...children) { this.children = children; this.scrollTop = 0; }
    setAttribute(name, value) { this[name] = value; }
    addEventListener(name, callback) { (this.listeners[name] ||= []).push(callback); }
    async click() { if (!this.disabled) await Promise.all((this.listeners.click || []).map(fn => fn())); }
    async change(checked) { this.checked = checked; await Promise.all((this.listeners.change || []).map(fn => fn())); }
}
function all(root) { return [root, ...root.children.flatMap(all)]; }
function bookRow(ui, name) { return ui.elements.listCurrent.children.find(row => row.children[1]?.textContent === name); }
function savedRow(ui, name) { return ui.elements.listSaved.children.find(row => row.children[1]?.textContent === name); }
function universalBox(row) { return row.children.find(child => child.className === 'universal-label').children[0]; }
function ids(ui) { return ui.current.macrosArray.map(m => m.guid); }
const macro = value => ({ guid: 'one', name: 'One', value, isUniversal: true });
const documentOf = value => ({ macrosArray: [macro(value)], current: 0 });
async function harness(action = 'push', conflict = false, emptyBook = false, serverUnavailable = false, withSeparator = false, reviewData = {}) {
    const pluginDirectory = path.join(__dirname, '../Macros Sync');
    const html = fs.readFileSync(path.join(pluginDirectory, 'index.html'), 'utf8');
    const elements = Object.fromEntries([...html.matchAll(/id="([^"]+)"/g)].map(match => [match[1], new Element()]));
    const body = new Element('body');
    let current = emptyBook ? null : documentOf('document'), writes = 0, closes = 0, rejectOrder = false;
    if (withSeparator) current.macrosArray.push(
        { guid: '00000000-separator-0000-000000000000', name: ' ', value: '', isSeparator: true },
        { guid: 'personal', name: 'Personal', value: 'local', isUniversal: false }
    );
    const calls = [];
    const settings = new Map([['macrosSync_dirPath', 'shared']]);
    const document = { body, getElementById: id => { if (!elements[id]) throw Error('Missing UI element: ' + id); return elements[id]; },
        createElement: tag => new Element(tag), createTextNode: text => { const node = new Element('text'); node.textContent = text; return node; } };
    const expected = emptyBook ? { macrosArray: [] } : structuredClone(current);
    const source = { macrosArray: [macro('library')], _r7Library: { revisionId: 'revision', libraryId: 'library' } };
    const result = macro('merged');
    let context;
    const plugin = {
        callCommand(fn, a, b, callback) { callback(vm.runInContext('(' + fn.toString() + ')()', context)); },
        executeCommand(command) { assert.equal(command, 'close'); closes++; }
    };
    const window = { Asc: { plugin, scope: {} }, getComputedStyle: () => ({ lineHeight: '18px' }) };
    context = vm.createContext({
        window, Asc: window.Asc, document, console, setTimeout, clearTimeout,
        localStorage: { getItem: key => settings.get(key), setItem: (key, value) => settings.set(key, value) },
        Api: { pluginMethod_GetMacros: () => current === null ? undefined : JSON.stringify(current), pluginMethod_SetMacros: json => { current = JSON.parse(json); writes++; } },
        fetch: async (url, options) => {
            if (serverUnavailable) throw new TypeError('Failed to fetch');
            const request = JSON.parse(options.body); calls.push({ url, request });
            let data;
            if (url.endsWith('/state')) data = { library: source, managed: true, externalChanges: false };
            else if (url.endsWith('/order')) {
                if (rejectOrder) return { ok: false, status: 409, json: async () => ({ message: 'Library order changed' }) };
                assert.equal(request.revisionId, source._r7Library.revisionId);
                assert.deepEqual(request.expectedGuids, source.macrosArray.map(m => m.guid));
                const byGuid = new Map(source.macrosArray.map(m => [m.guid, m]));
                source.macrosArray = request.orderedGuids.map(guid => byGuid.get(guid));
                data = { library: source, managed: true, externalChanges: false };
            }
            else if (url.endsWith('/preview')) data = { token: 'token', operationId: 'operation', changes: [{
                guid: 'one', name: 'One', kind: conflict ? 'conflict' : emptyBook ? 'added' : 'modified', base: null,
                document: emptyBook ? null : macro('document'), library: macro('library'), result: reviewData.result || result,
                conflicts: conflict ? ['base'] : [], chunks: [], diff: reviewData.diff || [{ kind: 'added', text: 'merged', oldLine: null, newLine: 1 }]
            }] };
            else if (url.endsWith('/apply')) data = action === 'push' ? { revisionId: 'saved', changed: true } : {
                document: documentOf('merged'), expectedDocument: expected, backupPath: 'backups/one.json', changed: true
            };
            else throw Error('Unexpected API request: ' + url);
            return { ok: true, status: 200, json: async () => data };
        }
    });
    for (const [, script] of html.matchAll(/<script[^>]+src="([^"]+)"[^>]*>/g)) {
        if (!script.startsWith('../')) vm.runInContext(fs.readFileSync(path.join(pluginDirectory, script), 'utf8'), context);
    }
    plugin.init();
    for (let i = 0; i < 20 && body.classList.contains('busy'); i++) await new Promise(resolve => setImmediate(resolve));
    assert.equal(body.classList.contains('busy'), false);
    return { elements, calls, source, plugin, get writes() { return writes; }, get closes() { return closes; },
        set rejectOrder(value) { rejectOrder = value; }, get current() { return current; }, set current(value) { current = value; },
        async open() { await elements[action === 'push' ? 'btnSelectCurrent' : 'btnSelectAll'].click(); await elements[action === 'push' ? 'btnPush' : 'btnLoadSelected'].click(); } };
}
test('opening and cancelling review does not write the document or library', async () => {
    const ui = await harness(); await ui.open();
    assert.equal(ui.elements.reviewChanges.children.length, 1);
    await ui.elements.btnCancelReview.click();
    assert.equal(ui.writes, 0);
    assert.equal(ui.calls.filter(c => c.url.endsWith('/apply')).length, 0);
});
test('review places diff beside result and synchronizes matching lines across deletions', async () => {
    const diffLines = [];
    for (let line = 1; line <= 30; line++) {
        if (line === 11) diffLines.push({ kind: 'removed', text: 'deleted', oldLine: 11, newLine: null });
        diffLines.push({ kind: 'same', text: 'line ' + line, oldLine: line + (line > 10 ? 1 : 0), newLine: line });
    }
    const result = macro(Array.from({ length: 30 }, (_, index) => 'line ' + (index + 1)).join('\n'));
    const ui = await harness('pull', false, false, false, false, { diff: diffLines, result });
    await ui.open();
    const columns = all(ui.elements.reviewChanges).find(element => element.className === 'review-code-columns');
    assert.equal(columns.children.length, 2);
    const diff = columns.children[0].children[1];
    const editor = columns.children[1].children[1];
    assert.equal(diff.className, 'diff');
    assert.equal(editor.className, 'result-code');
    assert.equal(editor.wrap, 'off');
    editor.scrollTop = 10 * 18;
    editor.listeners.scroll[0]();
    assert.equal(diff.scrollTop, 11 * 18);
    diff.listeners.scroll[0](); // Scroll event caused by synchronization.
    diff.scrollTop = 20 * 18;
    diff.listeners.scroll[0]();
    assert.equal(editor.scrollTop, 19 * 18);
    editor.value += '\nextra\nextra\nextra\nextra';
    editor.listeners.input[0]();
    editor.scrollTop = 20 * 18;
    editor.listeners.scroll[0]();
    assert.equal(diff.scrollTop, Math.round(20 * 30 / 33) * 18);
});
test('main plugin shows direct differences against the library', async () => {
    const ui = await harness('pull');
    assert.equal(ui.elements.comparisonStatus.textContent, 'Отличий книги от библиотеки: 1');
    assert.equal(ui.elements.listCurrent.children[0].children[2].textContent, 'отличается');
    assert.equal(ui.elements.listSaved.children[0].children[2].textContent, 'отличается от книги');
    const empty = await harness('pull', false, true);
    assert.equal(empty.elements.comparisonStatus.textContent, 'Отличий книги от библиотеки: 1');
    assert.equal(empty.elements.listSaved.children[0].children[2].textContent, 'нет в книге');
});
test('main plugin displays the book separator without allowing it to be selected', async () => {
    const ui = await harness('push', false, false, false, true);
    const rows = ui.elements.listCurrent.children;
    assert.equal(rows.length, 3);
    assert.equal(rows[1].role, 'separator');
    assert.equal(rows[1].children[0].textContent, 'Разделитель');
    assert.equal(rows[1].children.length, 1);
    assert.equal(rows[2].children[1].textContent, 'Personal');
    await ui.open();
    const preview = ui.calls.find(c => c.url.endsWith('/preview'));
    assert.deepEqual(preview.request.selectedGuids, ['one']);
});
test('changing universal status moves the macro across the separator and removes an unused separator', async () => {
    const ui = await harness('push', false, false, false, true);
    await universalBox(bookRow(ui, 'Personal')).change(true);
    assert.deepEqual(ids(ui), ['one', 'personal', '00000000-separator-0000-000000000000']);
    await universalBox(bookRow(ui, 'One')).change(false);
    assert.deepEqual(ids(ui), ['personal', '00000000-separator-0000-000000000000', 'one']);
    await universalBox(bookRow(ui, 'Personal')).change(false);
    assert.deepEqual(ids(ui), ['personal', 'one']);
    assert.equal(ui.writes, 3);
});
test('the first universal macro creates the book separator', async () => {
    const ui = await harness();
    await universalBox(bookRow(ui, 'One')).change(false);
    assert.deepEqual(ids(ui), ['one']);
    await universalBox(bookRow(ui, 'One')).change(true);
    assert.deepEqual(ids(ui), ['one', '00000000-separator-0000-000000000000']);
});
test('move buttons change book order only within the same group', async () => {
    const ui = await harness('push', false, false, false, true);
    ui.current.macrosArray.splice(1, 0, { guid: 'two', name: 'Two', value: 'code', isUniversal: true });
    ui.current.macrosArray.push({ guid: 'other', name: 'Other', value: 'code', isUniversal: false });
    await ui.elements.btnRefresh.click();
    assert.equal(bookRow(ui, 'One').children.at(-1).children[0].disabled, true);
    assert.equal(bookRow(ui, 'Two').children.at(-1).children[1].disabled, true);
    assert.equal(bookRow(ui, 'Personal').children.at(-1).children[0].disabled, true);
    ui.elements.listCurrent.scrollTop = 48;
    ui.elements.listSaved.scrollTop = 24;
    const stateRequests = ui.calls.filter(c => c.url.endsWith('/state')).length;
    await bookRow(ui, 'Personal').children.at(-1).children[1].click();
    assert.deepEqual(ids(ui), ['one', 'two', '00000000-separator-0000-000000000000', 'other', 'personal']);
    assert.equal(ui.elements.listCurrent.scrollTop, 48);
    assert.equal(ui.elements.listSaved.scrollTop, 24);
    assert.equal(ui.calls.filter(c => c.url.endsWith('/state')).length, stateRequests);
    await bookRow(ui, 'Two').children.at(-1).children[0].click();
    assert.deepEqual(ids(ui), ['two', 'one', '00000000-separator-0000-000000000000', 'other', 'personal']);
    assert.equal(ui.writes, 2);
});
test('saved macro arrows keep a draft until Save order is clicked', async () => {
    const ui = await harness();
    ui.source.macrosArray.push({ guid: 'two', name: 'Two', value: 'code', isUniversal: true });
    await ui.elements.btnRefresh.click();
    assert.equal(savedRow(ui, 'One').children.at(-1).children[0].disabled, true);
    assert.equal(savedRow(ui, 'Two').children.at(-1).children[1].disabled, true);
    ui.elements.listSaved.scrollTop = 32;
    await savedRow(ui, 'Two').children.at(-1).children[0].click();
    assert.deepEqual(ui.source.macrosArray.map(m => m.guid), ['one', 'two']);
    assert.deepEqual(ui.elements.listSaved.children.map(row => row.children[1].textContent), ['Two', 'One']);
    assert.equal(ui.elements.listSaved.scrollTop, 32);
    assert.equal(ui.calls.filter(c => c.url.endsWith('/order')).length, 0);
    assert.equal(ui.elements.btnSaveOrder.disabled, false);
    await ui.elements.btnSaveOrder.click();
    assert.deepEqual(ui.source.macrosArray.map(m => m.guid), ['two', 'one']);
    assert.equal(ui.source._r7Library.revisionId, 'revision');
    assert.equal(ui.elements.btnSaveOrder.disabled, true);
    assert.equal(ui.calls.filter(c => c.url.endsWith('/order')).length, 1);
    assert.equal(ui.writes, 0);
});
test('closing saves a pending library order and leaves the window open if saving fails', async () => {
    const ui = await harness();
    ui.source.macrosArray.push({ guid: 'two', name: 'Two', value: 'code', isUniversal: true });
    await ui.elements.btnRefresh.click();
    await savedRow(ui, 'Two').children.at(-1).children[0].click();
    ui.rejectOrder = true;
    await ui.plugin.button(-1);
    assert.equal(ui.closes, 0);
    assert.equal(ui.elements.btnSaveOrder.disabled, false);
    assert.match(ui.elements.log.textContent, /Library order changed/);
    ui.rejectOrder = false;
    await ui.plugin.button(-1);
    assert.equal(ui.closes, 1);
    assert.deepEqual(ui.source.macrosArray.map(m => m.guid), ['two', 'one']);
});
test('publishing from review calls v2 apply and never writes the document', async () => {
    const ui = await harness(); await ui.open(); await ui.elements.btnApply.click();
    assert.equal(ui.calls.filter(c => c.url.endsWith('/apply')).length, 1);
    assert.equal(ui.writes, 0);
    assert.equal(ui.current.macrosArray[0].value, 'document');
});
test('conflict blocks apply until a result is explicitly chosen', async () => {
    const ui = await harness('push', true); await ui.open();
    assert.equal(ui.elements.btnApply.disabled, true);
    const choose = all(ui.elements.reviewChanges).find(e => e.tag === 'button' && e.textContent === 'Взять библиотеку');
    await choose.click(); assert.equal(ui.elements.btnApply.disabled, false);
    await ui.elements.btnApply.click();
    const request = ui.calls.find(c => c.url.endsWith('/apply')).request;
    assert.equal(request.resolutions.one.macro.value, 'library');
});
test('changed document invalidates review before preparing application', async () => {
    const ui = await harness('pull'); await ui.open(); ui.current = documentOf('edited while reviewing');
    await ui.elements.btnApply.click();
    assert.equal(ui.calls.filter(c => c.url.endsWith('/apply')).length, 0);
    assert.equal(ui.writes, 0);
    assert.match(ui.elements.log.textContent, /Документ изменился/);
});
test('loading verifies and applies the prepared document once', async () => {
    const ui = await harness('pull'); await ui.open(); await ui.elements.btnApply.click();
    assert.equal(ui.writes, 1);
    assert.equal(ui.current.macrosArray[0].value, 'merged');
    assert.match(ui.elements.log.textContent, /Резервная копия/);
});
test('a new book with no GetMacros value can load a library macro', async () => {
    const ui = await harness('pull', false, true);
    assert.equal(ui.elements.listCurrent.children.length, 0);
    assert.equal(ui.elements.listSaved.children.length, 1);
    await ui.open();
    assert.deepEqual(ui.calls.find(c => c.url.endsWith('/preview')).request.document, { macrosArray: [] });
    await ui.elements.btnApply.click();
    assert.equal(ui.writes, 1);
    assert.equal(ui.current.macrosArray[0].value, 'merged');
});
test('an unavailable server does not leave a new book stuck on loading', async () => {
    const ui = await harness('pull', false, true, true);
    assert.equal(ui.elements.listCurrent.children.length, 0);
    assert.equal(ui.elements.listSaved.children.length, 0);
    assert.match(ui.elements.log.textContent, /Не удалось обратиться к серверу http:\/\/127\.0\.0\.1:8124\/macros\/v2\/state/);
    assert.equal(ui.writes, 0);
});
