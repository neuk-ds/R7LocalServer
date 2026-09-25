package ru.mrnds.r7localserver.ui.documentation.docs

import ru.mrnds.r7localserver.ui.documentation.model.EndpointDoc

val versionedMacroDoc = EndpointDoc(
    id = "macros-v2",
    title = "Версии и слияние макросов",
    method = "POST",
    path = "/macros/v2/preview",
    description = "Просмотр трёхстороннего слияния до записи. Сохранение в библиотеку не изменяет документ; загрузка не изменяет библиотеку. Конфликты разрешаются пользователем.",
    requestExample = """
        {
          "directoryPath": "D:\\shared",
          "fileName": "universal_macros.json",
          "action": "pull",
          "document": { "macrosArray": [] },
          "selectedGuids": ["macro-guid"]
        }
    """.trimIndent(),
    notes = listOf(
        "POST /macros/v2/state: directoryPath, fileName → library, managed, externalChanges.",
        "preview: action = push, pull, delete, import, restore или restoreDocument. Возвращает token, operationId, expiresAt, changes с исходными версиями, diff и конфликтами.",
        "POST /macros/v2/apply: directoryPath, fileName, token, operationId, selectedGuids, resolutions, comment, backupDirectory. resolutions: GUID → {macro: итоговый объект или null}.",
        "Токен действует 30 минут. При повторе опубликованной операции с тем же operationId новая версия не создаётся. При 409 перечитайте данные и пересчитайте изменения.",
        "POST /macros/v2/history: directoryPath, fileName → опубликованные снимки от нового к старому. /revision дополнительно принимает revisionId.",
        "restore требует revisionId и создаёт новую версию. import регистрирует исходный файл либо внешние изменения. Эти операции применяются целиком.",
        "restoreDocument требует replacementDocument — полный объект резервной копии. Перед восстановлением создаётся новая копия текущего состояния.",
        "Для pull возвращаются document, expectedDocument и backupPath. Плагин сравнивает текущий документ с expectedDocument перед SetMacros и проверяет результат после записи.",
        "История находится рядом с JSON в каталоге <имя-файла>.history. Общая папка должна поддерживать межпроцессные блокировки и атомарную замену; проверьте её с двух компьютеров.",
        "Перед включением истории обновите сервер и оба плагина на всех компьютерах. /files/write не может изменять управляемую библиотеку.",
        "Companion проверяет отличия, по настройке автоматически обновляет изменённые макросы и позволяет применить все изменения из библиотеки вручную. После применения сохраните книгу в Р7."
    )
)
