package ru.mrnds.r7localserver.ui.documentation

import ru.mrnds.r7localserver.ui.documentation.docs.*
import ru.mrnds.r7localserver.ui.documentation.model.DocumentationGroup

val documentationGroups = listOf(
    DocumentationGroup(
        title = "API",
        items = listOf(
            pingDoc,
            filesReadDoc,
            filesWriteDoc,
            proxyRequestDoc
        )
    ),
    DocumentationGroup(
        title = "Типы файлов",
        items = listOf(
            textFileDoc,
            jsonFileDoc,
            csvFileDoc,
            xmlFileDoc,
            excelFileDoc
        )
    )
)
