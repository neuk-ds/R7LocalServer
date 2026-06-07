package ru.mrnds.r7localserver.settings.autoStart

interface AutoStart {
    fun setEnabled(enabled: Boolean)
    fun isEnabled(): Boolean
}