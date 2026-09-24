(function (window) {
    'use strict';
    const core = window.R7SyncCore;
    const $ = id => document.getElementById(id);
    const settings = { serverUrl: 'http://127.0.0.1:8124', dirPath: '', fileName: 'universal_macros.json', backupPath: '' };
    let doc = { macrosArray: [] }, library = { macrosArray: [] }, state, active, busy = false;
    let currentSelection = new Set(), savedSelection = new Set();
    let savedOrderBase = [], orderLocation = null, orderServerUrl = null;
    function libraryOrder() { return library.macrosArray.map(m => m.guid); }
    function orderDirty() { return savedOrderBase.length === library.macrosArray.length &&
        savedOrderBase.some((guid, index) => guid !== library.macrosArray[index].guid); }
    function log(message) { $('log').textContent = '[' + new Date().toLocaleTimeString() + '] ' + message + '\n' + $('log').textContent; }
    function node(tag, text, className) {
        const element = document.createElement(tag);
        if (text != null) element.textContent = text;
        if (className) element.className = className;
        return element;
    }
    function button(text, action) {
        const element = node('button', text);
        element.type = 'button'; element.addEventListener('click', () => run(action));
        return element;
    }
    async function run(action) {
        if (busy) return;
        busy = true;
        document.body.classList.add('busy');
        try { await action(); } catch (error) { log(error.message); }
        finally { busy = false; document.body.classList.remove('busy'); }
    }
    function paths() { return { directoryPath: $('dirPath').value.trim(), fileName: $('fileName').value.trim() }; }
    async function api(path, body, serverUrl = $('serverUrl').value.trim()) {
        const url = serverUrl.replace(/\/$/, '') + path;
        let response;
        try {
            response = await fetch(url, {
                method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body)
            });
        } catch (error) {
            throw new Error('Не удалось обратиться к серверу ' + url + '. Проверьте, что R7LocalServer запущен, а адрес и порт верны.');
        }
        if (response.status === 404 && path.startsWith('/macros/v2')) throw new Error('Требуется обновить R7LocalServer до версии с Macros Sync v2.');
        let result;
        try { result = await response.json(); } catch (_) { throw new Error('Сервер вернул некорректный ответ'); }
        if (!response.ok) throw new Error(result.message || 'Ошибка сервера: ' + response.status);
        return result;
    }
    const { isSeparator, realMacros, groups, orderedMacros, selectionBox, render, bookPositions, savedPositions, animateBookMove, animateSavedMove } =
        window.R7SyncLists({
            core, $, node, button, run,
            getData: () => ({ doc, library, state, currentSelection, savedSelection, orderDirty: orderDirty() }),
            setUniversal, setExcluded, moveMacro, moveSavedMacro
        });
    const { drawReview } = window.R7SyncReviewUI({ $, node, button, selectionBox, getActive: () => active });
    async function setUniversal(guid, enabled) {
        const latest = await core.read(Asc.plugin), next = JSON.parse(JSON.stringify(latest));
        const target = next.macrosArray.find(m => m.guid === guid && !isSeparator(m));
        if (!target) throw new Error('Макрос удалён. Обновите список.');
        const grouped = groups(next.macrosArray.filter(m => m.guid !== guid));
        target.isUniversal = enabled;
        if (enabled) grouped.universal.push(target);
        else {
            target.isExcludedFromAutoSync = false;
            grouped.regular.unshift(target);
        }
        next.macrosArray = orderedMacros(grouped);
        await core.apply(Asc.plugin, Asc.scope, latest, next);
        await updateBook();
    }
    async function setExcluded(guid, excluded) {
        const latest = await core.read(Asc.plugin), next = JSON.parse(JSON.stringify(latest));
        const target = next.macrosArray.find(m => m.guid === guid && !isSeparator(m));
        if (!target) throw new Error('Макрос удалён. Обновите список.');
        target.isExcludedFromAutoSync = excluded;
        await core.apply(Asc.plugin, Asc.scope, latest, next);
        await updateBook();
    }
    async function moveMacro(guid, direction) {
        const latest = await core.read(Asc.plugin), next = JSON.parse(JSON.stringify(latest));
        const grouped = groups(next.macrosArray);
        const target = next.macrosArray.find(m => m.guid === guid && !isSeparator(m));
        if (!target) throw new Error('Макрос удалён. Обновите список.');
        const group = target.isUniversal === true ? grouped.universal : grouped.regular;
        const index = group.findIndex(m => m.guid === guid), destination = index + direction;
        if (destination < 0 || destination >= group.length) { await updateBook(); return; }
        [group[index], group[destination]] = [group[destination], group[index]];
        next.macrosArray = orderedMacros(grouped);
        await core.apply(Asc.plugin, Asc.scope, latest, next);
        await updateBook(true);
    }
    function moveSavedMacro(guid, direction) {
        if (!state?.managed || state.externalChanges || !library._r7Library?.revisionId)
            throw new Error('Сначала включите историю библиотеки и зарегистрируйте внешние изменения.');
        const macros = library.macrosArray.slice();
        const index = macros.findIndex(m => m.guid === guid && !isSeparator(m));
        const destination = index + direction;
        if (index < 0 || destination < 0 || destination >= macros.length || isSeparator(macros[destination])) return;
        [macros[index], macros[destination]] = [macros[destination], macros[index]];
        const before = savedPositions();
        library = Object.assign({}, library, { macrosArray: macros });
        render();
        animateSavedMove(before);
    }
    async function saveOrder() {
        if (!orderDirty()) return;
        const updated = await api('/macros/v2/order', Object.assign({}, orderLocation, {
            revisionId: library._r7Library.revisionId,
            expectedGuids: savedOrderBase,
            orderedGuids: libraryOrder()
        }), orderServerUrl);
        state = updated; library = updated.library;
        savedOrderBase = libraryOrder();
        render();
        log('Порядок макросов библиотеки сохранён.');
    }
    async function updateBook(animateMove = false) {
        doc = await core.read(Asc.plugin);
        const before = animateMove ? bookPositions() : null;
        render();
        animateBookMove(before);
    }
    async function refresh() {
        await saveOrder();
        doc = await core.read(Asc.plugin);
        state = undefined; library = { macrosArray: [] };
        render();
        state = await api('/macros/v2/state', paths()); library = state.library;
        savedOrderBase = libraryOrder(); orderLocation = paths(); orderServerUrl = $('serverUrl').value.trim();
        render();
    }
    async function preview(action, revisionId, replacementDocument) {
        await saveOrder();
        doc = await core.read(Asc.plugin);
        const selectedGuids = action === 'push' ? [...currentSelection].filter(id => realMacros(doc).some(m => m.guid === id && m.isUniversal)) : [...savedSelection];
        if (['push', 'pull', 'delete'].includes(action) && !selectedGuids.length) throw new Error('Выберите макросы; для публикации — универсальные.');
        const request = Object.assign(paths(), { action, document: doc, selectedGuids, revisionId: revisionId || null, replacementDocument: replacementDocument || null });
        const response = await api('/macros/v2/preview', request);
        active = { action, request, response, selected: new Set(response.changes.map(c => c.guid)), resolutions: {}, reviewed: new Set(), editors: new Map() };
        drawReview();
    }
    async function applyReview() {
        if (!active) return;
        const current = await core.read(Asc.plugin);
        if (active.prepared && core.canonical(current) === core.canonical(active.prepared.document)) {
            log('Запись в документ подтверждена повторным чтением. Сохраните книгу в Р7.');
            $('reviewOverlay').classList.add('hidden'); active = null; await refresh(); return;
        }
        if (core.canonical(current) !== core.canonical(active.request.document)) throw new Error('Документ изменился. Нажмите «Пересчитать».');
        const selectedGuids = [...active.selected];
        const resolutions = Object.fromEntries(Object.entries(active.resolutions).filter(([id]) => active.selected.has(id)));
        // Preserve the exact request for retries, even if the network response is lost.
        const candidate = Object.assign({}, { directoryPath: active.request.directoryPath, fileName: active.request.fileName }, {
            token: active.response.token, operationId: active.response.operationId, selectedGuids, resolutions,
            comment: $('reviewComment').value, backupDirectory: $('backupPath').value.trim()
        });
        if (active.applyRequest && core.canonical(active.applyRequest) !== core.canonical(candidate))
            throw new Error('Результат предыдущей попытки неизвестен. Верните прежний выбор для повтора либо перечитайте библиотеку и пересчитайте изменения.');
        if (!active.applyRequest) active.applyRequest = candidate;
        const result = await api('/macros/v2/apply', active.applyRequest);
        if (result.backupPath) { localStorage.setItem('macrosSync_lastBackup', result.backupPath); log('Резервная копия: ' + result.backupPath); }
        if (result.document && result.changed) {
            active.prepared = result;
            const written = await core.apply(Asc.plugin, Asc.scope, result.expectedDocument, result.document);
            if (written.metadataLost) log('Р7 не сохранил базу синхронизации. Дальнейшие различия потребуют ручного выбора.');
            log('Макросы применены к открытой книге. Сохраните документ в Р7.');
        } else log(result.changed ? 'Сохранена версия ' + result.revisionId : 'Изменений для сохранения нет.');
        $('reviewOverlay').classList.add('hidden'); active = null; await refresh();
    }
    async function showHistory() {
        const records = await api('/macros/v2/history', paths());
        $('historyList').replaceChildren();
        records.forEach(record => {
            const row = node('div', null, 'history-row');
            row.append(node('span', new Date(record.time).toLocaleString() + ' · ' + record.author + ' · ' + (record.comment || 'Без комментария') + ' · ' + record.id));
            row.append(button('Просмотреть и восстановить', async () => {
                $('historyOverlay').classList.add('hidden'); await preview('restore', record.id);
            }));
            $('historyList').append(row);
        });
        $('historyOverlay').classList.remove('hidden');
    }
    async function restoreBackup() {
        const path = window.prompt('Полный путь к резервной копии макросов:', localStorage.getItem('macrosSync_lastBackup') || '');
        if (!path) return;
        const split = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        if (split < 0) throw new Error('Укажите полный путь к копии');
        const response = await api('/files/read', { directoryPath: path.slice(0, split), fileName: path.slice(split + 1) });
        const backup = typeof response.content === 'string' ? JSON.parse(response.content) : response.content;
        await preview('restoreDocument', null, backup);
    }
    window.Asc.plugin.init = function () {
        for (const [key, fallback] of Object.entries(settings)) $(key).value = localStorage.getItem('macrosSync_' + key) || fallback;
        $('autoSync').checked = localStorage.getItem('macrosSync_autoSync') === 'true';
        if ((localStorage.getItem('ui-theme') || '').includes('dark')) document.body.classList.add('dark');
        const bind = (id, fn) => $(id).addEventListener('click', () => run(fn));
        bind('btnSaveSettings', () => {
            for (const key of Object.keys(settings)) localStorage.setItem('macrosSync_' + key, $(key).value.trim());
            localStorage.setItem('macrosSync_autoSync', String($('autoSync').checked)); log('Настройки сохранены');
        });
        bind('btnRefresh', refresh);
        bind('btnSaveOrder', saveOrder);
        bind('btnSelectCurrent', () => { currentSelection = new Set(realMacros(doc).filter(m => m.isUniversal).map(m => m.guid)); render(); });
        bind('btnSelectAll', () => { savedSelection = new Set(realMacros(library).map(m => m.guid)); render(); });
        bind('btnPush', () => preview('push')); bind('btnLoadSelected', () => preview('pull'));
        bind('btnDeleteSelected', () => preview('delete')); bind('btnImport', () => preview('import'));
        bind('btnHistory', showHistory); bind('btnRestoreBackup', restoreBackup);
        bind('btnApply', applyReview);
        bind('btnRecalculate', () => preview(active.action, active.request.revisionId, active.request.replacementDocument));
        bind('btnCancelReview', () => { $('reviewOverlay').classList.add('hidden'); active = null; });
        bind('btnCloseHistory', () => $('historyOverlay').classList.add('hidden'));
        run(refresh);
    };
    window.Asc.plugin.button = function () {
        if (!busy) return run(async () => { await saveOrder(); window.Asc.plugin.executeCommand('close', ''); });
    };
})(window);
