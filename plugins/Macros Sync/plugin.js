(function (window) {
    'use strict';
    const core = window.R7SyncCore;
    const $ = id => document.getElementById(id);
    const kinds = { added: 'Добавлен', modified: 'Изменён', deleted: 'Удалён', unchanged: 'Без изменений', conflict: 'Конфликт' };
    const settings = { serverUrl: 'http://127.0.0.1:8124', dirPath: '', fileName: 'universal_macros.json', backupPath: '' };
    let doc = { macrosArray: [] }, library = { macrosArray: [] }, state, active, busy = false;
    let currentSelection = new Set(), savedSelection = new Set();
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
    async function api(path, body) {
        const response = await fetch($('serverUrl').value.trim().replace(/\/$/, '') + path, {
            method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body)
        });
        if (response.status === 404 && path.startsWith('/macros/v2')) throw new Error('Требуется обновить R7LocalServer до версии с Macros Sync v2.');
        let result;
        try { result = await response.json(); } catch (_) { throw new Error('Сервер вернул некорректный ответ'); }
        if (!response.ok) throw new Error(result.message || 'Ошибка сервера: ' + response.status);
        return result;
    }
    function realMacros(data) { return data.macrosArray.filter(m => !m.isSeparator && m.guid !== '00000000-separator-0000-000000000000'); }
    function selectionBox(set, guid) {
        const checkbox = node('input'); checkbox.type = 'checkbox'; checkbox.checked = set.has(guid);
        checkbox.addEventListener('change', () => checkbox.checked ? set.add(guid) : set.delete(guid));
        return checkbox;
    }
    function render() {
        $('listCurrent').replaceChildren(); $('listSaved').replaceChildren();
        realMacros(doc).forEach(macro => {
            const row = node('div', null, 'row');
            row.append(selectionBox(currentSelection, macro.guid), node('span', macro.name || macro.guid, 'name'));
            const label = node('label', null, 'universal-label');
            const check = node('input'); check.type = 'checkbox'; check.checked = macro.isUniversal === true;
            check.addEventListener('change', () => run(async () => {
                const latest = await core.read(Asc.plugin), next = JSON.parse(JSON.stringify(latest));
                const target = next.macrosArray.find(m => m.guid === macro.guid);
                if (!target) throw new Error('Макрос удалён. Обновите список.');
                target.isUniversal = check.checked;
                if (!check.checked) target.isExcludedFromAutoSync = false;
                await core.apply(Asc.plugin, Asc.scope, latest, next);
                await refresh();
            }));
            label.append(check, document.createTextNode('универсальный')); row.append(label);
            if (macro.isUniversal) {
                const excluded = node('input'); excluded.type = 'checkbox'; excluded.checked = !!macro.isExcludedFromAutoSync;
                const exLabel = node('label', null, 'universal-label');
                excluded.addEventListener('change', () => run(async () => {
                    const latest = await core.read(Asc.plugin), next = JSON.parse(JSON.stringify(latest));
                    const target = next.macrosArray.find(m => m.guid === macro.guid);
                    if (!target) throw new Error('Макрос удалён. Обновите список.');
                    target.isExcludedFromAutoSync = excluded.checked;
                    await core.apply(Asc.plugin, Asc.scope, latest, next); await refresh();
                }));
                exLabel.append(excluded, document.createTextNode('не проверять')); row.append(exLabel);
            }
            $('listCurrent').append(row);
        });
        const allLibraryIds = new Set(realMacros(library).map(m => m.guid));
        // Deleted library macros remain visible so their deletion can be reviewed explicitly.
        const missing = realMacros(doc).filter(m => m._r7Sync && m._r7Sync.libraryId === (library._r7Library || {}).libraryId && !allLibraryIds.has(m.guid));
        realMacros(library).concat(missing).forEach(macro => {
            const row = node('div', null, 'row');
            row.append(selectionBox(savedSelection, macro.guid), node('span', macro.name || macro.guid, 'name'));
            if (!allLibraryIds.has(macro.guid)) row.append(node('span', 'удалён из библиотеки', 'badge-exists'));
            $('listSaved').append(row);
        });
        $('libraryStatus').textContent = !state ? '' : !state.managed ? 'История ещё не включена' :
            state.externalChanges ? 'Обнаружены внешние изменения. Зарегистрируйте их перед синхронизацией.' : 'Версия: ' + library._r7Library.revisionId;
        $('btnImport').textContent = state && state.managed ? 'Проверить внешние изменения' : 'Включить историю';
    }
    async function refresh() {
        doc = await core.read(Asc.plugin);
        state = await api('/macros/v2/state', paths()); library = state.library;
        render();
    }
    async function preview(action, revisionId, replacementDocument) {
        doc = await core.read(Asc.plugin);
        const selectedGuids = action === 'push' ? [...currentSelection].filter(id => realMacros(doc).some(m => m.guid === id && m.isUniversal)) : [...savedSelection];
        if (['push', 'pull', 'delete'].includes(action) && !selectedGuids.length) throw new Error('Выберите макросы; для публикации — универсальные.');
        const request = Object.assign(paths(), { action, document: doc, selectedGuids, revisionId: revisionId || null, replacementDocument: replacementDocument || null });
        const response = await api('/macros/v2/preview', request);
        active = { action, request, response, selected: new Set(response.changes.map(c => c.guid)), resolutions: {}, reviewed: new Set(), editors: new Map() };
        drawReview();
    }
    function drawReview() {
        const panel = $('reviewChanges'); panel.replaceChildren();
        $('reviewTitle').textContent = ({ push: 'Сохранить в библиотеку', pull: 'Применить к документу', delete: 'Удалить из библиотеки', restore: 'Восстановить версию библиотеки', import: 'Зарегистрировать библиотеку', restoreDocument: 'Восстановить резервную копию документа' })[active.action];
        active.response.changes.forEach(change => {
            const card = node('details', null, 'change-card'); card.open = change.kind === 'conflict';
            const summary = node('summary');
            const include = selectionBox(active.selected, change.guid);
            include.disabled = ['restore', 'import', 'restoreDocument'].includes(active.action);
            include.addEventListener('change', updateApply);
            summary.append(include, document.createTextNode(' ' + change.name + ' — ' + kinds[change.kind]));
            card.append(summary);
            const versions = node('div', null, 'versions');
            [['База', change.base], ['Документ / входящая версия', change.document], ['Библиотека / текущая версия', change.library]].forEach(([title, macro]) => {
                const block = node('details'); block.append(node('summary', title), node('pre', macro == null ? '(отсутствует)' : JSON.stringify(macro, null, 2)));
                versions.append(block);
            });
            card.append(versions);
            const diff = node('pre', null, 'diff');
            change.diff.forEach(line => diff.append(node('div', String(line.oldLine || '').padStart(4) + ' ' + String(line.newLine || '').padStart(4) + ' ' + ({ added: '+', removed: '-', same: ' ' })[line.kind] + ' ' + line.text, line.kind)));
            card.append(diff);
            const editor = node('textarea'); editor.className = 'result-code'; editor.spellcheck = false;
            editor.value = change.result ? change.result.value : '';
            const metadata = node('textarea'); metadata.className = 'result-metadata'; metadata.spellcheck = false;
            let resultMacro = change.result && JSON.parse(JSON.stringify(change.result));
            function showResult(macro) {
                resultMacro = macro == null ? null : JSON.parse(JSON.stringify(macro));
                editor.value = resultMacro ? resultMacro.value : '';
                const fields = resultMacro && Object.assign({}, resultMacro); if (fields) delete fields.value;
                metadata.value = JSON.stringify(fields, null, 2);
                editor.disabled = resultMacro == null;
            }
            showResult(change.result);
            const controls = node('div', null, 'review-controls');
            function choose(macro) {
                showResult(macro);
                active.resolutions[change.guid] = { macro: resultMacro };
                active.reviewed.add(change.guid); updateApply();
            }
            controls.append(button('Взять документ', () => choose(change.document)), button('Взять библиотеку', () => choose(change.library)));
            const decisions = new Map();
            if (change.chunks.some(c => c.document != null)) {
                const conflicts = node('div');
                change.chunks.forEach((chunk, index) => {
                    if (chunk.document == null) return;
                    const block = node('div', null, 'conflict-block');
                    block.append(node('pre', 'Документ:\n' + chunk.document + '\nБиблиотека:\n' + chunk.library));
                    function pick(side) {
                        decisions.set(index, chunk[side]);
                        editor.value = change.chunks.map((c, i) => c.document == null ? c.text : decisions.has(i) ? decisions.get(i) : c.document).join('');
                        active.reviewed.delete(change.guid);
                        active.resolutions[change.guid] = { macro: resultMacro }; updateApply();
                        block.classList.add('resolved');
                    }
                    block.append(button('Участок из документа', () => pick('document')), button('Участок из библиотеки', () => pick('library')));
                    conflicts.append(block);
                });
                card.append(conflicts);
            }
            controls.append(button('Подтвердить итог этого макроса', () => {
                let fields = JSON.parse(metadata.value);
                if (fields != null) {
                    if (fields.guid !== change.guid) throw new Error('GUID результата нельзя менять');
                    fields.value = editor.value;
                }
                resultMacro = fields;
                active.resolutions[change.guid] = { macro: fields };
                active.reviewed.add(change.guid); updateApply();
            }));
            const edited = () => { active.reviewed.delete(change.guid); active.resolutions[change.guid] = { macro: resultMacro }; updateApply(); };
            editor.addEventListener('input', edited); metadata.addEventListener('input', edited);
            card.append(node('div', 'Итоговый код'), editor, node('div', 'Итоговые свойства (null — удалить макрос)'), metadata, controls);
            if (['import', 'restore', 'restoreDocument', 'delete'].includes(active.action)) {
                editor.readOnly = true; metadata.readOnly = true; controls.replaceChildren();
            }
            panel.append(card);
        });
        if (!active.response.changes.length) panel.append(node('p', 'Изменений макросов нет. Можно зарегистрировать исходное состояние библиотеки.'));
        $('reviewOverlay').classList.remove('hidden');
        $('reviewComment').value = '';
        updateApply();
    }
    function updateApply() {
        if (!active) return;
        const unresolved = active.response.changes.filter(c => active.selected.has(c.guid) &&
            (c.conflicts.length || Object.prototype.hasOwnProperty.call(active.resolutions, c.guid)) && !active.reviewed.has(c.guid));
        $('reviewStatus').textContent = unresolved.length ? 'Требуют подтверждения: ' + unresolved.map(c => c.name).join(', ') : 'Итог готов к применению';
        $('btnApply').disabled = unresolved.length > 0;
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
    window.Asc.plugin.button = function () { if (!busy) this.executeCommand('close', ''); };
})(window);
