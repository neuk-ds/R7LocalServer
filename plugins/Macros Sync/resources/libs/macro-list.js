(function (window) {
    'use strict';
    window.R7SyncLists = function ({ core, $, node, button, run, getData, setUniversal, setExcluded, moveMacro, moveSavedMacro }) {
        function isSeparator(macro) { return macro.isSeparator || macro.guid === '00000000-separator-0000-000000000000'; }
        function realMacros(data) { return data.macrosArray.filter(m => !isSeparator(m)); }
        function groups(macros) {
            const entries = macros.filter(m => !isSeparator(m));
            return {
                universal: entries.filter(m => m.isUniversal === true),
                regular: entries.filter(m => m.isUniversal !== true),
                separator: macros.find(isSeparator) || {
                    name: ' ', guid: '00000000-separator-0000-000000000000', value: '', autostart: false, isSeparator: true
                }
            };
        }
        function orderedMacros({ universal, regular, separator }) {
            return universal.length ? universal.concat(separator, regular) : regular;
        }
        function separatorRow() {
            const row = node('div', null, 'row sep-row');
            row.setAttribute('role', 'separator');
            row.append(node('span', 'Разделитель', 'name'));
            return row;
        }
        function selectionBox(set, guid) {
            const checkbox = node('input'); checkbox.type = 'checkbox'; checkbox.checked = set.has(guid);
            checkbox.addEventListener('change', () => checkbox.checked ? set.add(guid) : set.delete(guid));
            return checkbox;
        }
        function render() {
            const { doc, library, state, currentSelection, savedSelection, orderDirty } = getData();
            const currentScroll = $('listCurrent').scrollTop, savedScroll = $('listSaved').scrollTop;
            $('listCurrent').replaceChildren(); $('listSaved').replaceChildren();
            const differences = state ? core.compare(doc, library) : [];
            const differenceByGuid = new Map(differences.map(change => [change.guid, change.kind]));
            const bookGroups = groups(doc.macrosArray);
            doc.macrosArray.forEach(macro => {
                if (isSeparator(macro)) { $('listCurrent').append(separatorRow()); return; }
                const row = node('div', null, 'row');
                row.setAttribute('data-guid', macro.guid);
                row.append(selectionBox(currentSelection, macro.guid), node('span', macro.name || macro.guid, 'name'));
                if (differenceByGuid.has(macro.guid)) row.append(node('span', {
                    modified: 'отличается', missingLibrary: 'нет в библиотеке'
                }[differenceByGuid.get(macro.guid)] || '', 'badge-exists'));
                const label = node('label', null, 'universal-label');
                const check = node('input'); check.type = 'checkbox'; check.checked = macro.isUniversal === true;
                check.addEventListener('change', () => run(() => setUniversal(macro.guid, check.checked)));
                label.append(check, document.createTextNode('универсальный')); row.append(label);
                if (macro.isUniversal) {
                    const excluded = node('input'); excluded.type = 'checkbox'; excluded.checked = !!macro.isExcludedFromAutoSync;
                    const exLabel = node('label', null, 'universal-label');
                    excluded.addEventListener('change', () => run(() => setExcluded(macro.guid, excluded.checked)));
                    exLabel.append(excluded, document.createTextNode('не проверять')); row.append(exLabel);
                }
                const group = macro.isUniversal === true ? bookGroups.universal : bookGroups.regular;
                const index = group.findIndex(m => m.guid === macro.guid);
                const controls = node('span', null, 'move-controls');
                for (const [direction, symbol, title] of [[-1, '↑', 'Поднять'], [1, '↓', 'Опустить']]) {
                    const move = button(symbol, () => moveMacro(macro.guid, direction));
                    move.setAttribute('aria-label', title + ' макрос «' + (macro.name || macro.guid) + '»');
                    move.title = title;
                    move.disabled = index + direction < 0 || index + direction >= group.length;
                    controls.append(move);
                }
                row.append(controls);
                $('listCurrent').append(row);
            });
            const allLibraryIds = new Set(realMacros(library).map(m => m.guid));
            // Deleted library macros remain visible so their deletion can be reviewed explicitly.
            const missing = realMacros(doc).filter(m => m._r7Sync && m._r7Sync.libraryId === (library._r7Library || {}).libraryId && !allLibraryIds.has(m.guid));
            library.macrosArray.concat(missing).forEach((macro, index) => {
                if (isSeparator(macro)) { $('listSaved').append(separatorRow()); return; }
                const row = node('div', null, 'row');
                row.setAttribute('data-guid', macro.guid);
                row.append(selectionBox(savedSelection, macro.guid), node('span', macro.name || macro.guid, 'name'));
                const label = !allLibraryIds.has(macro.guid) ? 'удалён из библиотеки' : {
                    modified: 'отличается от книги', missingDocument: 'нет в книге'
                }[differenceByGuid.get(macro.guid)];
                if (label) row.append(node('span', label, 'badge-exists'));
                if (allLibraryIds.has(macro.guid)) {
                    const controls = node('span', null, 'move-controls');
                    for (const [direction, symbol, title] of [[-1, '↑', 'Поднять'], [1, '↓', 'Опустить']]) {
                        const move = button(symbol, () => moveSavedMacro(macro.guid, direction));
                        move.setAttribute('aria-label', title + ' макрос «' + (macro.name || macro.guid) + '» в библиотеке');
                        move.title = title;
                        const neighbor = library.macrosArray[index + direction];
                        move.disabled = !state?.managed || state.externalChanges || !neighbor || isSeparator(neighbor);
                        controls.append(move);
                    }
                    row.append(controls);
                }
                $('listSaved').append(row);
            });
            $('listCurrent').scrollTop = currentScroll;
            $('listSaved').scrollTop = savedScroll;
            $('comparisonStatus').textContent = state ? differences.length
                ? 'Отличий книги от библиотеки: ' + differences.length
                : 'Макросы книги совпадают с библиотекой.' : '';
            $('libraryStatus').textContent = !state ? '' : !state.managed ? 'История ещё не включена' :
                state.externalChanges ? 'Обнаружены внешние изменения. Зарегистрируйте их перед синхронизацией.' : 'Версия: ' + library._r7Library.revisionId;
            $('btnImport').textContent = state && state.managed ? 'Проверить внешние изменения' : 'Включить историю';
            $('btnSaveOrder').disabled = !orderDirty;
        }
        function listPositions(id) {
            if (typeof window.matchMedia === 'function' && window.matchMedia('(prefers-reduced-motion: reduce)').matches) return null;
            const rows = Array.from($(id).children).filter(row => typeof row.animate === 'function');
            return new Map(rows.map(row => [row.getAttribute('data-guid'), row.getBoundingClientRect().top]));
        }
        function animateMove(id, before) {
            if (!before) return;
            for (const row of $(id).children) {
                if (typeof row.animate !== 'function') continue;
                const previous = before.get(row.getAttribute('data-guid'));
                if (previous == null) continue;
                const distance = previous - row.getBoundingClientRect().top;
                if (Math.abs(distance) < 1) continue;
                row.animate([{ transform: `translateY(${distance}px)` }, { transform: 'translateY(0)' }],
                    { duration: 180, easing: 'ease-out' });
            }
        }
        return {
            isSeparator, realMacros, groups, orderedMacros, selectionBox, render,
            bookPositions: () => listPositions('listCurrent'), savedPositions: () => listPositions('listSaved'),
            animateBookMove: before => animateMove('listCurrent', before),
            animateSavedMove: before => animateMove('listSaved', before)
        };
    };
})(window);
