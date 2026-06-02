package net.runee.gui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import jouvieje.bass.BassInit;
import net.dv8tion.jda.api.JDA;
import net.runee.DiscordAudioStreamBot;
import net.runee.gui.components.MaintenancePanel;
import net.runee.gui.components.SettingsPanel;
import net.runee.misc.Utils;
import net.runee.misc.gui.BorderPanel;
import net.runee.model.Config;
import net.runee.update.AutoUpdater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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
    private static final File logFile = new File("logs/app.log");
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

        // set L&F (FlatLaf "Rail d'icones" theme; see resources/net/runee/resources/theme/FlatLaf.properties)
        System.setProperty("flatlaf.useWindowDecorations", "true");
        System.setProperty("flatlaf.menuBarEmbedded", "true");
        try {
            FlatLaf.registerCustomDefaultsSource("net.runee.resources.theme");
            FlatLightLaf.setup();
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

    private static final Color RAIL_BG = new Color(0x23262E);
    private static final Color RAIL_BORDER = new Color(0x1A1C22);
    private static final Color ACCENT = new Color(0x3A40AC);
    private static final Color NAV_INACTIVE = new Color(0xAEB4C2);
    private static final Color NAV_HOVER = new Color(0x393D46);

    private static final Color NAV_DISABLED = new Color(0x5A6273);

    private static final String CARD_MAINTAIN = "maintenance";
    private static final String CARD_SETTINGS = "settings";

    private CardLayout cards;
    private JPanel content;
    private JToggleButton navMaintain;
    private JToggleButton navSettings;
    public MaintenancePanel tabMaintain;
    public SettingsPanel tabSettings;

    private MainFrame() {
        updateTitle();
        setIconImage(Utils.getIcon("icomoon/32px/017-headphones.png", 32, true).getImage());
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                final Config config = DiscordAudioStreamBot.getConfig();
                AutoUpdater.checkForUpdatesInBackground(MainFrame.this);
                if(config.botToken != null && config.isAutoLogin()) {
                    tabSettings.loginButtonPressed(1);
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

        Dimension minSize = new Dimension(840, 650);
        setMinimumSize(minSize);
        pack();
        setSize(minSize); // open at the minimum size by default
    }

    private void initComponents() {
        tabMaintain = new MaintenancePanel();
        tabSettings = new SettingsPanel(this);

        cards = new CardLayout();
        content = new JPanel(cards);
        content.add(new BorderPanel(tabSettings), CARD_SETTINGS);
        content.add(new BorderPanel(tabMaintain), CARD_MAINTAIN);

        navSettings = createNavButton("Settings", "190-menu", CARD_SETTINGS);
        navMaintain = createNavButton("Maintenance", "146-wrench", CARD_MAINTAIN);
        navMaintain.setEnabled(false);

        ButtonGroup navGroup = new ButtonGroup();
        navGroup.add(navSettings);
        navGroup.add(navMaintain);
        navSettings.setSelected(true);
    }

    /**
     * Builds one entry of the left navigation rail: an icon-over-label toggle button that fills
     * with the indigo accent when selected (white icon/text) and blends into the dark rail otherwise.
     */
    private JToggleButton createNavButton(String text, String iconFile, String card) {
        ImageIcon base = Utils.getIcon("icomoon/32px/" + iconFile + ".png", 19, true);
        final ImageIcon inactiveIcon = Utils.tintIcon(base, NAV_INACTIVE);
        final ImageIcon activeIcon = Utils.tintIcon(base, Color.WHITE);

        final JToggleButton b = new JToggleButton(text, inactiveIcon);
        b.setDisabledIcon(Utils.tintIcon(base, NAV_DISABLED));
        b.setVerticalTextPosition(SwingConstants.BOTTOM);
        b.setHorizontalTextPosition(SwingConstants.CENTER);
        b.setIconTextGap(4);
        b.setFont(b.getFont().deriveFont(9.5f));
        b.setForeground(NAV_INACTIVE);
        b.setBackground(RAIL_BG);
        b.setOpaque(true);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(9, 1, 9, 1));
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
        b.putClientProperty(FlatClientProperties.STYLE,
                "arc: 9; focusWidth: 0; innerFocusWidth: 0; borderWidth: 0; "
                        + "disabledBackground: #23262E; disabledForeground: #5A6273");
        b.addItemListener(e -> {
            boolean selected = b.isSelected();
            b.setBackground(selected ? ACCENT : RAIL_BG);
            b.setForeground(selected ? Color.WHITE : NAV_INACTIVE);
            b.setIcon(selected ? activeIcon : inactiveIcon);
        });
        b.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (b.isEnabled() && !b.isSelected()) {
                    b.setBackground(NAV_HOVER);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (!b.isSelected()) {
                    b.setBackground(RAIL_BG);
                }
            }
        });
        b.addActionListener(e -> cards.show(content, card));
        return b;
    }

    private JPanel buildRail() {
        JPanel rail = new JPanel();
        rail.setLayout(new BoxLayout(rail, BoxLayout.Y_AXIS));
        rail.setBackground(RAIL_BG);
        rail.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, RAIL_BORDER),
                BorderFactory.createEmptyBorder(10, 8, 10, 8)
        ));
        rail.setPreferredSize(new Dimension(80, 0));

        rail.add(navSettings);
        rail.add(Box.createVerticalStrut(6));
        rail.add(navMaintain);
        rail.add(Box.createVerticalGlue());
        return rail;
    }

    private void layoutComponents() {
        JPanel root = new JPanel(new BorderLayout());
        root.add(buildRail(), BorderLayout.WEST);
        root.add(content, BorderLayout.CENTER);
        setContentPane(root);
    }

    public void updateLoginStatus(JDA.Status status) {
        updateTitle();
        boolean connected = status == JDA.Status.CONNECTED;
        navMaintain.setEnabled(connected);
        if (!connected && navMaintain.isSelected()) {
            navSettings.setSelected(true);
            cards.show(content, CARD_SETTINGS);
        }
        tabMaintain.updateLoginStatus(status);
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
