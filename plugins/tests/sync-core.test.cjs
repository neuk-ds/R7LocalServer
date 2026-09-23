const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const core = require('../Macros Sync/sync-core.js');

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
test('Companion only reads and excludes opted-out macros', async () => {
    const source = fs.readFileSync(path.join(__dirname, '../Macros Sync Companion/companion.js'), 'utf8');
    let requests = [], notices = [];
    const settings = { macrosSync_autoSync: 'true', macrosSync_dirPath: 'shared' };
    const plugin = { callCommand(fn, a, b, callback) {
        callback(JSON.stringify({ macrosArray: [
            { guid: 'one', isUniversal: true },
            { guid: 'excluded', isUniversal: true, isExcludedFromAutoSync: true }
        ] }));
    } };
    const context = vm.createContext({
        window: { Asc: { plugin }, alert: text => notices.push(text) },
        localStorage: { getItem: key => settings[key] }, console,
        fetch: async (url, options) => { requests.push({ url, body: JSON.parse(options.body) }); return { ok: true, json: async () => ({ changes: [{ kind: 'modified' }] }) }; }
    });
    vm.runInContext(source, context); plugin.init();
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(requests.length, 1);
    assert.match(requests[0].url, /\/macros\/v2\/preview$/);
    assert.deepEqual(requests[0].body.selectedGuids, ['one']);
    assert.equal(notices.length, 1);
});
test('Companion accepts an empty macros response and skips invalid GUIDs', async () => {
    const source = fs.readFileSync(path.join(__dirname, '../Macros Sync Companion/companion.js'), 'utf8');
    let requests = [];
    const settings = { macrosSync_autoSync: 'true', macrosSync_dirPath: 'shared' };
    const plugin = { callCommand(fn, a, b, callback) {
        callback('');
    } };
    const context = vm.createContext({
        window: { Asc: { plugin }, alert: () => {} },
        localStorage: { getItem: key => settings[key] }, console,
        fetch: async (url, options) => { requests.push({ url, body: JSON.parse(options.body) }); return { ok: true, json: async () => ({ changes: [] }) }; }
    });
    vm.runInContext(source, context); plugin.init();
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(requests.length, 0);

    plugin.callCommand = (fn, a, b, callback) => callback(JSON.stringify({ macrosArray: [
        { isUniversal: true, guid: '' }, { isUniversal: true, guid: null }, { isUniversal: true, guid: 'valid' }
    ] }));
    plugin.init();
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(requests.length, 1);
    assert.deepEqual(requests[0].body.selectedGuids, ['valid']);
});
