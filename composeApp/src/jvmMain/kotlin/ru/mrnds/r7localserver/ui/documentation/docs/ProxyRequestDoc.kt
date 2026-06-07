package ru.mrnds.r7localserver.ui.documentation.docs

import ru.mrnds.r7localserver.ui.documentation.model.EndpointDoc

val proxyRequestDoc = EndpointDoc(
    id = "proxy-request",
    title = "Проксирование запроса",
    method = "POST",
    path = "/proxy/request",
    description = "Отправляет HTTP-запрос на сторонний сервер и возвращает статус, заголовки и тело ответа.",
    requestExample = """
        {
          "url": "https://example.com/api/data",
          "method": "GET",
          "headers": {
            "Accept": "application/json"
          },
          "body": null,
          "username": null,
          "password": null,
          "ignoreSslErrors": false
        }
    """.trimIndent(),
    responseExample = """
        {
          "status": 200,
          "headers": {
            "content-type": "application/json"
          },
          "body": "{\"result\":\"ok\"}"
        }
    """.trimIndent(),
    notes = listOf(
        "url - обязательное поле. Полный URL стороннего сервера.",
        "method - необязательное поле. HTTP-метод; значение по умолчанию GET.",
        "headers - необязательное поле. Заголовки для проксируемого запроса; значение по умолчанию пустой объект.",
        "body - необязательное поле. Тело запроса строкой; значение по умолчанию null.",
        "username - необязательное поле. Логин для Basic-авторизации; значение по умолчанию null.",
        "password - необязательное поле. Пароль для Basic-авторизации; значение по умолчанию null.",
        "ignoreSslErrors - необязательное поле. Если true, прокси не проверяет HTTPS-сертификат целевого сервера; значение по умолчанию false. Использовать только для тестов и внутренних серверов.",
        "Заголовки host, content-length и connection не проксируются."
    )
)
