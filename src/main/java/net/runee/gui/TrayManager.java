package net.runee.gui;

import net.runee.DiscordAudioStreamBot;
import net.runee.misc.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.ImageIcon;
import java.awt.AWTException;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;

/**
 * Cross-platform "hide to tray" support. Adds the application icon to the OS notification area
 * (Windows tray / Linux app indicator / macOS menu bar) when {@link SystemTray} is available, and
 * lets the window be hidden there instead of closing. Degrades gracefully where the tray is not
 * supported (the window then behaves normally and closing quits the app).
 */
public class TrayManager {
    private static final Logger logger = LoggerFactory.getLogger(TrayManager.class);
    private static final String ICON = "Logo.svg";

    private final MainFrame frame;
    private SystemTray tray;
    private TrayIcon trayIcon;
    private boolean hintShown;

    public TrayManager(MainFrame frame) {
        this.frame = frame;
    }

    /** Installs the tray icon. No-op (returns false) if the platform has no system tray. */
    public boolean install() {
        if (!SystemTray.isSupported()) {
            logger.info("System tray is not supported on this platform; hide-to-tray disabled");
            return false;
        }
        tray = SystemTray.getSystemTray();

        Dimension size = tray.getTrayIconSize();
        int px = Math.max(16, Math.min(size.width, size.height));
        ImageIcon icon = Utils.getSvgIcon(ICON, px, true);
        Image image = icon.getImage();

        PopupMenu popup = new PopupMenu();
        MenuItem showItem = new MenuItem("Show");
        showItem.addActionListener(e -> showWindow());
        MenuItem quitItem = new MenuItem("Quit");
        quitItem.addActionListener(e -> frame.exitApplication());
        popup.add(showItem);
        popup.addSeparator();
        popup.add(quitItem);

        trayIcon = new TrayIcon(image, DiscordAudioStreamBot.NAME, popup);
        trayIcon.setImageAutoSize(true);
        // left double-click (or platform default click) restores the window
        trayIcon.addActionListener(e -> showWindow());

        try {
            tray.add(trayIcon);
        } catch (AWTException ex) {
            logger.warn("Failed to install tray icon", ex);
            trayIcon = null;
            tray = null;
            return false;
        }
        return true;
    }

    public boolean isActive() {
        return trayIcon != null;
    }

    /** Hides the window to the tray (leaves the taskbar/dock entry, keeps the bot running). */
    public void hideToTray() {
        if (trayIcon == null) {
            return;
        }
        frame.setVisible(false);
        if (!hintShown) {
            hintShown = true;
            try {
                trayIcon.displayMessage(DiscordAudioStreamBot.NAME,
                        "Still running in the background. Double-click the tray icon to reopen, or use Quit to exit.",
                        TrayIcon.MessageType.INFO);
            } catch (RuntimeException ignore) {
                // some platforms don't support balloon messages
            }
        }
    }

    /** Restores and focuses the window. */
    public void showWindow() {
        frame.setVisible(true);
        frame.setExtendedState(frame.getExtendedState() & ~Frame.ICONIFIED);
        frame.toFront();
        frame.requestFocus();
    }

    public void setTooltip(String tooltip) {
        if (trayIcon != null && tooltip != null) {
            trayIcon.setToolTip(tooltip);
        }
    }

    /** Removes the tray icon (called on real shutdown). */
    public void remove() {
        if (tray != null && trayIcon != null) {
            tray.remove(trayIcon);
        }
        trayIcon = null;
        tray = null;
    }
}
