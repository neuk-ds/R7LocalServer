package ru.mrnds.r7localserver.files.model

enum class FileType(val extension: String) {
    TXT(".txt"),
    JSON(".json"),
    CSV(".csv"),
    XML(".xml"),
    XLS(".xls"),
    XLSX(".xlsx");

    companion object {
        fun fromFileName(fileName: String): FileType {
            return entries.firstOrNull {
                fileName.endsWith(
                    suffix = it.extension,
                    ignoreCase = true
                )
            } ?: throw IllegalArgumentException(
                "Unsupported file extension"
            )
        }
    }
}