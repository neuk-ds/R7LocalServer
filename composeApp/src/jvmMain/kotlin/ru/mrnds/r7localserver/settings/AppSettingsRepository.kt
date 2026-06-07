package ru.mrnds.r7localserver.settings

import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import ru.mrnds.r7localserver.platform.AppDirectories
import java.io.File

class AppSettingsRepository {
    private val logger = LoggerFactory.getLogger(AppSettingsRepository::class.java)
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    private val settingsFile: File = AppDirectories.settingsFile
    fun load(): AppSettings {
        if (!settingsFile.exists()) {
            logger.info("Settings file not found, using defaults: {}", settingsFile.absolutePath)
            return AppSettings()
        }
        return try {
            val settings = json.decodeFromString<AppSettings>(settingsFile.readText())
            logger.info("Settings loaded from {}", settingsFile.absolutePath)
            settings
        } catch (e: Exception) {
            logger.error("Failed to load settings from {}, using defaults", settingsFile.absolutePath, e)
            AppSettings()
        }
    }

    fun save(settings: AppSettings) {
        settingsFile.parentFile.mkdirs()
        settingsFile.writeText(json.encodeToString(settings))
        logger.info("Settings saved to {}", settingsFile.absolutePath)
    }
}