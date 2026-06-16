package ru.mrnds.r7localserver.ui.documentation.docs

import ru.mrnds.r7localserver.ui.documentation.model.EndpointDoc

val macroSyncDoc = EndpointDoc(
    id = "macros-sync",
    title = "Синхронизация макросов",
    method = "POST",
    path = "/macros/sync",
    description = "Синхронизирует универсальные макросы между книгами Р7 Офис через общий JSON-файл на диске. Режим merge обновляет macrosArray книги из файла; режим push записывает универсальные макросы книги в файл.",
    requestExample = """
        {
          "directoryPath": "D:\\shared",
          "fileName": "universal_macros.json",
          "mode": "merge",
          "macrosArray": [
            {
              "name": "Config",
              "guid": "c37cbf95bc894224a395ace27bfb9cfc",
              "value": "...код макроса...",
              "autostart": false,
              "isUniversal": true
            }
          ]
        }
    """.trimIndent(),
    responseExample = """
        {
          "status": "ok",
          "macrosArray": [ ... ],
          "updated": [ "Config" ],
          "added": [ "Utils" ],
          "totalUniversal": null
        }
    """.trimIndent(),
    notes = listOf(
        "directoryPath — обязательное поле. Путь к папке с файлом универсальных макросов.",
        "fileName — обязательное поле. Имя JSON-файла с универсальными макросами.",
        "mode — необязательное поле. 'merge' (по умолчанию) или 'push'.",
        "macrosArray — обязательное поле. Полный массив макросов книги в формате macros.json Р7 Офис. Неизвестные поля объектов сохраняются как есть.",
        "Разделитель определяется по полю isSeparator=true или guid='00000000-separator-0000-000000000000'.",
        "Режим merge: читает файл (404 если не существует — сначала сделайте push), обновляет universals из файла, добавляет разделитель, сохраняет специфичные макросы книги. Возвращает итоговый macrosArray.",
        "Режим push: берёт макросы с isUniversal=true из macrosArray, обновляет/добавляет их в файл, не удаляет чужие универсальные макросы. Возвращает totalUniversal — общее количество в файле.",
        "updated — имена макросов, чей guid совпал и был обновлён.",
        "added — имена макросов, которых не было и которые были добавлены.",
        "Порядок универсальных в merge-ответе соответствует порядку в файле. Порядок специфичных макросов книги сохраняется."
    )
)
