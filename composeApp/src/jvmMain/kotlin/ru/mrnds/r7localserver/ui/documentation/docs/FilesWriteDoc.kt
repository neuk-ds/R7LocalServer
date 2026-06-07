package ru.mrnds.r7localserver.ui.documentation.docs

import ru.mrnds.r7localserver.ui.documentation.model.EndpointDoc

val filesWriteDoc = EndpointDoc(
    id = "files-write",
    title = "Запись файла",
    method = "POST",
    path = "/files/write",
    description = "Создает или перезаписывает файл. Формат поля content зависит от расширения fileName.",
    requestExample = """
        {
          "directoryPath": "D:\\test",
          "fileName": "test.txt",
          "overwrite": true,
          "fileType": "TXT",
          "lastModifiedAt": "2026-05-25T10:15:30Z",
          "content": "Hello world"
        }
    """.trimIndent(),
    responseExample = """
        {
          "success": true,
          "path": "D:\\test\\test.txt",
          "fileType": "TXT"
        }
    """.trimIndent(),
    notes = listOf(
        "directoryPath - обязательное поле. Полный путь к дирректории.",
        "fileName - обязательное поле. Имя файла с расширением; по расширению определяется writer.",
        "overwrite - необязательное поле. Разрешает перезапись существующего файла; значение по умолчанию false.",
        "fileType - необязательное поле. Можно отправлять обратно из /files/read; при записи сервер его игнорирует.",
        "lastModifiedAt - необязательное поле. Можно отправлять обратно из /files/read; при записи сервер его игнорирует.",
        "content - обязательное поле. Содержимое файла; формат зависит от расширения fileName."
    )
)
