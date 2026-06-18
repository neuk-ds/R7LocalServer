(function (window, undefined) {

    // ===== Константы =====
    const LS_SERVER_URL  = 'macrosSync_serverUrl';
    const LS_DIR_PATH    = 'macrosSync_dirPath';
    const LS_FILE_NAME   = 'macrosSync_fileName';
    const LS_BACKUP_PATH = 'macrosSync_backupPath';
    const LS_AUTO_SYNC   = 'macrosSync_autoSync';
    const SEPARATOR_GUID = '00000000-separator-0000-000000000000';

    // ===== Состояние =====
    let currentMacros = [];   // macrosArray текущей книги
    let savedMacros = [];     // macrosArray из общего файла
    let selectedGuids = new Set(); // выбранные в правом столбце

    // ===== Управление разделителем (только для нормализации левого столбца) =====

    const SEPARATOR = {
        name: ' ',
        guid: SEPARATOR_GUID,
        value: '',
        autostart: false,
        isSeparator: true
    };

    function ensureSeparator(macrosArray) {
        const without = macrosArray.filter(m => !m.isSeparator && m.guid !== SEPARATOR_GUID);
        const universal = without.filter(m => m.isUniversal === true);
        const regular = without.filter(m => m.isUniversal !== true);
        if (universal.length === 0) return without;
        return [...universal, { ...SEPARATOR }, ...regular];
    }

    // ===== Утилиты =====

    function log(msg) {
        const el = document.getElementById('log');
        const time = new Date().toLocaleTimeString();
        el.textContent = `[${time}] ${msg}\n` + el.textContent;
    }

    function isUniversalCandidate(macro) {
        return macro.isUniversal === true;
    }

    function getServerUrl() {
        return document.getElementById('serverUrl').value.trim();
    }

    function getPaths() {
        return {
            directoryPath: document.getElementById('dirPath').value.trim(),
            fileName: document.getElementById('fileName').value.trim()
        };
    }

    // ===== Настройки (localStorage) =====

    function loadSettings() {
        document.getElementById('serverUrl').value  = localStorage.getItem(LS_SERVER_URL)  || 'http://127.0.0.1:8124';
        document.getElementById('dirPath').value    = localStorage.getItem(LS_DIR_PATH)    || '';
        document.getElementById('fileName').value   = localStorage.getItem(LS_FILE_NAME)   || 'universal_macros.json';
        document.getElementById('backupPath').value = localStorage.getItem(LS_BACKUP_PATH) || '';
        document.getElementById('autoSync').checked = localStorage.getItem(LS_AUTO_SYNC) === 'true';
    }

    function saveSettings() {
        localStorage.setItem(LS_SERVER_URL,  document.getElementById('serverUrl').value.trim());
        localStorage.setItem(LS_DIR_PATH,    document.getElementById('dirPath').value.trim());
        localStorage.setItem(LS_FILE_NAME,   document.getElementById('fileName').value.trim());
        localStorage.setItem(LS_BACKUP_PATH, document.getElementById('backupPath').value.trim());
        localStorage.setItem(LS_AUTO_SYNC,   document.getElementById('autoSync').checked ? 'true' : 'false');
        log('Настройки сохранены');
    }

    // ===== Тема =====

    function applyTheme() {
        const theme = localStorage.getItem('ui-theme') || '';
        if (theme.includes('dark')) {
            document.body.classList.add('dark');
        } else {
            document.body.classList.remove('dark');
        }
    }

    // ===== Рендер левого столбца (текущая книга) =====

    function renderCurrent() {
        const listEl = document.getElementById('listCurrent');
        listEl.innerHTML = '';

        currentMacros.forEach(macro => {
            const row = document.createElement('div');
            row.className = 'row' + (macro.isSeparator ? ' sep-row' : '');

            const nameSpan = document.createElement('span');
            nameSpan.className = 'name';
            nameSpan.textContent = macro.isSeparator
                ? '── разделитель ──'
                : (macro.name || '(без имени)');
            row.appendChild(nameSpan);

            if (!macro.isSeparator) {
                const label = document.createElement('label');
                label.className = 'universal-label';

                const cb = document.createElement('input');
                cb.type = 'checkbox';
                cb.checked = !!macro.isUniversal;
                cb.addEventListener('change', () => onToggleUniversal(macro, cb.checked));

                label.appendChild(cb);
                label.appendChild(document.createTextNode('универсальный'));
                row.appendChild(label);

                if (macro.isUniversal) {
                    const labelEx = document.createElement('label');
                    labelEx.className = 'universal-label exclude-label';

                    const cbEx = document.createElement('input');
                    cbEx.type = 'checkbox';
                    cbEx.checked = !!macro.isExcludedFromAutoSync;
                    cbEx.addEventListener('change', () => onToggleExcluded(macro, cbEx.checked));

                    labelEx.appendChild(cbEx);
                    labelEx.appendChild(document.createTextNode('не синхр.'));
                    row.appendChild(labelEx);
                }
            }

            listEl.appendChild(row);
        });
    }

    // ===== Рендер правого столбца (сохранённые) =====

    function renderSaved() {
        const listEl = document.getElementById('listSaved');
        listEl.innerHTML = '';

        if (savedMacros.length === 0) {
            listEl.textContent = 'Файл пуст или не загружен';
            return;
        }

        const currentGuids = new Set(currentMacros.map(m => m.guid));

        savedMacros.forEach(macro => {
            const row = document.createElement('div');
            row.className = 'row';

            const cb = document.createElement('input');
            cb.type = 'checkbox';
            cb.className = 'select-cb';
            cb.checked = selectedGuids.has(macro.guid);
            cb.addEventListener('change', () => {
                if (cb.checked) {
                    selectedGuids.add(macro.guid);
                } else {
                    selectedGuids.delete(macro.guid);
                }
            });
            row.appendChild(cb);

            const nameSpan = document.createElement('span');
            nameSpan.className = 'name';
            nameSpan.textContent = macro.name || '(без имени)';
            row.appendChild(nameSpan);

            if (currentGuids.has(macro.guid)) {
                const badge = document.createElement('span');
                badge.className = 'badge-exists';
                badge.textContent = 'есть';
                row.appendChild(badge);
            }

            listEl.appendChild(row);
        });
    }

    // ===== Модальное окно подтверждения =====

    function showConfirm(msg, conflicts, listTitle) {
        return new Promise((resolve) => {
            document.getElementById('confirmMsg').textContent = msg;

            const conflictsLabel = document.getElementById('conflictsLabel');
            const conflictsList = document.getElementById('conflictsList');
            conflictsList.innerHTML = '';

            if (conflicts.length > 0) {
                conflictsLabel.textContent = listTitle || 'Будут перезаписаны макросы:';
                conflicts.forEach(name => {
                    const li = document.createElement('li');
                    li.textContent = name;
                    conflictsList.appendChild(li);
                });
                conflictsLabel.classList.remove('hidden');
            } else {
                conflictsLabel.classList.add('hidden');
            }

            document.getElementById('confirmOverlay').classList.remove('hidden');

            function cleanup() {
                document.getElementById('confirmOverlay').classList.add('hidden');
                document.getElementById('btnConfirmOk').removeEventListener('click', onOk);
                document.getElementById('btnConfirmCancel').removeEventListener('click', onCancel);
            }

            function onOk() { cleanup(); resolve(true); }
            function onCancel() { cleanup(); resolve(false); }

            document.getElementById('btnConfirmOk').addEventListener('click', onOk);
            document.getElementById('btnConfirmCancel').addEventListener('click', onCancel);
        });
    }

    // ===== Вызовы в контекст документа =====

    function getMacrosFromDocument() {
        return new Promise((resolve) => {
            window.Asc.plugin.callCommand(function () {
                return Api.pluginMethod_GetMacros();
            }, false, false, function (result) {
                let json = JSON.parse(result);
                currentMacros = json.macrosArray || [];
                resolve(currentMacros);
            });
        });
    }

    function setMacrosInDocument(macrosArray) {
        Asc.scope.macrosArray = macrosArray;
        return new Promise((resolve) => {
            window.Asc.plugin.callCommand(function () {
                let data = JSON.parse(Api.pluginMethod_GetMacros());
                data.macrosArray = Asc.scope.macrosArray;
                Api.pluginMethod_SetMacros(JSON.stringify(data));
            }, false, false, function (result) {
                resolve(result);
            });
        });
    }

    function toggleExcludedFromAutoSync(guid, value) {
        Asc.scope.guid = guid;
        Asc.scope.value = value;
        return new Promise((resolve) => {
            window.Asc.plugin.callCommand(function () {
                let data = JSON.parse(Api.pluginMethod_GetMacros());
                let macro = data.macrosArray.find(m => m.guid === Asc.scope.guid);
                if (macro) macro.isExcludedFromAutoSync = Asc.scope.value;
                Api.pluginMethod_SetMacros(JSON.stringify(data));
            }, false, false, function (result) {
                resolve(result);
            });
        });
    }

    // ===== Загрузка сохранённых макросов с сервера =====

    async function loadSavedMacros() {
        const serverUrl = getServerUrl();
        const { directoryPath, fileName } = getPaths();

        if (!directoryPath || !fileName) {
            log('Укажите папку и имя файла в настройках');
            return;
        }

        document.getElementById('listSaved').textContent = 'Загрузка...';
        try {
            const res = await fetch(`${serverUrl}/files/read`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ directoryPath, fileName })
            });

            const json = await res.json();
            if (!res.ok) {
                log(`Ошибка чтения файла: ${json.message || res.status}`);
                document.getElementById('listSaved').textContent = 'Ошибка загрузки';
                return;
            }

            savedMacros = (json.content.macrosArray || []).filter(m => !m.isSeparator);
            selectedGuids.clear();
            renderSaved();
            log(`Загружено сохранённых макросов: ${savedMacros.length}`);
        } catch (err) {
            log('Ошибка загрузки сохранённых: ' + err.message);
            document.getElementById('listSaved').textContent = 'Ошибка загрузки';
        }
    }

    // ===== Обработчики =====

    async function loadMacros() {
        document.getElementById('listCurrent').textContent = 'Загрузка...';
        await getMacrosFromDocument();

        const fixed = ensureSeparator(currentMacros);
        const separatorChanged =
            JSON.stringify(fixed.map(m => m.guid)) !==
            JSON.stringify(currentMacros.map(m => m.guid));

        if (separatorChanged) {
            currentMacros = fixed;
            await setMacrosInDocument(currentMacros);
            log('Разделитель восстановлен');
        }

        renderCurrent();
        if (savedMacros.length > 0) renderSaved();
        log(`Загружено макросов книги: ${currentMacros.length}`);
    }

    async function onToggleUniversal(macro, checked) {
        macro.isUniversal = checked;
        if (!checked) macro.isExcludedFromAutoSync = false;
        currentMacros = ensureSeparator(currentMacros);
        await setMacrosInDocument(currentMacros);
        renderCurrent();
        log(`«${macro.name}» помечен как ${checked ? 'универсальный' : 'обычный'}`);
    }

    async function onToggleExcluded(macro, checked) {
        macro.isExcludedFromAutoSync = checked;
        await toggleExcludedFromAutoSync(macro.guid, checked);
        log(`«${macro.name}» ${checked ? 'исключён из' : 'включён в'} авто-синх.`);
    }

    function onSelectAll() {
        const allChecked = savedMacros.every(m => selectedGuids.has(m.guid));
        if (allChecked) {
            selectedGuids.clear();
        } else {
            savedMacros.forEach(m => selectedGuids.add(m.guid));
        }
        renderSaved();
    }

    async function onLoadSelected() {
        if (selectedGuids.size === 0) {
            log('Не выбрано ни одного макроса');
            return;
        }

        const serverUrl = getServerUrl();
        const { directoryPath, fileName } = getPaths();

        if (!directoryPath || !fileName) {
            log('Укажите папку и имя файла в настройках');
            return;
        }

        try {
            const res = await fetch(`${serverUrl}/macros/sync`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    mode: 'load',
                    directoryPath,
                    fileName,
                    macrosArray: currentMacros,
                    selectedGuids: [...selectedGuids]
                })
            });

            const json = await res.json();

            if (!res.ok) {
                log(`Ошибка сервера: ${json.message || res.status}`);
                return;
            }

            const msg = `Будет загружено макросов: ${selectedGuids.size}.`;
            const confirmed = await showConfirm(msg, json.conflicts || []);
            if (!confirmed) return;

            await setMacrosInDocument(json.macrosArray);
            currentMacros = json.macrosArray;
            renderCurrent();
            renderSaved();

            log(
                `Загружено. Обновлено: ${(json.updated || []).join(', ') || '—'}; ` +
                `добавлено: ${(json.added || []).join(', ') || '—'}`
            );
        } catch (err) {
            log('Ошибка: ' + err.message);
        }
    }

    async function onDeleteSelected() {
        if (selectedGuids.size === 0) {
            log('Не выбрано ни одного макроса для удаления');
            return;
        }

        const serverUrl = getServerUrl();
        const { directoryPath, fileName } = getPaths();

        if (!directoryPath || !fileName) {
            log('Укажите папку и имя файла в настройках');
            return;
        }

        const toDelete = savedMacros.filter(m => selectedGuids.has(m.guid));
        const confirmed = await showConfirm(
            `Будет удалено из файла: ${toDelete.length} макрос(ов).`,
            toDelete.map(m => m.name),
            'Будут удалены из файла:'
        );
        if (!confirmed) return;

        try {
            const res = await fetch(`${serverUrl}/macros/sync`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    mode: 'delete',
                    directoryPath,
                    fileName,
                    selectedGuids: [...selectedGuids]
                })
            });

            const json = await res.json();

            if (!res.ok) {
                log(`Ошибка сервера: ${json.message || res.status}`);
                return;
            }

            await loadSavedMacros();
            log(
                `Удалено из файла: ${(json.deleted || []).join(', ') || '—'}; ` +
                `осталось в файле: ${json.totalUniversal}`
            );
        } catch (err) {
            log('Ошибка: ' + err.message);
        }
    }

    async function onPush() {
        const serverUrl = getServerUrl();
        const { directoryPath, fileName } = getPaths();

        if (!directoryPath || !fileName) {
            log('Укажите папку и имя файла в настройках');
            return;
        }

        const universalCount = currentMacros.filter(isUniversalCandidate).length;
        if (universalCount === 0) {
            log('Нет макросов, помеченных как универсальные');
            return;
        }

        log(`Публикация ${universalCount} универсальных макросов...`);
        try {
            const res = await fetch(`${serverUrl}/macros/sync`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    directoryPath,
                    fileName,
                    mode: 'push',
                    macrosArray: currentMacros
                })
            });

            const json = await res.json();

            if (!res.ok) {
                log(`Ошибка сервера: ${json.message || res.status}`);
                return;
            }

            await loadSavedMacros();

            log(
                `Готово. Обновлено: ${(json.updated || []).join(', ') || '—'}; ` +
                `добавлено: ${(json.added || []).join(', ') || '—'}; ` +
                `всего в файле: ${json.totalUniversal}`
            );
        } catch (err) {
            log('Ошибка: ' + err.message);
        }
    }

    // ===== Инициализация =====

    window.Asc.plugin.init = function () {
        applyTheme();
        loadSettings();

        document.getElementById('btnSaveSettings').addEventListener('click', saveSettings);
        document.getElementById('btnRefresh').addEventListener('click', loadMacros);
        document.getElementById('btnSelectAll').addEventListener('click', onSelectAll);
        document.getElementById('btnLoadSelected').addEventListener('click', onLoadSelected);
        document.getElementById('btnDeleteSelected').addEventListener('click', onDeleteSelected);
        document.getElementById('btnPush').addEventListener('click', onPush);

        void Promise.all([
            loadMacros(),
            loadSavedMacros()
        ]);
    };

    window.Asc.plugin.button = function (id) {
        this.executeCommand('close', '');
    };

})(window, undefined);