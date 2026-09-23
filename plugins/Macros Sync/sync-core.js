(function (root) {
    'use strict';
    function canonical(value) {
        if (Array.isArray(value)) return '[' + value.map(canonical).join(',') + ']';
        if (value && typeof value === 'object') return '{' + Object.keys(value).sort().map(k => JSON.stringify(k) + ':' + canonical(value[k])).join(',') + '}';
        return JSON.stringify(value);
    }
    function call(plugin, command) {
        return new Promise((resolve, reject) => {
            const timer = setTimeout(() => reject(new Error('Редактор не ответил. Перечитайте документ перед повторной попыткой.')), 15000);
            try {
                plugin.callCommand(command, false, false, result => {
                    clearTimeout(timer);
                    try {
                        const response = typeof result === 'string' ? JSON.parse(result) : result;
                        if (!response || response.ok !== true) throw new Error(response && response.error || 'Не удалось проверить результат в редакторе');
                        resolve(response);
                    } catch (error) { reject(error); }
                });
            } catch (error) { clearTimeout(timer); reject(error); }
        });
    }
    function read(plugin) {
        return call(plugin, function () {
            try {
                const raw = Api.pluginMethod_GetMacros();
                const document = typeof raw === 'string' ? (raw.trim() === '' ? { macrosArray: [] } : JSON.parse(raw)) : raw;
                if (!document || !Array.isArray(document.macrosArray)) throw new Error('Некорректный macrosArray');
                return JSON.stringify({ ok: true, document });
            } catch (error) { return JSON.stringify({ ok: false, error: String(error) }); }
        }).then(result => result.document);
    }
    function apply(plugin, scope, expected, next) {
        scope.r7Expected = expected; scope.r7Next = next;
        return call(plugin, function () {
            function canonical(value) {
                if (Array.isArray(value)) return '[' + value.map(canonical).join(',') + ']';
                if (value && typeof value === 'object') return '{' + Object.keys(value).sort().map(k => JSON.stringify(k) + ':' + canonical(value[k])).join(',') + '}';
                return JSON.stringify(value);
            }
            function withoutSync(document) {
                const copy = JSON.parse(JSON.stringify(document));
                copy.macrosArray.forEach(m => delete m._r7Sync);
                return copy;
            }
            // The command runs in the editor context, so it cannot call outer helpers.
            function readDocument() {
                const raw = Api.pluginMethod_GetMacros();
                const document = typeof raw === 'string' ? (raw.trim() === '' ? { macrosArray: [] } : JSON.parse(raw)) : raw;
                if (!document || !Array.isArray(document.macrosArray)) throw new Error('Некорректный macrosArray');
                return document;
            }
            try {
                const current = readDocument();
                if (canonical(current) !== canonical(Asc.scope.r7Expected)) throw new Error('Документ изменился после просмотра. Обновите сравнение.');
                Api.pluginMethod_SetMacros(JSON.stringify(Asc.scope.r7Next));
                const actual = readDocument();
                if (canonical(actual) === canonical(Asc.scope.r7Next)) return JSON.stringify({ ok: true, metadataLost: false });
                const onlyMissingMetadata = actual.macrosArray.every(m => {
                    const expected = Asc.scope.r7Next.macrosArray.find(e => e.guid === m.guid);
                    return m._r7Sync == null || (expected && canonical(m._r7Sync) === canonical(expected._r7Sync));
                });
                if (onlyMissingMetadata && canonical(withoutSync(actual)) === canonical(withoutSync(Asc.scope.r7Next))) return JSON.stringify({ ok: true, metadataLost: true });
                throw new Error('Результат записи не совпал с ожидаемым. Доступна резервная копия.');
            } catch (error) { return JSON.stringify({ ok: false, error: String(error) }); }
        });
    }
    const api = { canonical, read, apply };
    if (typeof module !== 'undefined' && module.exports) module.exports = api;
    else root.R7SyncCore = api;
})(typeof window === 'undefined' ? globalThis : window);
