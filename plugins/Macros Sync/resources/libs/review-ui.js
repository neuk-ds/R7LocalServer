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
                const editor = node('textarea'); editor.className = 'result-code'; editor.spellcheck = false;
                editor.wrap = 'off';
                const columns = node('div', null, 'review-code-columns');
                const diffPanel = node('div', null, 'review-code-panel');
                const resultPanel = node('div', null, 'review-code-panel');
                diffPanel.append(node('div', 'Изменения', 'review-code-title'), diff);
                resultPanel.append(node('div', 'Итоговый код', 'review-code-title'), editor);
                columns.append(diffPanel, resultPanel);
                card.append(columns);
                const newToDiff = [];
                const diffToNew = [];
                let lastNewLine = 0;
                change.diff.forEach((line, index) => {
                    if (line.newLine != null) {
                        lastNewLine = line.newLine - 1;
                        newToDiff[lastNewLine] = index;
                    }
                    diffToNew[index] = lastNewLine;
                });
                let lineMappingValid = true;
                let resultLineCount = 1;
                let pendingScroll = null;
                function lineHeight(element) {
                    return parseFloat(window.getComputedStyle(element).lineHeight) || 18;
                }
                function syncScroll(source, target, map, sourceLines, targetLines) {
                    if (!sourceLines) return;
                    if (pendingScroll && pendingScroll.target === source && Math.abs(source.scrollTop - pendingScroll.top) < 1) {
                        pendingScroll = null;
                        return;
                    }
                    pendingScroll = null;
                    const sourceLineHeight = lineHeight(source);
                    const targetLineHeight = lineHeight(target);
                    const sourceIndex = Math.min(sourceLines - 1, Math.max(0, Math.floor(source.scrollTop / sourceLineHeight)));
                    const fraction = source.scrollTop / sourceLineHeight - sourceIndex;
                    const targetIndex = lineMappingValid && map[sourceIndex] != null ? map[sourceIndex] :
                        Math.round(sourceIndex * (targetLines - 1) / Math.max(1, sourceLines - 1));
                    const top = (targetIndex + fraction) * targetLineHeight;
                    if (Math.abs(target.scrollTop - top) < 1) return;
                    pendingScroll = { target, top };
                    target.scrollTop = top;
                }
                diff.addEventListener('scroll', () => syncScroll(diff, editor, diffToNew, change.diff.length, resultLineCount));
                editor.addEventListener('scroll', () => syncScroll(editor, diff, newToDiff, resultLineCount, change.diff.length));
                const metadata = node('textarea'); metadata.className = 'result-metadata'; metadata.spellcheck = false;
                let resultMacro = change.result && JSON.parse(JSON.stringify(change.result));
                function showResult(macro) {
                    resultMacro = macro == null ? null : JSON.parse(JSON.stringify(macro));
                    editor.value = resultMacro ? resultMacro.value : '';
                    resultLineCount = editor.value.split('\n').length;
                    lineMappingValid = editor.value === (change.result ? change.result.value : '');
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
                            resultLineCount = editor.value.split('\n').length;
                            lineMappingValid = editor.value === (change.result ? change.result.value : '');
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
                editor.addEventListener('input', () => { lineMappingValid = false; resultLineCount = editor.value.split('\n').length; edited(); });
                metadata.addEventListener('input', edited);
                card.append(node('div', 'Итоговые свойства (null — удалить макрос)'), metadata, controls);
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
