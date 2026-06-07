package ru.mrnds.r7localserver.settings.autoStart

class NoOpAutoStart : AutoStart {
    override fun setEnabled(enabled: Boolean) = Unit

    override fun isEnabled(): Boolean = false
}