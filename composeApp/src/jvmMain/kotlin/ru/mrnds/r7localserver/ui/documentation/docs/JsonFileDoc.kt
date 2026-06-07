package ru.mrnds.r7localserver.ui.documentation.docs

import ru.mrnds.r7localserver.ui.documentation.model.EndpointDoc

val jsonFileDoc = EndpointDoc(
    id = "file-json",
    title = "JSON",
    description = "JSON-файл передается в content как настоящий JSON: объект, массив, строка, число или boolean.",
    requestExample = """
        {
          "directoryPath": "D:\\test",
          "fileName": "data.json",
          "overwrite": true,
          "fileType": "JSON",
          "lastModifiedAt": "2026-05-25T10:15:30Z",
          "content": {
            "name": "Ivan",
            "age": 30
          }
        }
    """.trimIndent(),
    responseExample = """
        {
          "directoryPath": "D:\\test",
          "fileName": "data.json",
          "fileType": "JSON",
          "lastModifiedAt": "2026-05-25T10:15:30Z",
          "content": {
            "name": "Ivan",
            "age": 30
          }
        }
    """.trimIndent(),
    notes = listOf(
        "directoryPath - обязательное поле. Полный путь к дирректории.",
        "fileName - обязательное поле. Должно иметь расширение .json.",
        "overwrite - необязательное поле для /files/write; значение по умолчанию false.",
        "fileType - необязательное поле для /files/write. Игнорируется сервером.",
        "lastModifiedAt - необязательное поле для /files/write. Игнорируется сервером.",
        "content - обязательное поле для /files/write. Для JSON может быть объектом, массивом, строкой, числом, boolean или null."
    )
)
