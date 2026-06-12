package ru.mrnds.r7localserver.ui.documentation.docs

import ru.mrnds.r7localserver.ui.documentation.model.EndpointDoc

val excelExportDoc = EndpointDoc(
    id = "export-excel-sheets",
    title = "Экспорт листов Excel",
    method = "POST",
    path = "/export/excel-sheets",
    description = "Создаёт чистый XLSX-файл из переданных данных листов и копирует в него стили (заливку, границы, шрифты, форматы значений, ширины колонок, высоты строк, объединения ячеек) из исходного файла.",
    requestExample = """
        {
          "sourceDirectoryPath": "D:\\work",
          "sourceFileName": "work.xlsx",
          "targetDirectoryPath": "D:\\result",
          "targetFileName": "export.xlsx",
          "overwrite": false,
          "sheets": [
            {
              "name": "Лист1",
              "cells": [
                {
                  "address": "A1",
                  "rowIndex": 0,
                  "columnIndex": 0,
                  "type": "STRING",
                  "value": "Заголовок",
                  "valueState": "VALUE"
                }
              ]
            }
          ]
        }
    """.trimIndent(),
    responseExample = """
        {
          "targetDirectoryPath": "D:\\result",
          "targetFileName": "export.xlsx",
          "exportedSheetNames": ["Лист1"]
        }
    """.trimIndent(),
    notes = listOf(
        "sourceDirectoryPath — обязательное поле. Путь к папке с исходным файлом, из которого копируются стили.",
        "sourceFileName — обязательное поле. Имя исходного файла. Поддерживается только .xlsx.",
        "targetDirectoryPath — обязательное поле. Путь к папке, куда будет создан новый файл.",
        "targetFileName — обязательное поле. Имя создаваемого файла. Поддерживается только .xlsx.",
        "overwrite — необязательное поле. Если true, существующий targetFileName будет перезаписан; по умолчанию false.",
        "sheets — обязательное поле. Список листов с данными ячеек в формате ExcelRawContent. Формулы следует заменять значениями на стороне клиента до отправки запроса.",
        "Стили копируются по имени листа: имя листа в sheets должно совпадать с именем листа в sourceFileName.",
        "Если лист из sheets не найден в sourceFileName, его стили не копируются, данные записываются без стилей.",
        "Созданный файл не содержит макросов, внешних ссылок и прочих артефактов исходного файла."
    )
)