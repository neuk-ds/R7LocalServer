package ru.mrnds.r7localserver.settings.autoStart

import org.slf4j.LoggerFactory
import ru.mrnds.r7localserver.platform.AppDirectories
import java.io.File

class LinuxAutoStart : AutoStart {
    private val logger = LoggerFactory.getLogger(LinuxAutoStart::class.java)
    private val desktopFile: File
        get() = File(AppDirectories.autostartDirectory, "R7LocalServer.desktop")

    override fun setEnabled(enabled: Boolean) {
        logger.info("Changing Linux autostart: {}", enabled)

        if (enabled) {
            enable()
        } else {
            disable()
        }
    }

    override fun isEnabled(): Boolean {
        val enabled = desktopFile.exists()
        logger.debug("Linux autostart is enabled: {}", enabled)
        return enabled
    }

    private fun enable() {
        val exePath = currentExecutablePath()
        val content = buildDesktopFile(exePath)

        desktopFile.parentFile.mkdirs()
        desktopFile.writeText(content)

        logger.info("Linux autostart enabled: {}", desktopFile.absolutePath)
    }

    private fun disable() {
        if (desktopFile.exists()) {
            desktopFile.delete()
        }

        logger.info("Linux autostart disabled")
    }

    private fun currentExecutablePath(): String {
        return ProcessHandle.current()
            .info()
            .command()
            .orElseThrow {
                IllegalStateException("Cannot determine application executable path")
            }
    }
    private fun buildDesktopFile(exePath: String): String {
        return """
            [Desktop Entry]
            Type=Application
            Name=R7 Local Server
            Exec=${quoteExecPath(exePath)}
            Terminal=false
            X-GNOME-Autostart-enabled=true
        """.trimIndent()
    }

    private fun quoteExecPath(path: String): String {
        val escaped = path
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("$", "\\$")
            .replace("`", "\\`")

        return "\"$escaped\""
    }
}