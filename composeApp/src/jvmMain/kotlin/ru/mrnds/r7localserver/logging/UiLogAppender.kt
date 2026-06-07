package ru.mrnds.r7localserver.logging

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class UiLogAppender : AppenderBase<ILoggingEvent>() {
    private val formatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

    override fun append(eventObject: ILoggingEvent) {
        val time = formatter.format(
            Instant.ofEpochMilli(eventObject.timeStamp)
        )
        val level = when (eventObject.level.levelStr) {
            "TRACE" -> UiLogLevel.TRACE
            "DEBUG" -> UiLogLevel.DEBUG
            "INFO" -> UiLogLevel.INFO
            "WARN" -> UiLogLevel.WARN
            "ERROR" -> UiLogLevel.ERROR
            else -> UiLogLevel.INFO
        }
        UiLogStore.add(
            UiLogEntry(
                level = level,
                text = "$time ${eventObject.loggerName} [${eventObject.level}] - ${eventObject.formattedMessage}"
            )
        )
    }
}