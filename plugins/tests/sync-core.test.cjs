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
function companion(initial, settings = { macrosSync_autoSync: 'true', macrosSync_dirPath: 'shared' }, response = { library: { macrosArray: [] } }, mode = 'background', bridge = {}) {
    class Element {
        constructor() { this.textContent = ''; this.dataset = {}; this.disabled = false; this.children = []; this.listeners = {}; }
        replaceChildren(...children) { this.children = children; }
        append(...children) { this.children.push(...children); }
        addEventListener(name, callback) { this.listeners[name] = callback; }
        click() { return this.listeners.click(); }
    }
    const elements = Object.fromEntries(['status', 'changes', 'refresh', 'applyAll'].map(id => [id, new Element()]));
    const body = { classList: { add() {} } };
    const requests = [], windows = [], messages = [];
    let raw = initial, serverResponse = response, writes = 0, closes = 0, bodyReady = false, context;
    const plugin = {
        callCommand(fn, a, b, callback) {
            if (mode === 'window') throw new Error('callCommand is forbidden in a window frame');
            callback(vm.runInContext('(' + fn.toString() + ')()', context));
        },
        executeCommand() { throw new Error('executeCommand is forbidden in a window frame'); },
        executeMethod() { throw new Error('executeMethod is forbidden in a window frame'); },
        sendToPlugin(name, data) {
            assert.equal(mode, 'window');
            messages.push({ name, data: JSON.parse(JSON.stringify(data)) });
            bridge.pending = bridge.background.emit(name, data);
            return true;
        }
    };
    const window = {
        location: { href: 'file:///plugins/Macros%20Sync%20Companion/companion.html' },
        R7SyncCore: core,
        Asc: { plugin, scope: {}, PluginWindow: class {
            constructor() { this.events = {}; this.id = 'test-window'; }
            attachEvent(name, callback) { this.events[name] = callback; }
            show(variation) { windows.push({ variation, handle: this }); }
            command(name, data) { bridge.child.plugin['event_' + name](data); }
            close() { closes++; }
        } }
    };
    context = vm.createContext({
        window, Asc: window.Asc,
        document: { body, getElementById: id => bodyReady && mode === 'window' ? elements[id] : null, createElement: () => new Element() },
        localStorage: { getItem: key => settings[key], setItem: (key, value) => { settings[key] = value; } },
        console: { warn() {} }, setTimeout, clearTimeout,
        Api: { pluginMethod_GetMacros: () => raw, pluginMethod_SetMacros: json => { raw = json; writes++; } },
        fetch: async (url, options) => {
            if (mode === 'window') throw new Error('fetch is forbidden in a window frame');
            requests.push({ url, body: JSON.parse(options.body) });
            return { ok: true, json: async () => typeof serverResponse === 'function' ? serverResponse(url, JSON.parse(options.body)) : serverResponse };
        }
    });
    vm.runInContext(fs.readFileSync(path.join(__dirname, '../Macros Sync Companion/companion.js'), 'utf8'), context);
    bodyReady = true;
    const instance = {
        plugin, elements, requests, windows, messages, settings,
        emit(name, data) { return windows[0].handle.events[name](data); },
        set response(value) { serverResponse = value; },
        get writes() { return writes; }, get closes() { return closes; }, get raw() { return raw; }, set raw(value) { raw = value; }
    };
    bridge[mode === 'window' ? 'child' : 'background'] = instance;
    const initialCheck = plugin.init();
    instance.settle = () => initialCheck;
    return instance;
}

test('background Companion compares book code directly with library and opens one window', async () => {
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
    assert.equal(ui.windows.length, 1);
    assert.equal(ui.windows[0].variation.isInsideMode, false);
    assert.match(ui.windows[0].variation.url, /companion-window\.html$/);
    assert.equal(ui.writes, 0);
});

test('background Companion opens no window for disabled, equal, or missing-document-only changes', async () => {
    const disabled = companion(undefined, { macrosSync_autoSync: 'false', macrosSync_dirPath: 'shared' });
    await disabled.settle();
    assert.equal(disabled.windows.length, 0);
    assert.equal(disabled.requests.length, 0);

    const equal = companion(JSON.stringify({ macrosArray: [{ value: 'same', guid: 'one', name: 'One', isUniversal: true, _r7Sync: {} }] }),
        undefined, { library: { macrosArray: [{ name: 'One', guid: 'one', value: 'same' }] } });
    await equal.settle();
    assert.equal(equal.windows.length, 0);

    const missing = companion(undefined, undefined, { library: { macrosArray: [{ guid: 'saved', name: 'Saved' }] } });
    await missing.settle();
    assert.equal(missing.windows.length, 0);
});

test('window receives the background result without accessing the editor or server', async () => {
    const bridge = {};
    const background = companion(JSON.stringify({ macrosArray: [
        { guid: 'one', name: 'One', value: 'book', isUniversal: true },
        { guid: 'local', name: 'Local', value: 'local', isUniversal: true }
    ] }), undefined, { library: { macrosArray: [
        { guid: 'one', name: 'One', value: 'library' },
        { guid: 'saved', name: 'Saved', value: 'code' }
    ] } }, 'background', bridge);
    await background.settle();
    const child = companion(undefined, undefined, undefined, 'window', bridge);
    await child.settle();
    assert.deepEqual(child.messages, [{ name: 'companionReady', data: {} }]);
    assert.equal(child.requests.length, 0);
    assert.equal(child.elements.status.textContent, 'Найдены отличия: 3');
    assert.equal(child.elements.changes.children[0].children[1].textContent, 'Отличается от библиотеки');
    assert.equal(child.elements.changes.children[1].children[1].textContent, 'Нет в книге');
    assert.equal(child.elements.changes.children[2].children[1].textContent, 'Нет в библиотеке');
    assert.equal(child.elements.applyAll.disabled, false);
    assert.equal(child.writes, 0);
});

test('refresh uses the background checker and closes its window when only missing book macros remain', async () => {
    const bridge = {};
    const background = companion(JSON.stringify({ macrosArray: [{ guid: 'one', name: 'One', value: 'book', isUniversal: true }] }),
        undefined, { library: { macrosArray: [{ guid: 'one', name: 'One', value: 'library' }] } }, 'background', bridge);
    await background.settle();
    const child = companion(undefined, undefined, undefined, 'window', bridge);
    await child.settle();
    background.raw = JSON.stringify({ macrosArray: [] });
    await child.elements.refresh.click();
    await bridge.pending;
    assert.equal(background.closes, 1);
    assert.equal(child.requests.length, 0);
    assert.equal(background.requests.length, 2);
    assert.deepEqual(child.messages.map(message => message.name), ['companionReady', 'companionRefresh']);
});

test('refresh updates the open window and keeps server errors visible', async () => {
    const bridge = {};
    const background = companion(JSON.stringify({ macrosArray: [{ guid: 'one', name: 'One', value: 'book', isUniversal: true }] }),
        undefined, { library: { macrosArray: [{ guid: 'one', name: 'One', value: 'library' }] } }, 'background', bridge);
    await background.settle();
    const child = companion(undefined, undefined, undefined, 'window', bridge);
    await child.settle();
    background.response = { broken: true };
    await child.elements.refresh.click();
    await bridge.pending;
    assert.match(child.elements.status.textContent, /некорректную библиотеку/);
    assert.equal(child.elements.status.dataset.state, 'error');
    assert.equal(child.elements.refresh.disabled, false);
    assert.equal(background.windows.length, 1);
    assert.equal(background.closes, 0);
});

test('background Companion opens a window for a startup error', async () => {
    const bridge = {};
    const background = companion(undefined, undefined, { broken: true }, 'background', bridge);
    await background.settle();
    assert.equal(background.windows.length, 1);
    const child = companion(undefined, undefined, undefined, 'window', bridge);
    await child.settle();
    assert.match(child.elements.status.textContent, /некорректную библиотеку/);
    assert.equal(child.elements.status.dataset.state, 'error');
    assert.equal(child.requests.length, 0);
});

test('close button closes only the active Companion window', async () => {
    const background = companion(JSON.stringify({ macrosArray: [{ guid: 'local', isUniversal: true }] }));
    await background.settle();
    assert.equal(background.windows.length, 1);

    background.plugin.button(-1, 'another-window');
    background.plugin.button(0, 'test-window');
    assert.equal(background.closes, 0);

    background.plugin.button(-1, 'test-window');
    assert.equal(background.closes, 1);
    background.plugin.button(-1, 'test-window');
    assert.equal(background.closes, 1);
});

test('Apply all loads changed and missing library macros while preserving book-only and excluded macros', async () => {
    const bridge = {};
    const original = { macrosArray: [
        { guid: 'one', name: 'One', value: 'book', isUniversal: true },
        { guid: 'local', name: 'Local', value: 'local', isUniversal: true },
        { guid: 'excluded', name: 'Excluded', value: 'mine', isUniversal: true, isExcludedFromAutoSync: true }
    ] };
    const saved = { guid: 'one', name: 'One', value: 'library' };
    const added = { guid: 'saved', name: 'Saved', value: 'new' };
    const library = { macrosArray: [saved, added, { guid: 'excluded', name: 'Excluded', value: 'library' }] };
    const next = { macrosArray: [
        { guid: 'one', name: 'One', value: 'library', isUniversal: true },
        original.macrosArray[1], original.macrosArray[2],
        { guid: 'saved', name: 'Saved', value: 'new', isUniversal: true }
    ] };
    const background = companion(JSON.stringify(original), undefined, (url, request) => {
        if (url.endsWith('/state')) return { library };
        if (url.endsWith('/preview')) {
            assert.deepEqual(request.selectedGuids, ['one', 'saved']);
            return { token: 'token', operationId: 'operation', changes: [
                { guid: 'one', library: saved, result: saved, conflicts: [] },
                { guid: 'saved', library: added, result: added, conflicts: [] }
            ] };
        }
        assert.match(url, /\/apply$/);
        assert.deepEqual(request.selectedGuids, ['one', 'saved']);
        return { document: next, expectedDocument: original, backupPath: 'backup.json', changed: true };
    }, 'background', bridge);
    await background.settle();
    const child = companion(undefined, undefined, undefined, 'window', bridge);
    await child.settle();
    await child.elements.applyAll.click();
    await bridge.pending;
    assert.equal(background.writes, 1);
    assert.deepEqual(JSON.parse(background.raw), next);
    assert.equal(child.elements.changes.children.length, 1);
    assert.equal(child.elements.changes.children[0].children[1].textContent, 'Нет в библиотеке');
    assert.equal(child.elements.applyAll.disabled, true);
    assert.equal(child.elements.refresh.disabled, false);
});

test('always update applies library code and leaves excluded and missing book macros untouched', async () => {
    const settings = { macrosSync_autoSync: 'true', macrosSync_alwaysUpdate: 'true',
        macrosSync_dirPath: 'shared', macrosSync_backupPath: 'backups' };
    const original = { macrosArray: [
        { guid: 'one', name: 'One', value: 'book', isUniversal: true },
        { guid: 'excluded', name: 'Excluded', value: 'mine', isUniversal: true, isExcludedFromAutoSync: true }
    ] };
    const saved = { guid: 'one', name: 'One', value: 'library' };
    const library = { macrosArray: [saved, { guid: 'excluded', name: 'Excluded', value: 'library' },
        { guid: 'new', name: 'New', value: 'new' }] };
    const next = { macrosArray: [
        { guid: 'one', name: 'One', value: 'library', isUniversal: true }, original.macrosArray[1]
    ] };
    const ui = companion(JSON.stringify(original), settings, (url, request) => {
        if (url.endsWith('/state')) return { library };
        if (url.endsWith('/preview')) {
            assert.deepEqual(request.selectedGuids, ['one']);
            assert.equal(request.action, 'pull');
            return { token: 'token', operationId: 'operation', changes: [
                { guid: 'one', library: saved, result: saved, conflicts: [] }
            ] };
        }
        assert.match(url, /\/apply$/);
        assert.deepEqual(request.selectedGuids, ['one']);
        assert.equal(request.backupDirectory, 'backups');
        return { document: next, expectedDocument: original, backupPath: 'backups/snapshot.json', changed: true };
    });
    await ui.settle();
    assert.equal(ui.writes, 1);
    assert.deepEqual(JSON.parse(ui.raw), next);
    assert.equal(settings.macrosSync_lastBackup, 'backups/snapshot.json');
    assert.equal(ui.requests.filter(request => request.url.endsWith('/preview')).length, 1);
    assert.equal(ui.windows.length, 0);
});

test('always update does not overwrite a book changed after preview', async () => {
    const original = { macrosArray: [{ guid: 'one', name: 'One', value: 'book' }] };
    const edited = { macrosArray: [{ guid: 'one', name: 'One', value: 'new local edit' }] };
    let ui;
    ui = companion(JSON.stringify(original), { macrosSync_autoSync: 'true', macrosSync_alwaysUpdate: 'true', macrosSync_dirPath: 'shared' },
        (url) => {
            if (url.endsWith('/state')) return { library: { macrosArray: [{ guid: 'one', name: 'One', value: 'library' }] } };
            if (url.endsWith('/preview')) {
                ui.raw = JSON.stringify(edited);
                return { token: 'token', operationId: 'operation', changes: [
                    { guid: 'one', library: { guid: 'one', name: 'One', value: 'library' },
                        result: { guid: 'one', name: 'One', value: 'library' }, conflicts: [] }
                ] };
            }
            return { document: { macrosArray: [{ guid: 'one', name: 'One', value: 'library' }] },
                expectedDocument: original, backupPath: 'backup.json', changed: true };
        });
    await ui.settle();
    assert.equal(ui.writes, 0);
    assert.equal(ui.windows.length, 1);
    assert.match(ui.windows[0].variation.url, /companion-window\.html$/);
});

test('always update asks for library settings before reading or writing macros', async () => {
    const ui = companion(undefined, { macrosSync_alwaysUpdate: 'true' });
    await ui.settle();
    assert.equal(ui.requests.length, 0);
    assert.equal(ui.writes, 0);
    assert.equal(ui.windows.length, 1);
});
