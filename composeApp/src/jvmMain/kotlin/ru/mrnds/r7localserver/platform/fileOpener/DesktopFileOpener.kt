package ru.mrnds.r7localserver.platform.fileOpener

import java.awt.Desktop
import java.io.File

class DesktopFileOpener : FileOpener {
    override fun open(file: File) {
        if (!Desktop.isDesktopSupported()) {
            throw IllegalStateException("Desktop API is not supported")
        }
        val desktop = Desktop.getDesktop()
        if (!desktop.isSupported(Desktop.Action.OPEN)) {
            throw IllegalStateException("Desktop open action is not supported")
        }
        desktop.open(file)
    }
}