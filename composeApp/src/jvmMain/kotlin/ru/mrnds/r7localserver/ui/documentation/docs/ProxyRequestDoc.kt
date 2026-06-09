package ru.mrnds.r7localserver.ui.documentation.docs

import ru.mrnds.r7localserver.ui.documentation.model.EndpointDoc

val proxyRequestDoc = EndpointDoc(
    id = "proxy-request",
    title = "Проксирование запроса",
    method = "POST",
    path = "/proxy/request",
    description = "Отправляет HTTP-запрос на сторонний сервер и возвращает статус, заголовки и тело ответа. CORS для клиента добавляет локальный сервер.",
    requestExample = """
        {
          "url": "https://example.com/api/data",
          "method": "GET",
          "headers": {
            "Accept": "application/json"
          },
          "body": null,
          "authType": "NONE",
          "username": null,
          "password": null,
          "domain": null,
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
        "authType - необязательное поле. Тип авторизации; значение по умолчанию NONE. Возможные значения: NONE, BASIC, NTLM, NEGOTIATE.",
        "username - необязательное поле. Логин для BASIC или NTLM. Для BASIC и NTLM становится обязательным.",
        "password - необязательное поле. Пароль для BASIC или NTLM. Для BASIC и NTLM становится обязательным.",
        "domain - необязательное поле. Домен Windows/Active Directory для NTLM. Для NTLM становится обязательным.",
        "ignoreSslErrors - необязательное поле. Если true, прокси не проверяет HTTPS-сертификат целевого сервера; значение по умолчанию false. Использовать только для тестов и внутренних серверов.",
        "authType NONE - запрос отправляется без авторизации.",
        "authType BASIC - используется Basic-авторизация через username/password.",
        "authType NTLM - используется NTLM-авторизация через username/password/domain.",
        "authType NEGOTIATE - используется Windows Negotiate/NTLM через текущую Windows-сессию. На Linux и macOS возвращается ошибка.",
        "Заголовки host, content-length и connection не проксируются.",
        "CORS-заголовки для Р7 Office добавляет локальный сервер, а не сторонний сервер."
    )
)
