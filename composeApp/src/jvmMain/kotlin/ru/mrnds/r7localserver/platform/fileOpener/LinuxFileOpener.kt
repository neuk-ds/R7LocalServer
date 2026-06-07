package ru.mrnds.r7localserver.platform.fileOpener

import java.io.File

class LinuxFileOpener : FileOpener {
    override fun open(file: File) {
        val process = ProcessBuilder("xdg-open", file.absolutePath)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw IllegalStateException(output.ifBlank { "Failed to open file with xdg-open: $output" })
        }
    }
}