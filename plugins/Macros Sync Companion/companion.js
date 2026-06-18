(function (window, undefined) {
    console.log('[MacrosSync companion] скрипт загружен');

    // ===== Константы =====
    const LS_SERVER_URL  = 'macrosSync_serverUrl';
    const LS_DIR_PATH    = 'macrosSync_dirPath';
    const LS_FILE_NAME   = 'macrosSync_fileName';
    const LS_BACKUP_PATH = 'macrosSync_backupPath';
    const LS_AUTO_SYNC   = 'macrosSync_autoSync';

    // ===== Запрос к серверу =====

    function fetchJson(serverUrl, path, body) {
        return fetch(`${serverUrl}${path}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        }).then(res => res.json().then(json => ({ ok: res.ok, json })));
    }

    // ===== Получить макросы из документа =====

    function getMacrosFromDocument() {
        return new Promise((resolve) => {
            window.Asc.plugin.callCommand(function () {
                return Api.pluginMethod_GetMacros();
            }, false, false, function (result) {
                let data = { macrosArray: [] };
                try {
                    if (result) data = JSON.parse(result);
                } catch (e) {
                    console.warn('[MacrosSync Companion] Не удалось распарсить макросы книги, используем пустой массив');
                }
                resolve(data);
            });
        });
    }

    // ===== Записать макросы в документ =====

    function setMacrosInDocument(data) {
        Asc.scope.data = data;
        return new Promise((resolve) => {
            window.Asc.plugin.callCommand(function () {
                Api.pluginMethod_SetMacros(JSON.stringify(Asc.scope.data));
            }, false, false, function () {
                resolve();
            });
        });
    }

    // ===== Основная логика синхронизации =====

    async function autoSync() {
        const serverUrl  = localStorage.getItem(LS_SERVER_URL)  || 'http://127.0.0.1:8124';
        const dirPath    = localStorage.getItem(LS_DIR_PATH)    || '';
        const fileName   = localStorage.getItem(LS_FILE_NAME)   || 'universal_macros.json';
        const backupPath = localStorage.getItem(LS_BACKUP_PATH) || '';

        if (!dirPath || !fileName) {
            console.warn('[MacrosSync Companion] Автосинхронизация: не настроен путь к файлу');
            return;
        }

        // 1. Получаем текущие макросы книги
        let docData;
        try {
            docData = await getMacrosFromDocument();
        } catch (e) {
            console.error('[MacrosSync Companion] Не удалось получить макросы книги:', e);
            return;
        }

        // 2. Резервная копия (если настроен путь)
        if (backupPath) {
            try {
                await fetchJson(serverUrl, '/files/write', {
                    directoryPath: backupPath,
                    fileName: 'universal_macros_backup.json',
                    overwrite: true,
                    content: { macrosArray: docData.macrosArray || [] }
                });
                console.log('[MacrosSync Companion ] Резервная копия сохранена');
            } catch (e) {
                console.warn('[MacrosSync Companion] Не удалось сохранить резервную копию:', e);
            }
        }

        // 3. Обновляем универсальные макросы через сервер
        const excludedMacros = (docData.macrosArray || []).filter(
            m => m.isUniversal === true && m.isExcludedFromAutoSync === true
        );
        const excludedGuids = new Set(excludedMacros.map(m => m.guid));
        const excludedNames = new Set(excludedMacros.map(m => m.name));

        let result;
        try {
            const { ok, json } = await fetchJson(serverUrl, '/macros/sync', {
                mode: 'refresh',
                directoryPath: dirPath,
                fileName,
                macrosArray: docData.macrosArray || []
            });
            if (!ok) {
                console.warn('[MacrosSync Companion] Ошибка синхронизации:', json.message);
                return;
            }
            result = json;
        } catch (e) {
            console.error('[MacrosSync Companion] Не удалось выполнить синхронизацию:', e);
            return;
        }

        // Восстанавливаем исключённые макросы — не обновляем их при автосинхронизации
        if (excludedGuids.size > 0 && result.macrosArray) {
            result.macrosArray = result.macrosArray.map(m =>
                excludedGuids.has(m.guid)
                    ? (excludedMacros.find(e => e.guid === m.guid) || m)
                    : m
            );
            result.updated = (result.updated || []).filter(n => !excludedNames.has(n));
        }

        // 4. Записываем только если что-то изменилось
        if (!result.updated || result.updated.length === 0) {
            console.log('[MacrosSync Companion] Универсальные макросы актуальны, обновление не требуется');
            return;
        }

        try {
            docData.macrosArray = result.macrosArray;
            await setMacrosInDocument(docData);
            console.log(`[MacrosSync Companion] Синхронизация завершена. Обновлено: ${result.updated.length}`);
        } catch (e) {
            console.error('[MacrosSync Companion] Не удалось записать макросы в документ:', e);
        }
    }

    // ===== Инициализация компаньона =====

    window.Asc.plugin.init = function () {
        if (localStorage.getItem(LS_AUTO_SYNC) !== 'true') {
            console.log('[MacrosSync Companion] Автосинхронизация отключена');
            return;
        }
        console.log('[MacrosSync Companion] Запуск автосинхронизации...');
        autoSync();
    };

})(window, undefined);