(function (window) {
    'use strict';
    // Read-only checker: never calls SetMacros or any write endpoint.
    window.Asc.plugin.init = function () {
        if (localStorage.getItem('macrosSync_autoSync') !== 'true') return;
        const directoryPath = localStorage.getItem('macrosSync_dirPath');
        const fileName = localStorage.getItem('macrosSync_fileName') || 'universal_macros.json';
        const server = localStorage.getItem('macrosSync_serverUrl') || 'http://127.0.0.1:8124';
        if (!directoryPath) return;
        window.Asc.plugin.callCommand(function () { return Api.pluginMethod_GetMacros(); }, false, false, async function (raw) {
            try {
                const document = typeof raw === 'string' ? JSON.parse(raw) : raw;
                if (!document || !Array.isArray(document.macrosArray)) throw new Error('Не удалось прочитать макросы документа');
                const selectedGuids = document.macrosArray.filter(m => m.isUniversal && !m.isSeparator && !m.isExcludedFromAutoSync).map(m => m.guid);
                if (!selectedGuids.length) return;
                const response = await fetch(server.replace(/\/$/, '') + '/macros/v2/preview', {
                    method: 'POST', headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ directoryPath, fileName, action: 'pull', document, selectedGuids })
                });
                if (!response.ok) throw new Error(response.status === 404 ? 'Обновите R7LocalServer для Macros Sync v2.' : (await response.json()).message);
                const preview = await response.json();
                const changed = preview.changes.filter(c => c.kind !== 'unchanged');
                if (changed.length) window.alert('В библиотеке есть изменения макросов (' + changed.length + '). Откройте «Синхронизация макросов», чтобы просмотреть и применить их.');
            } catch (error) { console.warn('[Macros Sync Companion]', error.message); }
        });
    };
})(window);
