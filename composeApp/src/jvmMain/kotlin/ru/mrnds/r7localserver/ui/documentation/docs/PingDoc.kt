package ru.mrnds.r7localserver.ui.documentation.docs

import ru.mrnds.r7localserver.ui.documentation.model.EndpointDoc

val pingDoc = EndpointDoc(
    id = "ping",
    title = "Проверка сервера",
    method = "GET",
    path = "/ping",
    description = "Проверяет, что локальный сервер запущен и отвечает на запросы.",
    responseExample = """
        {
          "status": "ok"
        }
    """.trimIndent(),
    notes = listOf(
        "Этот запрос удобно использовать для проверки доступности сервера.",
        "Тело запроса не требуется."
    )
)
