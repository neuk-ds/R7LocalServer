const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');

// Minimal DOM double for testing event/data flow; visual checks still run in R7.
class Element {
    constructor(tag = 'div') {
        this.tag = tag; this.children = []; this.listeners = {}; this.value = ''; this.textContent = ''; this.disabled = false;
        const classes = new Set();
        this.classList = { add: c => classes.add(c), remove: c => classes.delete(c), contains: c => classes.has(c) };
    }
    append(...children) { this.children.push(...children); }
    replaceChildren(...children) { this.children = children; }
    addEventListener(name, callback) { (this.listeners[name] ||= []).push(callback); }
    async click() { if (!this.disabled) await Promise.all((this.listeners.click || []).map(fn => fn())); }
}
function all(root) { return [root, ...root.children.flatMap(all)]; }
const macro = value => ({ guid: 'one', name: 'One', value, isUniversal: true });
const documentOf = value => ({ macrosArray: [macro(value)], current: 0 });
async function harness(action = 'push', conflict = false) {
    const pluginDirectory = path.join(__dirname, '../Macros Sync');
    const html = fs.readFileSync(path.join(pluginDirectory, 'index.html'), 'utf8');
    const elements = Object.fromEntries([...html.matchAll(/id="([^"]+)"/g)].map(match => [match[1], new Element()]));
    const body = new Element('body');
    let current = documentOf('document'), writes = 0;
    const calls = [];
    const settings = new Map([['macrosSync_dirPath', 'shared']]);
    const document = { body, getElementById: id => { if (!elements[id]) throw Error('Missing UI element: ' + id); return elements[id]; },
        createElement: tag => new Element(tag), createTextNode: text => { const node = new Element('text'); node.textContent = text; return node; } };
    const expected = structuredClone(current);
    const source = { macrosArray: [macro('library')], _r7Library: { revisionId: 'revision', libraryId: 'library' } };
    const result = macro('merged');
    let context;
    const plugin = { callCommand(fn, a, b, callback) { callback(vm.runInContext('(' + fn.toString() + ')()', context)); } };
    const window = { Asc: { plugin, scope: {} } };
    context = vm.createContext({
        window, Asc: window.Asc, document, console, setTimeout, clearTimeout,
        localStorage: { getItem: key => settings.get(key), setItem: (key, value) => settings.set(key, value) },
        Api: { pluginMethod_GetMacros: () => JSON.stringify(current), pluginMethod_SetMacros: json => { current = JSON.parse(json); writes++; } },
        fetch: async (url, options) => {
            const request = JSON.parse(options.body); calls.push({ url, request });
            let data;
            if (url.endsWith('/state')) data = { library: source, managed: true, externalChanges: false };
            else if (url.endsWith('/preview')) data = { token: 'token', operationId: 'operation', changes: [{
                guid: 'one', name: 'One', kind: conflict ? 'conflict' : 'modified', base: null,
                document: macro('document'), library: macro('library'), result,
                conflicts: conflict ? ['base'] : [], chunks: [], diff: [{ kind: 'added', text: 'merged', oldLine: null, newLine: 1 }]
            }] };
            else if (url.endsWith('/apply')) data = action === 'push' ? { revisionId: 'saved', changed: true } : {
                document: documentOf('merged'), expectedDocument: expected, backupPath: 'backups/one.json', changed: true
            };
            else throw Error('Unexpected API request: ' + url);
            return { ok: true, status: 200, json: async () => data };
        }
    });
    vm.runInContext(fs.readFileSync(path.join(pluginDirectory, 'sync-core.js'), 'utf8'), context);
    vm.runInContext(fs.readFileSync(path.join(pluginDirectory, 'plugin.js'), 'utf8'), context);
    plugin.init();
    for (let i = 0; i < 20 && body.classList.contains('busy'); i++) await new Promise(resolve => setImmediate(resolve));
    assert.equal(body.classList.contains('busy'), false);
    return { elements, calls, get writes() { return writes; }, get current() { return current; }, set current(value) { current = value; },
        async open() { await elements[action === 'push' ? 'btnSelectCurrent' : 'btnSelectAll'].click(); await elements[action === 'push' ? 'btnPush' : 'btnLoadSelected'].click(); } };
}
test('opening and cancelling review does not write the document or library', async () => {
    const ui = await harness(); await ui.open();
    assert.equal(ui.elements.reviewChanges.children.length, 1);
    await ui.elements.btnCancelReview.click();
    assert.equal(ui.writes, 0);
    assert.equal(ui.calls.filter(c => c.url.endsWith('/apply')).length, 0);
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
