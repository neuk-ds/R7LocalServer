package ru.mrnds.r7localserver.ui.documentation.docs

import ru.mrnds.r7localserver.ui.documentation.model.EndpointDoc

val textFileDoc = EndpointDoc(
    id = "file-txt",
    title = "TXT",
    description = "Текстовый файл передается в content как обычная JSON-строка.",
    requestExample = """
        {
          "directoryPath": "D:\\test",
          "fileName": "note.txt",
          "overwrite": true,
          "fileType": "TXT",
          "lastModifiedAt": "2026-05-25T10:15:30Z",
          "content": "Hello world"
        }
    """.trimIndent(),
    responseExample = """
        {
          "directoryPath": "D:\\test",
          "fileName": "note.txt",
          "fileType": "TXT",
          "lastModifiedAt": "2026-05-25T10:15:30Z",
          "content": "Hello world"
        }
    """.trimIndent(),
    notes = listOf(
        "directoryPath - обязательное поле. Полный путь к дирректории.",
        "fileName - обязательное поле. Должно иметь расширение .txt.",
        "overwrite - необязательное поле для /files/write; значение по умолчанию false.",
        "fileType - необязательное поле для /files/write. Игнорируется сервером.",
        "lastModifiedAt - необязательное поле для /files/write. Игнорируется сервером.",
        "content - обязательное поле для /files/write. Для TXT должно быть строкой."
    )
)
