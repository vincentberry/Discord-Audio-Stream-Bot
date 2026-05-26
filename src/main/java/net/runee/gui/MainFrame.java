package net.runee.gui;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.FileAppender;
import jouvieje.bass.BassInit;
import net.dv8tion.jda.api.JDA;
import net.runee.DiscordAudioStreamBot;
import net.runee.gui.components.MaintenancePanel;
import net.runee.gui.components.HomePanel;
import net.runee.gui.components.SettingsPanel;
import net.runee.misc.Utils;
import net.runee.misc.gui.BorderPanel;
import net.runee.model.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URISyntaxException;
import java.net.URL;
import java.io.File;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class MainFrame extends JFrame implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(MainFrame.class);
    private static final File logFile = new File("app.log");
    private static MainFrame instance;

    public static void main(String[] args) {
        // add shutdown hook
        Thread shutdownThread = new Thread(MainFrame::onRuntimeShutdown);
        shutdownThread.setName("DASB Shutdown Hook");
        shutdownThread.setDaemon(false);
        Runtime.getRuntime().addShutdownHook(shutdownThread);

        Thread.setDefaultUncaughtExceptionHandler(MainFrame::uncaughtException);

        logger.info("Hello World!");
        Utils.printSystemInfo();

        // set L&F
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Throwable ex) {
            logger.warn("Failed to set L&F", ex);
        }

        // load bass natives
        configureBassLibraryPath();
        BassInit.loadLibraries();

        // run app
        EventQueue.invokeLater(getInstance());
    }

    public static MainFrame getInstance() {
        if (instance == null) {
            instance = new MainFrame();
        }
        return instance;
    }

    public static boolean hasInstance() {
        return instance != null;
    }

    private static void uncaughtException(Thread t, Throwable e) {
        logger.error("Uncaught exception in thread " + t.getName(), e);
        if (instance == null || "main".equals(t.getName())) {
            JOptionPane.showMessageDialog(instance, "A fatal error occurred and the application will be closed.\nThe logs can be found at " + logFile.getAbsolutePath(), "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(-1);
        }
        EventQueue.invokeLater(() -> JOptionPane.showMessageDialog(instance, "An unexpected error occurred.\nThe bot will keep running where possible.\nThe logs can be found at " + logFile.getAbsolutePath(), "Error", JOptionPane.ERROR_MESSAGE));
    }

    private static void onRuntimeShutdown() {
        logger.info("Goodbye!");
    }

    private static void configureBassLibraryPath() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String nativeSubdir;

        if (os.contains("win")) {
            nativeSubdir = arch.contains("64") ? "win64" : "win32";
        } else if (os.contains("mac")) {
            nativeSubdir = "mac";
        } else if (os.contains("nux") || os.contains("nix") || os.contains("aix")) {
            nativeSubdir = arch.contains("64") ? "linux64" : "linux32";
        } else {
            logger.warn("Unsupported OS for BASS native auto-discovery: {}", os);
            return;
        }

        List<File> candidates = new ArrayList<>(Arrays.asList(
                new File("natives/" + nativeSubdir),
                new File("../natives/" + nativeSubdir),
                new File("../../natives/" + nativeSubdir),
                new File(System.getProperty("user.dir"), "natives/" + nativeSubdir)
        ));

        File appDir = getApplicationDirectory();
        if (appDir != null) {
            candidates.add(new File(appDir, "natives/" + nativeSubdir));
            File parentDir = appDir.getParentFile();
            if (parentDir != null) {
                candidates.add(new File(parentDir, "natives/" + nativeSubdir));
                candidates.add(new File(parentDir, "app/natives/" + nativeSubdir));
            }
        }

        for (File candidate : candidates) {
            if (candidate != null && candidate.isDirectory()) {
                String absolute = candidate.getAbsoluteFile().getAbsolutePath();
                System.setProperty("org.lwjgl.librarypath", absolute);
                System.setProperty("java.library.path", absolute);
                logger.info("Using BASS natives: {}", absolute);
                return;
            }
        }

        logger.warn("Could not auto-discover BASS natives in {}", candidates);
    }

    private static File getApplicationDirectory() {
        try {
            CodeSource codeSource = MainFrame.class.getProtectionDomain().getCodeSource();
            if (codeSource == null) {
                return null;
            }
            URL location = codeSource.getLocation();
            if (location == null) {
                return null;
            }
            File locationFile = new File(location.toURI());
            return locationFile.isFile() ? locationFile.getParentFile() : locationFile;
        } catch (URISyntaxException | IllegalArgumentException ex) {
            logger.warn("Failed to resolve application directory", ex);
            return null;
        }
    }

    private JTabbedPane tabs;
    private int idxHome;
    public HomePanel tabHome;
    private int idxMaintain;
    public MaintenancePanel tabMaintain;
    private int idxSettings;
    public SettingsPanel tabSettings;

    private MainFrame() {
        updateTitle();
        setIconImage(Utils.getIcon("icomoon/32px/017-headphones.png", 32, true).getImage());
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                final Config config = DiscordAudioStreamBot.getConfig();
                if(config.botToken != null && config.isAutoLogin()) {
                    tabHome.loginButtonPressed(1);
                }
            }

            @Override
            public void windowClosing(WindowEvent e) {
                JDA jda = DiscordAudioStreamBot.getInstance().getJDA();
                if (jda != null) {
                    jda.shutdownNow();
                }
                MainFrame.this.dispose();
                System.exit(0);
            }
        });

        initComponents();
        layoutComponents();

        setMinimumSize(new Dimension(800, 600));
        pack();
    }

    private void initComponents() {
        tabs = new JTabbedPane();
        tabHome = new HomePanel(this);
        tabMaintain = new MaintenancePanel();
        tabSettings = new SettingsPanel();

        // home
        idxHome = tabs.getTabCount();
        tabs.addTab("Home", getTabIcon("001-home"), new BorderPanel(tabHome));

        // maintenance
        idxMaintain = tabs.getTabCount();
        tabs.addTab("Maintenance", getTabIcon("146-wrench"), new BorderPanel(tabMaintain));
        tabs.setEnabledAt(idxMaintain, false);

        // settings
        idxSettings = tabs.getTabCount();
        tabs.addTab("Settings", getTabIcon("190-menu"), new BorderPanel(tabSettings));
    }

    private Icon getTabIcon(String file) {
        return Utils.getIcon("icomoon/32px/" + file + ".png", 24, true);
    }

    private void layoutComponents() {
        setContentPane(tabs);
    }

    public void updateLoginStatus(JDA.Status status) {
        updateTitle();
        tabs.setEnabledAt(idxMaintain, status == JDA.Status.CONNECTED);
        tabMaintain.updateLoginStatus(status);
        tabSettings.updateLoginStatus(status);
    }

    @Override
    public void run() {
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void updateTitle() {
        final JDA jda = DiscordAudioStreamBot.getInstance().getJDA();
        JDA.Status status = jda != null ? jda.getStatus() : JDA.Status.SHUTDOWN;
        String title = DiscordAudioStreamBot.NAME + " - " + format(status);
        if (status == JDA.Status.CONNECTED) {
            title += " [" + jda.getSelfUser().getName() + "]";
        }
        setTitle(title);
    }

    private String format(JDA.Status status) {
        String[] words = status.name().replace("_", " ").split(" ", -1);
        for (int i = 0; i < words.length; i++) {
            final String word = words[i];
            words[i] = word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase();
        }
        return String.join(" ", words);
    }
}
