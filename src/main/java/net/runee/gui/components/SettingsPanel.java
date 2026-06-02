package net.runee.gui.components;

import com.formdev.flatlaf.FlatClientProperties;
import com.jgoodies.forms.builder.FormBuilder;
import jouvieje.bass.Bass;
import jouvieje.bass.structures.BASS_DEVICEINFO;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.StatusChangeEvent;
import net.dv8tion.jda.api.events.session.ShutdownEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.requests.CloseCode;
import net.runee.DiscordAudioStreamBot;
import net.runee.gui.MainFrame;
import net.runee.gui.renderer.PlaybackDeviceListCellRenderer;
import net.runee.gui.renderer.RecordingDeviceListCellRenderer;
import net.runee.gui.listitems.PlaybackDeviceItem;
import net.runee.misc.Utils;
import net.runee.misc.gui.SpecBuilder;
import net.runee.gui.listitems.RecordingDeviceItem;
import net.runee.model.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.security.auth.login.LoginException;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SettingsPanel extends JPanel implements EventListener {
    private static final Logger logger = LoggerFactory.getLogger(SettingsPanel.class);

    private static final Color MUTED_TEXT = new Color(0x98A0AC);

    private final MainFrame mainFrame;

    // connection
    private JButton loginButton;
    private JLabel loginLabel;
    private JLabel voiceLabel;
    private JLabel pingLabel;
    private Long gatewayPing;
    private final Map<Guild, Long> audioPings = new HashMap<>();

    // general
    private JPasswordField botToken;
    private JCheckBox autoLogin;
    private JCheckBox autoUpdate;

    // audio
    private JButton speakEnabled;
    private JButton listenEnabled;
    private JList<RecordingDeviceItem> recordingDevices;
    private JScrollPane recordingDevicesScroll;
    private JList<PlaybackDeviceItem> playbackDevices;
    private JScrollPane playbackDevicesScroll;
    private JButton refreshDevices;
    private JButton restartAudio;
    private JCheckBox speakThresholdEnabled;
    private JSlider speakThreshold;
    private boolean loadingConfig;

    public SettingsPanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        initComponents();
        layoutComponents();
        loadConfig();
        applyConnectionStatus(JDA.Status.SHUTDOWN, true, null);
    }

    private void initComponents() {
        final DiscordAudioStreamBot bot = DiscordAudioStreamBot.getInstance();

        // connection
        loginButton = new JButton("Connect", Utils.getIcon("icomoon/32px/183-switch.png", 18, true));
        loginButton.setFocusPainted(false);
        loginButton.setIconTextGap(8);
        loginButton.putClientProperty(FlatClientProperties.STYLE,
                "background: #3A40AC; foreground: #FFFFFF; borderColor: #3A40AC; "
                        + "hoverBackground: #333AA0; pressedBackground: #2E348C; "
                        + "disabledBackground: #C7C9DA; disabledForeground: #FFFFFF");
        loginButton.addActionListener(e -> loginButtonPressed(0));

        loginLabel = createWrapLabel();
        voiceLabel = createWrapLabel();
        voiceLabel.setForeground(MUTED_TEXT);
        pingLabel = new JLabel("Gateway: N/A");
        pingLabel.setForeground(MUTED_TEXT);
        pingLabel.setFont(pingLabel.getFont().deriveFont(11f));

        // general
        botToken = new JPasswordField();
        botToken.setEchoChar('•');
        botToken.setToolTipText("Bot token");
        Utils.addChangeListener(botToken, e -> {
            DiscordAudioStreamBot.getConfig().botToken = Utils.emptyStringToNull(new String(((JPasswordField) e.getSource()).getPassword()));
            updateAutoLoginEnabled();
            updateLoginButtonEnabled();
            saveConfig();
        });
        autoLogin = new JCheckBox();
        autoLogin.setToolTipText("Login automatically when the application opens");
        autoLogin.addActionListener(e -> {
            final Config cfg = DiscordAudioStreamBot.getConfig();
            cfg.autoLogin = autoLogin.isSelected();
            saveConfig();
        });
        autoUpdate = new JCheckBox();
        autoUpdate.setToolTipText("Check GitHub releases and install new packaged versions automatically");
        autoUpdate.addActionListener(e -> {
            final Config cfg = DiscordAudioStreamBot.getConfig();
            cfg.autoUpdate = autoUpdate.isSelected();
            saveConfig();
        });

        // audio
        speakEnabled = new JButton();
        speakEnabled.setToolTipText("Mute or unmute the bot microphone");
        speakEnabled.addActionListener(e -> {
            final Config cfg = DiscordAudioStreamBot.getConfig();
            cfg.speakEnabled = !cfg.getSpeakEnabled();
            bot.setSpeakEnabled(cfg.getSpeakEnabled());
            updateSpeakEnabled();
            saveConfig();
        });
        listenEnabled = new JButton();
        listenEnabled.setToolTipText("Deafen or undeafen the bot output");
        listenEnabled.addActionListener(e -> {
            final Config cfg = DiscordAudioStreamBot.getConfig();
            cfg.listenEnabled = !cfg.getListenEnabled();
            bot.setListenEnabled(cfg.getListenEnabled());
            updateListenEnabled();
            saveConfig();
        });
        recordingDevices = new JList<>();
        recordingDevices.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        recordingDevices.setCellRenderer(new RecordingDeviceListCellRenderer());
        recordingDevices.setVisibleRowCount(6);
        recordingDevices.addListSelectionListener(e -> {
            if (!loadingConfig && !e.getValueIsAdjusting() && recordingDevices.getSelectedIndex() >= 0) {
                RecordingDeviceItem value = recordingDevices.getSelectedValue();
                String recordingDevice = value != null ? value.getName() : null;
                bot.setRecordingDevice(recordingDevice);
                DiscordAudioStreamBot.getConfig().recordingDevice = recordingDevice;
                saveConfig();
            }
        });
        recordingDevicesScroll = new JScrollPane(recordingDevices);
        recordingDevicesScroll.setPreferredSize(new Dimension(240, 120));
        playbackDevices = new JList<>();
        playbackDevices.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        playbackDevices.setCellRenderer(new PlaybackDeviceListCellRenderer());
        playbackDevices.setVisibleRowCount(6);
        playbackDevices.addListSelectionListener(e -> {
            if (!loadingConfig && !e.getValueIsAdjusting() && playbackDevices.getSelectedIndex() >= 0) {
                PlaybackDeviceItem value = playbackDevices.getSelectedValue();
                String playbackDevice = value != null ? value.getName() : null;
                bot.setPlaybackDevice(playbackDevice);
                DiscordAudioStreamBot.getConfig().playbackDevice = playbackDevice;
                saveConfig();
            }
        });
        playbackDevicesScroll = new JScrollPane(playbackDevices);
        playbackDevicesScroll.setPreferredSize(new Dimension(240, 120));
        refreshDevices = new JButton("Refresh devices", Utils.getIcon("icomoon/32px/303-loop2.png", 16, true));
        refreshDevices.addActionListener(e -> reloadAudioDevices());
        restartAudio = new JButton("Restart audio", Utils.getIcon("icomoon/32px/280-stop.png", 16, true));
        restartAudio.setEnabled(false);
        restartAudio.addActionListener(e -> {
            bot.restartAudio();
            JOptionPane.showMessageDialog(this, "Audio has been restarted.", "Audio", JOptionPane.INFORMATION_MESSAGE);
        });
        speakThresholdEnabled = new JCheckBox();
        speakThresholdEnabled.addActionListener(e -> {
            final Config cfg = DiscordAudioStreamBot.getConfig();
            cfg.speakThresholdEnabled = !cfg.getSpeakThresholdEnabled();
            updateSpeakThresholdEnabled();
            saveConfig();
        });
        speakThreshold = new JSlider();
        speakThreshold.setMinimum(1);
        speakThreshold.setMaximum(99);
        speakThreshold.addChangeListener(e -> {
            if (!speakThreshold.getValueIsAdjusting()) {
                final Config cfg = DiscordAudioStreamBot.getConfig();
                cfg.speakThreshold = speakThreshold.getValue() * (1d / 100d);
                saveConfig();
            }
        });
    }

    private void loadConfig() {
        loadingConfig = true;
        final Config cfg = DiscordAudioStreamBot.getConfig();

        // general
        botToken.setText(Utils.nullToEmptyString(cfg.botToken));
        autoLogin.setSelected(cfg.isAutoLogin());
        autoUpdate.setSelected(cfg.isAutoUpdate());
        updateAutoLoginEnabled();

        // voice
        speakEnabled.setSelected(cfg.getSpeakEnabled());
        updateSpeakEnabled();
        listenEnabled.setSelected(cfg.getListenEnabled());
        updateListenEnabled();
        reloadAudioDevices();
        speakThresholdEnabled.setSelected(cfg.getSpeakThresholdEnabled());
        speakThreshold.setValue((int) (cfg.getSpeakThreshold() * 100));
        updateSpeakThresholdEnabled();
        loadingConfig = false;
    }

    private void reloadAudioDevices() {
        final Config cfg = DiscordAudioStreamBot.getConfig();
        boolean wasLoading = loadingConfig;
        loadingConfig = true;
        {
            DefaultListModel<RecordingDeviceItem> model = new DefaultListModel<>();
            BASS_DEVICEINFO info = BASS_DEVICEINFO.allocate();
            for (int device = 0; Bass.BASS_RecordGetDeviceInfo(device, info); device++) {
                model.addElement(new RecordingDeviceItem(info.getName(), device));
            }
            info.release();
            recordingDevices.setModel(model);
            for (int i = 0; i < model.getSize(); i++) {
                RecordingDeviceItem recordingDevice = model.get(i);
                String recordingDeviceName = recordingDevice != null ? recordingDevice.getName() : null;
                if (Objects.equals(recordingDeviceName, cfg.recordingDevice)) {
                    recordingDevices.setSelectedIndex(i);
                    break;
                }
            }
        }
        {
            DefaultListModel<PlaybackDeviceItem> model = new DefaultListModel<>();
            BASS_DEVICEINFO info = BASS_DEVICEINFO.allocate();
            for (int device = 0; Bass.BASS_GetDeviceInfo(device, info); device++) {
                model.addElement(new PlaybackDeviceItem(info.getName(), device));
            }
            info.release();
            playbackDevices.setModel(model);
            for (int i = 0; i < model.getSize(); i++) {
                PlaybackDeviceItem playbackDeviceItem = model.get(i);
                String playbackDeviceName = playbackDeviceItem != null ? playbackDeviceItem.getName() : null;
                if (Objects.equals(playbackDeviceName, cfg.playbackDevice)) {
                    playbackDevices.setSelectedIndex(i);
                    break;
                }
            }
        }
        loadingConfig = wasLoading;
    }

    private void saveConfig() {
        try {
            DiscordAudioStreamBot.saveConfig();
        } catch (IOException ex) {
            Utils.guiError(this, "Failed to save config", ex);
        }
    }

    private void layoutComponents() {
        int row = 1;
        FormBuilder
                .create()
                .columns(SpecBuilder
                        .create()
                        .add("r:p")        // label (preferred width)
                        .add("f:50dlu:g")  // field: elastic, grows/shrinks with the window
                        .gap("10dlu")      // fixed gap between the two field groups
                        .add("r:p")        // label
                        .add("f:50dlu:g")  // field: elastic
                        .build()
                )
                .rows(SpecBuilder
                        .create()
                        .add("c:p") // General separator
                        .add("c:p") // Bot token
                        .add("c:p") // Auto update
                        .add("c:p") // Connection
                        .gapUnrelated().add("c:p") // Audio separator
                        .add("c:p") // Mute / Deafen
                        .add("f:80dlu:g") // device lists (the growing row)
                        .add("c:p", 4) // device buttons, voice activity, speak threshold
                        .build()
                )
                .columnGroups(new int[]{1, 5}, new int[]{3, 7})
                .panel(this)
                .border(BorderFactory.createEmptyBorder(5, 5, 5, 5))
                .addSeparator("General").xyw(1, row, 7)
                .add("Bot token").xy(1, row += 2)
                /**/.add(botToken).xy(3, row)
                /**/.add("Auto login").xy(5, row)
                /**/.add(autoLogin).xy(7, row)
                .add("Auto update").xy(5, row += 2)
                /**/.add(autoUpdate).xy(7, row)
                .add("Connection").xy(1, row += 2)
                /**/.add(buildConnectionPanel()).xyw(3, row, 5)
                .addSeparator("Audio").xyw(1, row += 2, 7)
                .add("Mute/Unmute").xy(1, row += 2)
                /**/.add(speakEnabled).xy(3, row)
                /**/.add("Deafen/Undeafen").xy(5, row)
                /**/.add(listenEnabled).xy(7, row)
                .add("Input device").xy(1, row += 2)
                /**/.add(recordingDevicesScroll).xy(3, row)
                /**/.add("Output device").xy(5, row)
                /**/.add(playbackDevicesScroll).xy(7, row)
                .add(Utils.buildFlowPanel(refreshDevices, restartAudio)).xyw(3, row += 2, 5)
                .add("Voice activity").xy(1, row += 2)
                /**/.add(speakThresholdEnabled).xy(3, row)
                .add("Speak threshold").xy(1, row += 2)
                /**/.add(speakThreshold).xy(3, row)
                .build();
    }

    private JComponent buildConnectionPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 0));
        panel.setOpaque(false);

        loginButton.setPreferredSize(new Dimension(150, 34));
        JPanel buttonWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttonWrap.setOpaque(false);
        buttonWrap.add(loginButton);

        JPanel texts = new JPanel(new GridLayout(0, 1, 0, 2));
        texts.setOpaque(false);
        texts.add(loginLabel);
        texts.add(voiceLabel);
        texts.add(pingLabel);

        panel.add(buttonWrap, BorderLayout.WEST);
        panel.add(texts, BorderLayout.CENTER);
        return panel;
    }

    // ---- connection logic (merged from the former HomePanel) ----

    public void loginButtonPressed(int toggle__login__logoff) {
        loginButton.setEnabled(false);
        final DiscordAudioStreamBot bot = DiscordAudioStreamBot.getInstance();
        switch (bot.getJDA() != null ? bot.getJDA().getStatus() : JDA.Status.SHUTDOWN) {
            case CONNECTED:
                if (toggle__login__logoff != 1) {
                    bot.logoff();
                }
                break;
            case SHUTDOWN:
            case FAILED_TO_LOGIN:
                if (DiscordAudioStreamBot.getConfig().botToken == null) {
                    JOptionPane.showMessageDialog(this, "A bot token must be set.", "Error", JOptionPane.ERROR_MESSAGE);
                    loginButton.setEnabled(true);
                    return;
                }
                if (toggle__login__logoff != 2) {
                    try {
                        bot.login();
                        bot.getJDA().addEventListener(this);
                    } catch (LoginException ex) {
                        logger.error("Failed to log in", ex);
                        JOptionPane.showMessageDialog(this, "Failed to log in:\n\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                        loginButton.setEnabled(true);
                    }
                }
                break;
        }
    }

    @Override
    public void onEvent(@Nonnull GenericEvent e) {
        if (e instanceof StatusChangeEvent) {
            applyConnectionStatus(((StatusChangeEvent) e).getNewValue(), false, null);
        }
        if (e instanceof ShutdownEvent) {
            applyConnectionStatus(JDA.Status.SHUTDOWN, false, ((ShutdownEvent) e).getCloseCode());
        }
    }

    /**
     * Single entry point that reflects the connection state across the whole settings screen:
     * status label, connect button, bot-token / restart-audio enablement, voice summary,
     * and (unless this is the initial paint) the navigation rail + maintenance screen.
     */
    public void applyConnectionStatus(JDA.Status status, boolean initial, CloseCode code) {
        final JDA jda = DiscordAudioStreamBot.getInstance().getJDA();

        String connectedName = (status == JDA.Status.CONNECTED && jda != null) ? " [" + jda.getSelfUser().getName() + "]" : "";
        Color statusColor;
        switch (status) {
            case CONNECTED:
                statusColor = Utils.colorGreen;
                break;
            case SHUTDOWN:
            case FAILED_TO_LOGIN:
                statusColor = Utils.colorRed;
                break;
            default:
                statusColor = Utils.colorYellow;
                break;
        }
        String message = "Status: " + friendlyStatus(status) + connectedName;
        String reason = friendlyCloseReason(code);
        if (reason != null) {
            message += " — " + reason;
        }
        setWrapText(loginLabel, message, statusColor);

        switch (status) {
            case CONNECTED:
                loginButton.setText("Disconnect");
                loginButton.setEnabled(true);
                break;
            case SHUTDOWN:
            case FAILED_TO_LOGIN:
                loginButton.setText("Connect");
                updateLoginButtonEnabled();
                break;
            default:
                loginButton.setText("Please wait");
                loginButton.setEnabled(false);
                updatePingLabel();
                break;
        }

        boolean disconnected = status == JDA.Status.SHUTDOWN || status == JDA.Status.FAILED_TO_LOGIN;
        botToken.setEnabled(disconnected);
        restartAudio.setEnabled(status == JDA.Status.CONNECTED);

        onVoiceStateChanged();

        if (!initial) {
            mainFrame.updateLoginStatus(status);
        }
    }

    public void onGatewayPing(Long ping) {
        gatewayPing = ping;
        updatePingLabel();
    }

    public void onAudioPing(Guild guild, Long ping) {
        if (ping != null) {
            audioPings.put(guild, ping);
        } else {
            audioPings.remove(guild);
        }
        updatePingLabel();
    }

    public void onVoiceStateChanged() {
        String status = DiscordAudioStreamBot.getInstance().getVoiceStatusSummary();
        setWrapText(voiceLabel, "Voice: " + status, MUTED_TEXT);
    }

    private void updatePingLabel() {
        List<String> lines = new ArrayList<>();
        lines.add("Gateway: " + formatPing(gatewayPing));
        for (Map.Entry<Guild, Long> entry : audioPings.entrySet()) {
            lines.add("Audio (" + entry.getKey().getName() + "): " + formatPing(entry.getValue()));
        }
        pingLabel.setText("<html>" + String.join("<br>", lines) + "</html>");
    }

    private static String formatPing(Long ping) {
        return ping != null ? ping + " ms" : "N/A";
    }

    private static String friendlyStatus(JDA.Status status) {
        switch (status) {
            case CONNECTED:
                return "Connected";
            case SHUTDOWN:
                return "Disconnected";
            case FAILED_TO_LOGIN:
                return "Login failed";
            case SHUTTING_DOWN:
                return "Disconnecting…";
            case DISCONNECTED:
            case RECONNECT_QUEUED:
            case WAITING_TO_RECONNECT:
            case ATTEMPTING_TO_RECONNECT:
                return "Reconnecting…";
            case INITIALIZING:
            case INITIALIZED:
                return "Starting…";
            default:
                return "Connecting…"; // logging in / websocket / identifying / loading subsystems
        }
    }

    /**
     * Maps a websocket close code to a short, friendly reason. Returns {@code null} for benign
     * cases (normal closure) so we don't append an alarming sentence to the status line.
     */
    private static String friendlyCloseReason(CloseCode code) {
        if (code == null) {
            return null;
        }
        switch (code.getCode()) {
            case 1000: // graceful close
            case 1001: // going away
                return null;
            case 4004:
                return "invalid bot token";
            case 4013:
                return "invalid gateway intents";
            case 4014:
                return "disabled gateway intents (check the bot settings)";
            case 4008:
                return "rate limited";
            case 4010:
            case 4011:
            case 4012:
                return "gateway error";
            default:
                return "connection lost";
        }
    }

    /**
     * Creates a label that wraps its text to the width it is given by the layout, instead of
     * forcing the whole panel wider (which would crop the window on long status / error messages).
     */
    private static JLabel createWrapLabel() {
        JLabel label = new JLabel() {
            @Override
            public Dimension getPreferredSize() {
                // never let the (potentially long) text dictate the layout width
                return new Dimension(1, super.getPreferredSize().height);
            }

            @Override
            public Dimension getMinimumSize() {
                return new Dimension(1, super.getMinimumSize().height);
            }
        };
        label.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                applyWrap(label);
            }
        });
        return label;
    }

    private static void setWrapText(JLabel label, String text, Color color) {
        if (color != null) {
            label.setForeground(color);
        }
        label.putClientProperty("wrapText", text);
        applyWrap(label);
    }

    private static void applyWrap(JLabel label) {
        Object raw = label.getClientProperty("wrapText");
        if (raw == null) {
            return;
        }
        String text = raw.toString();
        int width = label.getWidth();
        Object lastWidth = label.getClientProperty("wrapWidth");
        Object lastText = label.getClientProperty("wrapApplied");
        if (lastWidth instanceof Integer && (Integer) lastWidth == width && text.equals(lastText)) {
            return; // nothing changed -> avoid a setText/relayout loop
        }
        label.putClientProperty("wrapWidth", width);
        label.putClientProperty("wrapApplied", text);
        String esc = escapeHtml(text);
        if (width <= 0) {
            label.setText("<html>" + esc + "</html>");
        } else {
            label.setText("<html><body style='width:" + width + "px'>" + esc + "</body></html>");
        }
        label.revalidate();
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void updateLoginButtonEnabled() {
        final DiscordAudioStreamBot bot = DiscordAudioStreamBot.getInstance();
        JDA.Status status = bot.getJDA() != null ? bot.getJDA().getStatus() : JDA.Status.SHUTDOWN;
        boolean disconnected = status == JDA.Status.SHUTDOWN || status == JDA.Status.FAILED_TO_LOGIN;
        if (disconnected) {
            loginButton.setEnabled(DiscordAudioStreamBot.getConfig().botToken != null);
        }
    }

    private void updateAutoLoginEnabled() {
        boolean enabled = DiscordAudioStreamBot.getConfig().botToken != null;
        autoLogin.setEnabled(enabled);
    }

    private void updateSpeakEnabled() {
        boolean enabled = DiscordAudioStreamBot.getConfig().getSpeakEnabled();
        ImageIcon icon = Utils.getIcon("icomoon/32px/031-mic.png", 24, true);
        if (!enabled) {
            icon = new ImageIcon(Utils.overlayImage((BufferedImage) icon.getImage(), Utils.getIcon("runee/32px/strike-through.png", 24, true).getImage()));
        }
        speakEnabled.setIcon(icon);
        recordingDevices.setEnabled(enabled);
    }

    private void updateListenEnabled() {
        boolean enabled = DiscordAudioStreamBot.getConfig().getListenEnabled();
        ImageIcon icon = Utils.getIcon("icomoon/32px/017-headphones.png", 24, true);
        if (!enabled) {
            icon = new ImageIcon(Utils.overlayImage((BufferedImage) icon.getImage(), Utils.getIcon("runee/32px/strike-through.png", 24, true).getImage()));
        }
        listenEnabled.setIcon(icon);
        playbackDevices.setEnabled(enabled);
    }

    private void updateSpeakThresholdEnabled() {
        boolean enabled = DiscordAudioStreamBot.getConfig().getSpeakThresholdEnabled();
        speakThreshold.setEnabled(enabled);
    }
}
