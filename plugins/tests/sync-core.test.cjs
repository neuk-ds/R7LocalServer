const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const core = require('../Macros Sync/resources/libs/sync-core.js');

function editor(initial, transform = x => x, encode = JSON.stringify) {
    let data = structuredClone(initial), writes = 0;
    const scope = {};
    const context = vm.createContext({
        Asc: { scope },
        Api: {
            pluginMethod_GetMacros: () => encode(data),
            pluginMethod_SetMacros: json => { writes++; data = transform(JSON.parse(json)); }
        }
    });
    return {
        scope, get data() { return data; }, get writes() { return writes; },
        plugin: { callCommand(command, a, b, callback) { callback(vm.runInContext('(' + command.toString() + ')()', context)); } }
    };
}
const doc = code => ({ macrosArray: [{ guid: 'one', value: code, _r7Sync: { schemaVersion: 1, base: { value: 'base' } } }], current: 0 });

test('comparison uses library content and reports book-only edits', () => {
    const book = { macrosArray: [
        { guid: 'one', name: 'One', value: 'book', isUniversal: true, _r7Sync: { base: { value: 'library' } } },
        { guid: 'local', name: 'Local', value: 'local', isUniversal: true }
    ] };
    const library = { macrosArray: [
        { guid: 'one', name: 'One', value: 'library' },
        { guid: 'saved', name: 'Saved', value: 'saved' }
    ] };
    assert.deepEqual(core.compare(book, library), [
        { guid: 'one', name: 'One', kind: 'modified' },
        { guid: 'saved', name: 'Saved', kind: 'missingDocument' },
        { guid: 'local', name: 'Local', kind: 'missingLibrary' }
    ]);
    assert.deepEqual(core.compare(
        { macrosArray: [{ guid: 'one', value: 'same', autostart: true, isUniversal: true }] },
        { macrosArray: [{ guid: 'one', value: 'same', autostart: false }] }
    ), [{ guid: 'one', name: 'one', kind: 'modified' }]);
});

test('read is read-only and preserves complete document', async () => {
    const e = editor(doc('original'));
    assert.deepEqual(await core.read(e.plugin), doc('original'));
    assert.equal(e.writes, 0);
});
test('a never-used macros collection is read as empty without writing it', async () => {
    for (const raw of ['', '  \r\n']) {
        const e = editor(null, x => x, () => raw);
        assert.deepEqual(await core.read(e.plugin), { macrosArray: [] });
        assert.equal(e.writes, 0);
    }
});
test('macros can be applied to a book whose GetMacros initially returns an empty string', async () => {
    const e = editor(null, x => x, value => value === null ? '' : JSON.stringify(value));
    const expected = await core.read(e.plugin);
    await core.apply(e.plugin, e.scope, expected, doc('loaded'));
    assert.deepEqual(e.data, doc('loaded'));
    assert.equal(e.writes, 1);
});
test('macros can be applied when GetMacros returns no value for a new book', async () => {
    for (const empty of [undefined, null]) {
        const e = editor(empty, x => x, value => value == null ? value : JSON.stringify(value));
        const expected = await core.read(e.plugin);
        assert.deepEqual(expected, { macrosArray: [] });
        await core.apply(e.plugin, e.scope, expected, doc('loaded'));
        assert.deepEqual(e.data, doc('loaded'));
        assert.equal(e.writes, 1);
    }
});
test('invalid nonempty JSON is not mistaken for an empty book', async () => {
    const e = editor(null, x => x, () => '{"macrosArray":');
    await assert.rejects(core.read(e.plugin), /SyntaxError/);
    await assert.rejects(core.apply(e.plugin, e.scope, { macrosArray: [] }, doc('loaded')), /SyntaxError/);
    assert.equal(e.writes, 0);
});
test('a newly created macro invalidates the previously empty snapshot', async () => {
    const e = editor(doc('created in editor'));
    await assert.rejects(core.apply(e.plugin, e.scope, { macrosArray: [] }, doc('loaded')), /Документ изменился/);
    assert.equal(e.writes, 0);
});
test('compare and set rejects concurrent editing', async () => {
    const e = editor(doc('changed'));
    await assert.rejects(core.apply(e.plugin, e.scope, doc('original'), doc('merged')), /Документ изменился/);
    assert.equal(e.writes, 0);
});
test('successful application verifies content and retains unrelated document fields', async () => {
    const e = editor(doc('original'));
    const result = await core.apply(e.plugin, e.scope, doc('original'), doc('merged'));
    assert.equal(result.metadataLost, false);
    assert.deepEqual(e.data, doc('merged'));
    assert.equal(e.writes, 1);
});
test('discarded sync metadata is reported instead of pretending the base persisted', async () => {
    const e = editor(doc('original'), data => { data.macrosArray.forEach(m => delete m._r7Sync); return data; });
    assert.equal((await core.apply(e.plugin, e.scope, doc('original'), doc('merged'))).metadataLost, true);
});
test('write corruption is detected', async () => {
    const e = editor(doc('original'), () => doc('corrupt'));
    await assert.rejects(core.apply(e.plugin, e.scope, doc('original'), doc('merged')), /не совпал/);
});
test('corrupted base metadata is rejected rather than accepted as missing', async () => {
    const e = editor(doc('original'), data => { data.macrosArray[0]._r7Sync.base.value = 'corrupt'; return data; });
    await assert.rejects(core.apply(e.plugin, e.scope, doc('original'), doc('merged')), /не совпал/);
});
test('invalid read fails instead of treating document as empty', async () => {
    const e = editor({ broken: true });
    await assert.rejects(core.read(e.plugin), /macrosArray/);
});
function companion(initial, settings = { macrosSync_autoSync: 'true', macrosSync_dirPath: 'shared' }, response = { library: { macrosArray: [] } }) {
    class Element {
        constructor() { this.textContent = ''; this.dataset = {}; this.disabled = false; this.children = []; this.listeners = {}; }
        replaceChildren(...children) { this.children = children; }
        append(...children) { this.children.push(...children); }
        addEventListener(name, callback) { this.listeners[name] = callback; }
        click() { return this.listeners.click(); }
    }
    const elements = Object.fromEntries(['status', 'changes', 'refresh'].map(id => [id, new Element()]));
    const body = { classList: { add() {} } };
    let raw = initial, writes = 0, closes = 0;
    const requests = [];
    let context;
    const plugin = {
        callCommand(fn, a, b, callback) { callback(vm.runInContext('(' + fn.toString() + ')()', context)); },
        executeCommand(command) { assert.equal(command, 'close'); closes++; }
    };
    const window = { Asc: { plugin } };
    context = vm.createContext({
        window, Asc: window.Asc,
        document: { body, getElementById: id => elements[id], createElement: () => new Element() },
        localStorage: { getItem: key => settings[key] }, console: { warn() {} }, setTimeout, clearTimeout,
        Api: { pluginMethod_GetMacros: () => raw, pluginMethod_SetMacros: () => { writes++; } },
        fetch: async (url, options) => { requests.push({ url, body: JSON.parse(options.body) }); return { ok: true, json: async () => response }; }
    });
    vm.runInContext(fs.readFileSync(path.join(__dirname, '../Macros Sync Companion/companion.js'), 'utf8'), context);
    plugin.init();
    return { elements, requests, settings, set raw(value) { raw = value; }, get writes() { return writes; }, get closes() { return closes; },
        async settle() { for (let i = 0; i < 10 && elements.refresh.disabled; i++) await new Promise(resolve => setImmediate(resolve)); } };
}
test('Companion compares book code directly with library, even when only the book changed', async () => {
    const ui = companion(JSON.stringify({ macrosArray: [
        { guid: 'one', name: 'One', value: 'book', isUniversal: true, _r7Sync: { base: { value: 'library' } } },
        { guid: 'excluded', name: 'Excluded', value: 'book', isUniversal: true, isExcludedFromAutoSync: true }
    ] }), undefined, { library: { macrosArray: [
        { guid: 'one', name: 'One', value: 'library', isUniversal: true },
        { guid: 'excluded', name: 'Excluded', value: 'library', isUniversal: true }
    ] } });
    await ui.settle();
    assert.equal(ui.requests.length, 1);
    assert.match(ui.requests[0].url, /\/macros\/v2\/state$/);
    assert.deepEqual(ui.requests[0].body, { directoryPath: 'shared', fileName: 'universal_macros.json' });
    assert.equal(ui.elements.status.textContent, 'Найдены отличия: 1');
    assert.equal(ui.elements.changes.children[0].children[0].textContent, 'One');
    assert.equal(ui.elements.changes.children[0].children[1].textContent, 'Отличается от библиотеки');
    assert.equal(ui.closes, 0);
    assert.equal(ui.writes, 0);
});
test('Companion ignores local flags and property order when contents match', async () => {
    const ui = companion(JSON.stringify({ macrosArray: [{ value: 'same', guid: 'one', name: 'One', isUniversal: true, _r7Sync: {} }] }),
        undefined, { library: { macrosArray: [{ name: 'One', guid: 'one', value: 'same' }] } });
    await ui.settle();
    assert.equal(ui.closes, 1);
    assert.equal(ui.elements.changes.children.length, 0);
});
test('Companion shows library macros missing from a new book and local universal macros', async () => {
    const ui = companion(undefined, undefined, { library: { macrosArray: [{ guid: 'saved', name: 'Saved', value: 'code' }] } });
    await ui.settle();
    assert.equal(ui.elements.changes.children[0].children[1].textContent, 'Нет в книге');
    ui.raw = JSON.stringify({ macrosArray: [{ guid: 'local', name: 'Local', value: 'code', isUniversal: true }] });
    await ui.elements.refresh.click();
    assert.equal(ui.elements.status.textContent, 'Найдены отличия: 2');
    assert.equal(ui.elements.changes.children[1].children[1].textContent, 'Нет в библиотеке');
});
test('Companion closes without reading the book when checking is disabled', async () => {
    const ui = companion(undefined, { macrosSync_autoSync: 'false', macrosSync_dirPath: 'shared' });
    await ui.settle();
    assert.equal(ui.closes, 1);
    assert.equal(ui.requests.length, 0);
});
test('Companion panel shows server errors', async () => {
    const ui = companion(undefined, undefined, { broken: true });
    await ui.settle();
    assert.match(ui.elements.status.textContent, /некорректную библиотеку/);
    assert.equal(ui.elements.status.dataset.state, 'error');
    assert.equal(ui.closes, 0);
});
