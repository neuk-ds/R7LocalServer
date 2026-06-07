package ru.mrnds.r7localserver.settings.autoStart

import org.slf4j.LoggerFactory
import java.io.File

class WindowsAutoStart : AutoStart {
    private val logger = LoggerFactory.getLogger(WindowsAutoStart::class.java)
    private val appName = "R7LocalServer"
    private val runKey = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private val regExe = "${System.getenv("SystemRoot")}\\System32\\reg.exe"
    override fun setEnabled(enabled: Boolean) {
        logger.info("Changing Windows autostart: {}", enabled)
        if (enabled) {
            enable()
        } else {
            disable()
        }
    }

    override fun isEnabled(): Boolean {
        return try {
            runCommand(
                regExe,
                "query",
                runKey,
                "/v",
                appName
            )
            logger.debug("Windows autostart is enabled")
            true
        } catch (_: Exception) {
            logger.debug("Windows autostart is disabled")
            false
        }
    }

    private fun enable() {
        val exePath = currentExecutablePath()
        val command = "\"$exePath\""


        runCommand(
            regExe,
            "add",
            runKey,
            "/v",
            appName,
            "/t",
            "REG_SZ",
            "/d",
            command,
            "/f"
        )
        logger.info("Windows autostart enabled for {}", exePath)
    }

    private fun disable() {
        runCommand(
            regExe,
            "delete",
            runKey,
            "/v",
            appName,
            "/f",
            ignoreErrors = true
        )
        logger.info("Windows autostart disabled")
    }

    private fun currentExecutablePath(): String {
        val path = ProcessHandle.current()
            .info()
            .command()
            .orElseThrow {
                IllegalStateException("Cannot determine application executable path")
            }

        return File(path).absolutePath
    }

    private fun runCommand(vararg command: String, ignoreErrors: Boolean = false) {
        val process = ProcessBuilder(*command)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0 && !ignoreErrors) {
            throw IllegalStateException(output.ifBlank { "Windows registry command failed" })
        }
    }
}