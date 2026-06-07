package ru.mrnds.r7localserver.ui.documentation.docs

import ru.mrnds.r7localserver.ui.documentation.model.EndpointDoc

val filesReadDoc = EndpointDoc(
    id = "files-read",
    title = "Чтение файла",
    method = "POST",
    path = "/files/read",
    description = "Читает файл и возвращает его содержимое в поле content. Формат content зависит от типа файла.",
    requestExample = """
        {
          "directoryPath": "D:\\test",
          "fileName": "test.xlsx",
          "readMode": "DEFAULT"
        }
    """.trimIndent(),
    responseExample = """
        {
          "directoryPath": "D:\\test",
          "fileName": "test.xlsx",
          "fileType": "XLSX",
          "lastModifiedAt": "2026-05-25T10:15:30Z",
          "content": {
            "sheets": []
          }
        }
    """.trimIndent(),
    notes = listOf(
        "directoryPath - обязательное поле. Путь к папке, где находится файл.",
        "fileName - обязательное поле. Имя файла с расширением; по расширению определяется тип файла.",
        "readMode - необязательное поле. Режим чтения; значение по умолчанию DEFAULT.",
        "readMode DEFAULT - возвращает обычное содержимое файла. Для Excel возвращает таблицу значений.",
        "readMode RAW - используется для Excel и возвращает ячейки с адресами, типами, формулами и valueState."
    )
)
