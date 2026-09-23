(function (window) {
    'use strict';
    window.R7SyncReviewUI = function ({ $, node, button, selectionBox, getActive }) {
        const kinds = { added: 'Добавлен', modified: 'Изменён', deleted: 'Удалён', unchanged: 'Без изменений', conflict: 'Конфликт' };
        function drawReview() {
            const active = getActive();
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
            const active = getActive();
            if (!active) return;
            const unresolved = active.response.changes.filter(c => active.selected.has(c.guid) &&
                (c.conflicts.length || Object.prototype.hasOwnProperty.call(active.resolutions, c.guid)) && !active.reviewed.has(c.guid));
            $('reviewStatus').textContent = unresolved.length ? 'Требуют подтверждения: ' + unresolved.map(c => c.name).join(', ') : 'Итог готов к применению';
            $('btnApply').disabled = unresolved.length > 0;
        }
        return { drawReview, updateApply };
    };
})(window);
