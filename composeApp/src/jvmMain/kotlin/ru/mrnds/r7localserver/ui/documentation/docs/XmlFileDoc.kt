package ru.mrnds.r7localserver.ui.documentation.docs

import ru.mrnds.r7localserver.ui.documentation.model.EndpointDoc

val xmlFileDoc = EndpointDoc(
    id = "file-xml",
    title = "XML",
    description = "XML-файл передается в content как дерево узлов. Корневой узел находится в поле root.",
    requestExample = """
        {
          "directoryPath": "D:\\test",
          "fileName": "document.xml",
          "overwrite": true,
          "fileType": "XML",
          "lastModifiedAt": "2026-05-25T10:15:30Z",
          "content": {
            "root": {
              "name": "document",
              "attributes": {
                "version": "1"
              },
              "text": null,
              "children": [
                {
                  "name": "title",
                  "attributes": {},
                  "text": "Example",
                  "children": []
                }
              ]
            },
            "lineSeparator": "\n",
            "indent": "\t"
          }
        }
    """.trimIndent(),
    responseExample = """
        {
          "directoryPath": "D:\\test",
          "fileName": "document.xml",
          "fileType": "XML",
          "lastModifiedAt": "2026-05-25T10:15:30Z",
          "content": {
            "root": {
              "name": "document",
              "attributes": {
                "version": "1"
              },
              "text": null,
              "children": [
                {
                  "name": "title",
                  "attributes": {},
                  "text": "Example",
                  "children": []
                }
              ]
            },
            "lineSeparator": "\n",
            "indent": "\t"
          }
        }
    """.trimIndent(),
    notes = listOf(
        "directoryPath - обязательное поле. Полный путь к дирректории.",
        "fileName - обязательное поле. Должно иметь расширение .xml.",
        "overwrite - необязательное поле для /files/write; значение по умолчанию false.",
        "fileType - необязательное поле для /files/write. Игнорируется сервером.",
        "lastModifiedAt - необязательное поле для /files/write. Игнорируется сервером.",
        "content - обязательное поле для /files/write. Для XML должно быть объектом XmlFileOptions.",
        "content.root - обязательное поле. Корневой XML-узел.",
        "content.lineSeparator - необязательное поле. Разделитель строк; значение по умолчанию \\n.",
        "content.indent - необязательное поле. Отступ при записи; значение по умолчанию \\t.",
        "XmlNode.name - обязательное поле. Имя XML-узла.",
        "XmlNode.attributes - необязательное поле. Атрибуты узла; значение по умолчанию пустой объект.",
        "XmlNode.text - необязательное поле. Текст узла; значение по умолчанию null.",
        "XmlNode.children - необязательное поле. Дочерние узлы; значение по умолчанию пустой список."
    )
)
