package ru.mrnds.r7localserver.ui.documentation.docs

import ru.mrnds.r7localserver.ui.documentation.model.EndpointDoc

val csvFileDoc = EndpointDoc(
    id = "file-csv",
    title = "CSV",
    description = "CSV-файл передается в content как объект с разделителем, переводом строки и таблицей.",
    requestExample = """
        {
          "directoryPath": "D:\\test",
          "fileName": "table.csv",
          "overwrite": true,
          "fileType": "CSV",
          "lastModifiedAt": "2026-05-25T10:15:30Z",
          "content": {
            "delimiter": ";",
            "lineSeparator": "\n",
            "table": [
              ["name", "comment"],
              ["Ivan", "hello; world"],
              ["Anna", "she said \"hi\""]
            ]
          }
        }
    """.trimIndent(),
    responseExample = """
        {
          "directoryPath": "D:\\test",
          "fileName": "table.csv",
          "fileType": "CSV",
          "lastModifiedAt": "2026-05-25T10:15:30Z",
          "content": {
            "delimiter": ";",
            "lineSeparator": "\n",
            "table": [
              ["name", "comment"],
              ["Ivan", "hello; world"],
              ["Anna", "she said \"hi\""]
            ]
          }
        }
    """.trimIndent(),
    notes = listOf(
        "directoryPath - обязательное поле. Полный путь к дирректории.",
        "fileName - обязательное поле. Должно иметь расширение .csv.",
        "overwrite - необязательное поле для /files/write; значение по умолчанию false.",
        "fileType - необязательное поле для /files/write. Игнорируется сервером.",
        "lastModifiedAt - необязательное поле для /files/write. Игнорируется сервером.",
        "content - обязательное поле для /files/write.",
        "content.delimiter - необязательное поле. Разделитель колонок; значение по умолчанию ;.",
        "content.lineSeparator - необязательное поле. Разделитель строк; значение по умолчанию \\n.",
        "content.table - обязательное поле. Таблица CSV как список строк, где каждая строка является списком ячеек."
    )
)
