package ru.mrnds.r7localserver.ui.documentation.docs

import ru.mrnds.r7localserver.ui.documentation.model.EndpointDoc

val excelExportDoc = EndpointDoc(
    id = "export-excel-sheets",
    title = "Экспорт листов Excel",
    method = "POST",
    path = "/export/excel-sheets",
    description = "Создаёт новый XLSX-файл на основе рабочего файла и оставляет в нём выбранные листы. Значения, формулы, стили, ширины колонок, высоты строк, объединения и оформление сохраняются за счёт копирования исходного файла.",
    requestExample = """
        {
          "sourceDirectoryPath": "D:\\work",
          "sourceFileName": "work.xlsx",
          "targetDirectoryPath": "D:\\result",
          "targetFileName": "export.xlsx",
          "sheetNames": ["Лист1", "Отчёт"],
          "overwrite": false
        }
    """.trimIndent(),
    responseExample = """
        {
          "targetDirectoryPath": "D:\\result",
          "targetFileName": "export.xlsx",
          "exportedSheetNames": ["Лист1", "Отчёт"],
          "replacedFormulaCount": 3
        }
    """.trimIndent(),
    notes = listOf(
        "sourceDirectoryPath - обязательное поле. Путь к папке, где находится исходный рабочий файл.",
        "sourceFileName - обязательное поле. Имя исходного файла. Сейчас поддерживается только .xlsx.",
        "targetDirectoryPath - обязательное поле. Путь к папке, куда будет создан новый файл.",
        "targetFileName - обязательное поле. Имя создаваемого файла. Сейчас поддерживается только .xlsx.",
        "sheetNames - необязательное поле. Список имён листов для экспорта; значение по умолчанию пустой список. Если список пустой, экспортируется весь файл.",
        "overwrite - необязательное поле. Если true, существующий targetFileName будет перезаписан; значение по умолчанию false.",
        "Если targetFileName уже существует и overwrite=false, сервер вернёт ошибку.",
        "Если в sheetNames указан лист, которого нет в исходном файле, сервер вернёт ошибку.",
        "Формулы, которые ссылаются только на экспортируемые листы, остаются формулами.",
        "Формулы с внешними ссылками или ссылками на неэкспортируемые листы заменяются на кэшированное значение. Если значение получить нельзя, формула остаётся без изменений.",
        "replacedFormulaCount показывает, сколько формул было заменено на значения.",
        "Заморозка областей, фильтры, параметры печати, картинки и диаграммы специально не обрабатываются. Если они сохранятся после копирования файла, сервер их не удаляет."
    )
)
