package ru.mrnds.r7localserver.platform.fileOpener

import java.io.File

interface FileOpener {
    fun open(file: File)
}