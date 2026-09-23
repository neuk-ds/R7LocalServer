(function (window) {
    'use strict';
    const $ = id => document.getElementById(id);
    const kinds = { modified: 'Отличается от библиотеки', missingDocument: 'Нет в книге', missingLibrary: 'Нет в библиотеке' };
    const localFields = new Set(['_r7Sync', 'isUniversal', 'isExcludedFromAutoSync', 'isSeparator']);
    const separatorGuid = '00000000-separator-0000-000000000000';
    let checking = false;

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

    function status(message, state) {
        $('status').textContent = message;
        $('status').dataset.state = state;
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

    // Read-only checker: never calls SetMacros or a write endpoint.
    async function check() {
        if (checking) return;
        checking = true;
        $('refresh').disabled = true;
        $('changes').replaceChildren();
        status('Проверка обновлений…', 'checking');
        try {
            if (localStorage.getItem('macrosSync_autoSync') !== 'true') {
                status('Проверка выключена. Включите её в настройках «Синхронизация макросов».', 'idle');
                return;
            }
            const directoryPath = localStorage.getItem('macrosSync_dirPath');
            const fileName = localStorage.getItem('macrosSync_fileName') || 'universal_macros.json';
            const server = localStorage.getItem('macrosSync_serverUrl') || 'http://127.0.0.1:8124';
            if (!directoryPath) {
                status('Укажите папку библиотеки в настройках «Синхронизация макросов».', 'idle');
                return;
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
            if (!changed.length) {
                status('Отличий от библиотеки нет.', 'ok');
                return;
            }
            for (const change of changed) {
                const row = document.createElement('li');
                const name = document.createElement('span');
                name.textContent = change.name || change.guid;
                const kind = document.createElement('span');
                kind.className = 'kind';
                kind.textContent = kinds[change.kind];
                row.append(name, kind);
                $('changes').append(row);
            }
            status('Найдены отличия: ' + changed.length, 'changes');
        } catch (error) {
            console.warn('[Macros Sync Companion]', error);
            status('Проверка не удалась: ' + error.message, 'error');
        } finally {
            checking = false;
            $('refresh').disabled = false;
        }
    }

    window.Asc.plugin.init = function () {
        if ((localStorage.getItem('ui-theme') || '').includes('dark')) document.body.classList.add('dark');
        $('refresh').addEventListener('click', check);
        check();
    };
})(window);
