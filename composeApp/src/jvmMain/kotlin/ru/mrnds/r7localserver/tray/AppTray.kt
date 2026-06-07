package ru.mrnds.r7localserver.tray

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
    private var trayIcon: TrayIcon? = null

    fun install() {
        if (!SystemTray.isSupported()) {
            return
        }

        val imageUrl = javaClass.getResource("/tray-icon.png") ?: return
        val image = Toolkit.getDefaultToolkit().createImage(imageUrl)

        trayIcon = TrayIcon(image, "R7 Local Server").apply {
            isImageAutoSize = true
            addMouseListener(
                object : MouseAdapter() {
                    override fun mouseClicked(e: MouseEvent) {
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