package ru.mrnds.r7localserver.ui.documentation.docs

import ru.mrnds.r7localserver.ui.documentation.model.EndpointDoc

val excelFileDoc = EndpointDoc(
    id = "file-excel",
    title = "Excel XLS/XLSX",
    description = "Excel поддерживает три режима чтения. DEFAULT возвращает матрицу строк, TYPED возвращает матрицу JSON-значений, RAW возвращает список ячеек с адресами, типами, формулами и состоянием значения. Запись XLSX выполняется по RAW-структуре.",
    requestExample = """
        {
          "directoryPath": "D:\\test",
          "fileName": "book.xlsx",
          "readMode": "TYPED"
        }
    """.trimIndent(),
    responseExample = """
        {
          "directoryPath": "D:\\test",
          "fileName": "book.xlsx",
          "fileType": "XLSX",
          "lastModifiedAt": "2026-05-25T10:15:30Z",
          "content": {
            "sheets": [
              {
                "name": "Лист1",
                "rows": [
                  ["name", "age", "active", "createdAt"],
                  ["Ivan", 42, true, "2026-05-25 10:15:30"]
                ]
              }
            ]
          }
        }
    """.trimIndent(),
    notes = listOf(
        "directoryPath - обязательное поле. Полный путь к дирректории.",
        "fileName - обязательное поле. Для чтения поддерживаются .xls и .xlsx, для записи поддерживается .xlsx.",
        "readMode - необязательное поле для /files/read. Значения: DEFAULT, TYPED или RAW; значение по умолчанию DEFAULT.",
        "readMode DEFAULT - возвращает content.sheets[].rows как List<List<String>>. Все значения возвращаются строками, числа не теряют значимые знаки.",
        "readMode TYPED - возвращает content.sheets[].rows как матрицу JSON-значений. Числа возвращаются как JSON number, boolean как JSON boolean, строки и даты как JSON string.",
        "readMode RAW - возвращает content.sheets[].cells как плоский список непустых ячеек с адресом, индексами, типом, значением, формулой, форматом и valueState.",
        "overwrite - необязательное поле для /files/write; значение по умолчанию false.",
        "fileType - необязательное поле для /files/write. Игнорируется сервером.",
        "lastModifiedAt - необязательное поле для /files/write. Игнорируется сервером.",
        "content - обязательное поле для /files/write. Для Excel-записи должно быть объектом ExcelRawContent.",
        "При чтении DEFAULT и TYPED content содержит sheets[].rows. При чтении RAW content содержит sheets[].cells.",
        "content.sheets - обязательное поле. Список листов Excel.",
        "sheet.name - обязательное поле. Имя листа.",
        "sheet.cells - обязательное поле. Плоский список ячеек листа.",
        "cell.address - обязательное поле в RAW-ответе, при записи используется как справочная информация.",
        "cell.rowIndex - обязательное поле. Индекс строки с нуля.",
        "cell.columnIndex - обязательное поле. Индекс колонки с нуля.",
        "cell.type - обязательное поле. Тип ячейки: STRING, NUMBER, DATETIME, BOOLEAN, FORMULA или BLANK.",
        "cell.value - обязательное поле. Значение ячейки строкой.",
        "cell.formula - необязательное поле. Формула для ячеек типа FORMULA; значение по умолчанию null.",
        "cell.format - необязательное поле. Формат ячейки; значение по умолчанию null.",
        "cell.valueState - обязательное поле в RAW-ответе, при записи используется как справочная информация."
    )
)
