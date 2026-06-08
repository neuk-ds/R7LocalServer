package ru.mrnds.r7localserver.tray

import org.slf4j.LoggerFactory
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities

class AppTray(
    private val onOpenMainWindow: () -> Unit,
    private val onOpenTrayPanel: () -> Unit,
) {
    private val logger = LoggerFactory.getLogger(AppTray::class.java)
    private var trayIcon: TrayIcon? = null

    fun install() {
        logger.info("Installing tray. SystemTray supported={}", SystemTray.isSupported())
        if (!SystemTray.isSupported()) {
            return
        }

        val imageUrl = javaClass.getResource("/tray-icon.png") ?: return
        val image = Toolkit.getDefaultToolkit().createImage(imageUrl)

        trayIcon = TrayIcon(image, "R7 Local Server").apply {
            isImageAutoSize = true
            addMouseListener(
                object : MouseAdapter() {
                    override fun mousePressed(e: MouseEvent) {
                        logger.info(
                            "Tray mouse pressed: button={}, clickCount={}, popupTrigger={}",
                            e.button,
                            e.clickCount,
                            e.isPopupTrigger
                        )
                    }

                    override fun mouseReleased(e: MouseEvent) {
                        logger.info(
                            "Tray mouse released: button={}, clickCount={}, popupTrigger={}",
                            e.button,
                            e.clickCount,
                            e.isPopupTrigger
                        )

                        if (e.isPopupTrigger || SwingUtilities.isRightMouseButton(e)) {
                            onOpenTrayPanel()
                        }
                    }


                    override fun mouseClicked(e: MouseEvent) {
                        logger.info(
                            "Tray mouse clicked: button={}, clickCount={}",
                            e.button,
                            e.clickCount
                        )
                        when {
                            SwingUtilities.isRightMouseButton(e) -> {
                                onOpenTrayPanel()
                            }

                            SwingUtilities.isLeftMouseButton(e) && e.clickCount >= 2 -> {
                                onOpenMainWindow()
                            }
                        }

                    }

                }

            )
        }
        SystemTray.getSystemTray().add(trayIcon)
        logger.info("Tray icon installed")
    }

    fun updateTooltip(text: String) {
        trayIcon?.toolTip = text
    }

    fun dispose() {
        trayIcon?.let {
            SystemTray.getSystemTray().remove(it)
        }
        trayIcon = null
    }
}