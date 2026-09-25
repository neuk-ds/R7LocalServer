(function (window) {
    'use strict';
    const $ = id => document.getElementById(id);
    const kinds = { modified: 'Отличается от библиотеки', missingDocument: 'Нет в книге', missingLibrary: 'Нет в библиотеке' };
    const localFields = new Set(['_r7Sync', 'isUniversal', 'isExcludedFromAutoSync', 'isSeparator']);
    const separatorGuid = '00000000-separator-0000-000000000000';
    const core = window.R7SyncCore;
    let checking = false;
    let isWindow = false;
    let activeWindow = null;
    let latestResult = null;

    function canonical(value) {
        if (Array.isArray(value)) return '[' + value.map(canonical).join(',') + ']';
        if (value && typeof value === 'object') return '{' + Object.keys(value).sort().map(key => JSON.stringify(key) + ':' + canonical(value[key])).join(',') + '}';
        return JSON.stringify(value);
    }

    function comparable(macro) {
        return Object.fromEntries(Object.entries(macro).filter(([key]) => !localFields.has(key)));
    }

    function isMacro(macro) {
        return macro && !macro.isSeparator && typeof macro.guid === 'string' && macro.guid.trim() && macro.guid !== separatorGuid;
    }

    function readDocument() {
        return new Promise((resolve, reject) => {
            const timer = setTimeout(() => reject(new Error('Редактор не ответил на запрос макросов.')), 15000);
            try {
                window.Asc.plugin.callCommand(function () { return Api.pluginMethod_GetMacros(); }, false, false, raw => {
                    clearTimeout(timer);
                    resolve(raw);
                });
            } catch (error) { clearTimeout(timer); reject(error); }
        });
    }

    // The comparison is read-only; optional updates run after it.
    async function getChanges() {
        if (localStorage.getItem('macrosSync_autoSync') !== 'true' && localStorage.getItem('macrosSync_alwaysUpdate') !== 'true') return null;
        const directoryPath = localStorage.getItem('macrosSync_dirPath');
        const fileName = localStorage.getItem('macrosSync_fileName') || 'universal_macros.json';
        const server = localStorage.getItem('macrosSync_serverUrl') || 'http://127.0.0.1:8124';
        if (!directoryPath) {
            return { message: 'Укажите папку библиотеки в настройках «Синхронизация макросов».', state: 'idle' };
        }
        const raw = await readDocument();
        const macros = raw == null || (typeof raw === 'string' && raw.trim() === '')
            ? { macrosArray: [] } : typeof raw === 'string' ? JSON.parse(raw) : raw;
        if (!macros || !Array.isArray(macros.macrosArray)) throw new Error('Не удалось прочитать макросы документа.');
        const url = server.replace(/\/$/, '') + '/macros/v2/state';
        let response;
        try {
            response = await fetch(url, {
                method: 'POST', headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ directoryPath, fileName })
            });
        } catch (_) { throw new Error('Нет соединения с ' + url + '. Проверьте сервер и адрес.'); }
        if (!response.ok) {
            if (response.status === 404) throw new Error('Обновите R7LocalServer для Macros Sync v2.');
            const error = await response.json();
            throw new Error(error.message || 'Ошибка сервера: ' + response.status);
        }
        const state = await response.json();
        if (!state || !state.library || !Array.isArray(state.library.macrosArray)) throw new Error('Сервер вернул некорректную библиотеку.');
        const book = new Map(macros.macrosArray.filter(isMacro).map(m => [m.guid, m]));
        const library = new Map(state.library.macrosArray.filter(isMacro).map(m => [m.guid, m]));
        const changed = [];
        for (const [guid, saved] of library) {
            const current = book.get(guid);
            if (current && current.isExcludedFromAutoSync) continue;
            if (!current) changed.push({ guid, name: saved.name, kind: 'missingDocument' });
            else if (canonical(comparable(current)) !== canonical(comparable(saved)))
                changed.push({ guid, name: saved.name || current.name, kind: 'modified' });
        }
        for (const [guid, current] of book) {
            if (current.isUniversal && !current.isExcludedFromAutoSync && !library.has(guid))
                changed.push({ guid, name: current.name, kind: 'missingLibrary' });
        }
        return { changed, document: macros, library: state.library };
    }

    async function post(server, path, body) {
        const url = server.replace(/\/$/, '') + path;
        let response;
        try {
            response = await fetch(url, {
                method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body)
            });
        } catch (_) { throw new Error('Нет соединения с ' + url + '. Проверьте сервер и адрес.'); }
        if (response.status === 404) throw new Error('Обновите R7LocalServer для Macros Sync v2.');
        const result = await response.json();
        if (!response.ok) throw new Error(result.message || 'Ошибка сервера: ' + response.status);
        return result;
    }

    async function updateFromLibrary(snapshot, includeMissingDocument = false) {
        const book = new Map(snapshot.document.macrosArray.filter(isMacro).map(m => [m.guid, m]));
        const saved = new Map(snapshot.library.macrosArray.filter(isMacro).map(m => [m.guid, m]));
        const selectedGuids = snapshot.changed.filter(change =>
            (change.kind === 'modified' || (includeMissingDocument && change.kind === 'missingDocument')) &&
            (!book.has(change.guid) || !book.get(change.guid).isExcludedFromAutoSync) && saved.has(change.guid))
            .map(change => change.guid);
        if (!selectedGuids.length) return false;
        const current = await core.read(window.Asc.plugin);
        if (core.canonical(current) !== core.canonical(snapshot.document))
            throw new Error('Документ изменился во время проверки. Повторите проверку.');
        const directoryPath = localStorage.getItem('macrosSync_dirPath');
        const fileName = localStorage.getItem('macrosSync_fileName') || 'universal_macros.json';
        const server = localStorage.getItem('macrosSync_serverUrl') || 'http://127.0.0.1:8124';
        const preview = await post(server, '/macros/v2/preview', {
            directoryPath, fileName, action: 'pull', document: snapshot.document, selectedGuids
        });
        if (!Array.isArray(preview.changes) || preview.changes.length !== selectedGuids.length ||
            preview.changes.some(change => !selectedGuids.includes(change.guid) || !change.result ||
                (change.conflicts || []).length ||
                core.canonical(change.library) !== core.canonical(comparable(saved.get(change.guid)))))
            throw new Error('Библиотека изменилась во время проверки. Повторите проверку.');
        const result = await post(server, '/macros/v2/apply', {
            directoryPath, fileName, token: preview.token, operationId: preview.operationId,
            selectedGuids, resolutions: {}, comment: '',
            backupDirectory: localStorage.getItem('macrosSync_backupPath') || ''
        });
        if (!result.document || !result.expectedDocument || !result.backupPath)
            throw new Error('Сервер не подготовил документ и резервную копию.');
        if (core.canonical(result.expectedDocument) !== core.canonical(snapshot.document))
            throw new Error('Сервер вернул другой исходный документ.');
        localStorage.setItem('macrosSync_lastBackup', result.backupPath);
        await core.apply(window.Asc.plugin, window.Asc.scope, result.expectedDocument, result.document);
        return true;
    }

    function showWindow() {
        const pluginWindow = new window.Asc.PluginWindow();
        activeWindow = pluginWindow;
        pluginWindow.attachEvent('companionReady', () => {
            if (activeWindow === pluginWindow && latestResult) sendResult();
        });
        pluginWindow.attachEvent('companionRefresh', () => check());
        pluginWindow.attachEvent('companionApplyAll', () => check(true));
        pluginWindow.show({
            url: window.location.href.replace(/[^/]*$/, 'companion-window.html'),
            description: 'Проверка макросов',
            type: 'window',
            isVisual: true,
            isModal: false,
            isInsideMode: false,
            EditorsSupport: ['cell'],
            size: [460, 400],
            buttons: []
        });
    }

    function sendResult() {
        activeWindow.command('companionResult', JSON.stringify(latestResult));
    }

    function closeWindow() {
        if (!activeWindow) return;
        const pluginWindow = activeWindow;
        activeWindow = null;
        pluginWindow.close();
    }

    function render(result) {
        $('changes').replaceChildren();
        if (result.message) {
            $('status').textContent = result.message;
            $('status').dataset.state = result.state;
            $('refresh').disabled = false;
            $('applyAll').disabled = true;
            return;
        }
        for (const change of result.changed) {
            const row = document.createElement('li');
            const name = document.createElement('span');
            name.textContent = change.name || change.guid;
            const kind = document.createElement('span');
            kind.className = 'kind';
            kind.textContent = kinds[change.kind];
            row.append(name, kind);
            $('changes').append(row);
        }
        $('status').textContent = 'Найдены отличия: ' + result.changed.length;
        $('status').dataset.state = 'changes';
        $('refresh').disabled = false;
        $('applyAll').disabled = !result.changed.some(change => change.kind === 'modified' || change.kind === 'missingDocument');
    }

    async function check(applyAll = false) {
        if (checking) return;
        checking = true;
        let result;
        try {
            result = await getChanges();
            if (result && Array.isArray(result.changed) &&
                (applyAll || localStorage.getItem('macrosSync_alwaysUpdate') === 'true') &&
                await updateFromLibrary(result, applyAll))
                result = await getChanges();
            if (result && Array.isArray(result.changed)) result = { changed: result.changed };
        } catch (error) {
            console.warn('[Macros Sync Companion]', error);
            result = { message: (applyAll ? 'Применение не удалось: ' : 'Проверка не удалась: ') + error.message, state: 'error' };
        }
        try {
            latestResult = result;
            if (!result || (result.changed && result.changed.every(change => change.kind === 'missingDocument'))) {
                closeWindow();
                return;
            }
            if (activeWindow) sendResult();
            else showWindow();
        } catch (error) {
            console.warn('[Macros Sync Companion] Не удалось показать результат:', error);
        } finally {
            checking = false;
        }
    }

    window.Asc.plugin.init = function () {
        isWindow = !!$('status');
        if (isWindow) {
            if ((localStorage.getItem('ui-theme') || '').includes('dark')) document.body.classList.add('dark');
            window.Asc.plugin.event_companionResult = data => render(JSON.parse(data));
            $('refresh').addEventListener('click', () => {
                $('refresh').disabled = true;
                $('applyAll').disabled = true;
                $('status').textContent = 'Проверка обновлений…';
                $('status').dataset.state = 'checking';
                window.Asc.plugin.sendToPlugin('companionRefresh', {});
            });
            $('applyAll').addEventListener('click', () => {
                $('refresh').disabled = true;
                $('applyAll').disabled = true;
                $('status').textContent = 'Применение макросов из библиотеки…';
                $('status').dataset.state = 'checking';
                window.Asc.plugin.sendToPlugin('companionApplyAll', {});
            });
            window.Asc.plugin.sendToPlugin('companionReady', {});
            return;
        }
        return check();
    };

    window.Asc.plugin.button = function (id, windowId) {
        if (id === -1 && activeWindow && windowId === activeWindow.id) closeWindow();
    };
})(window);
