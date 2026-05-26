package net.runee.gui.components;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.guild.GenericGuildEvent;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.guild.update.GuildUpdateIconEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.runee.DiscordAudioStreamBot;
import net.runee.gui.renderer.GuildListCellRenderer;
import net.runee.misc.Utils;
import net.runee.model.GuildConfig;

import javax.annotation.Nonnull;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.Objects;

public class MaintenancePanel extends JPanel implements EventListener {
    private JList<Guild> guilds;
    private JList<AudioChannel> audioChannels;
    private JLabel connectionStatus;
    private JLabel permissionStatus;
    private JLabel autoJoinStatus;
    private JButton addGuild;
    private JButton removeGuild;
    private JButton joinChannel;
    private JButton leaveChannel;
    private JButton setAutoJoin;
    private JButton clearAutoJoin;
    private JButton restartAudio;
    private JButton refreshChannels;

    private DefaultListModel<Guild> guildsModel;
    private DefaultListModel<AudioChannel> audioChannelsModel;
    private boolean listening;

    public MaintenancePanel() {
        initModels();
        initComponents();
        layoutComponents();
        updateGuildControls();
    }

    private void initModels() {
        guildsModel = new DefaultListModel<>();
        audioChannelsModel = new DefaultListModel<>();
    }

    private void initComponents() {
        guilds = new JList<>();
        guilds.setModel(guildsModel);
        guilds.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        guilds.setCellRenderer(GuildListCellRenderer.getInstance());
        guilds.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateAudioChannels();
                updateGuildControls();
            }
        });

        audioChannels = new JList<>();
        audioChannels.setModel(audioChannelsModel);
        audioChannels.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        audioChannels.setVisibleRowCount(10);
        audioChannels.setCellRenderer(new AudioChannelListCellRenderer());
        audioChannels.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateGuildControls();
            }
        });

        connectionStatus = new JLabel();
        permissionStatus = new JLabel();
        autoJoinStatus = new JLabel();

        addGuild = new JButton("Invite bot...", Utils.getIcon("icomoon/32px/116-user-plus.png", 16, true));
        addGuild.addActionListener(e -> {
            if(!Utils.browseUrl(DiscordAudioStreamBot.getInstance().getInviteUrl())) {
                JOptionPane.showMessageDialog(this, "Unable to open invite url in browser.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        removeGuild = new JButton("Leave guild", Utils.getIcon("icomoon/32px/117-user-minus.png", 16, true));
        removeGuild.addActionListener(e -> {
            Guild guild = guilds.getSelectedValue();
            if (guild != null) {
                guild.leave().queue();
            }
        });

        joinChannel = new JButton("Join selected", Utils.getIcon("icomoon/32px/027-bullhorn.png", 16, true));
        joinChannel.addActionListener(e -> {
            AudioChannel channel = audioChannels.getSelectedValue();
            if (channel != null) {
                DiscordAudioStreamBot.getInstance().joinAudio(channel);
                updateGuildControls();
            }
        });

        leaveChannel = new JButton("Leave", Utils.getIcon("icomoon/32px/277-exit.png", 16, true));
        leaveChannel.addActionListener(e -> {
            Guild guild = guilds.getSelectedValue();
            if (guild != null) {
                DiscordAudioStreamBot.getInstance().leaveAudio(guild);
                updateGuildControls();
            }
        });

        setAutoJoin = new JButton("Set auto-join", Utils.getIcon("icomoon/32px/273-checkmark.png", 16, true));
        setAutoJoin.addActionListener(e -> {
            Guild guild = guilds.getSelectedValue();
            AudioChannel channel = audioChannels.getSelectedValue();
            if (guild != null && channel != null) {
                GuildConfig guildConfig = DiscordAudioStreamBot.getConfig().getGuildConfig(guild);
                guildConfig.autoJoinAudioChannelId = channel.getId();
                saveConfig();
                updateGuildControls();
            }
        });

        clearAutoJoin = new JButton("Clear auto-join", Utils.getIcon("icomoon/32px/272-cross.png", 16, true));
        clearAutoJoin.addActionListener(e -> {
            Guild guild = guilds.getSelectedValue();
            if (guild != null) {
                GuildConfig guildConfig = DiscordAudioStreamBot.getConfig().getGuildConfig(guild);
                guildConfig.autoJoinAudioChannelId = null;
                saveConfig();
                updateGuildControls();
            }
        });

        restartAudio = new JButton("Restart audio", Utils.getIcon("icomoon/32px/303-loop2.png", 16, true));
        restartAudio.addActionListener(e -> {
            Guild guild = guilds.getSelectedValue();
            if (guild != null) {
                DiscordAudioStreamBot.getInstance().restartAudio(guild);
                updateGuildControls();
            }
        });

        refreshChannels = new JButton("Refresh", Utils.getIcon("icomoon/32px/135-search.png", 16, true));
        refreshChannels.addActionListener(e -> {
            updateAudioChannels();
            updateGuildControls();
        });
    }

    private void layoutComponents() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel guildPanel = new JPanel(new BorderLayout(6, 6));
        guildPanel.setBorder(BorderFactory.createTitledBorder("Guilds"));
        guildPanel.add(new JScrollPane(guilds), BorderLayout.CENTER);
        guildPanel.add(Utils.buildFlowPanel(addGuild, removeGuild), BorderLayout.SOUTH);

        JPanel statusPanel = new JPanel(new GridLayout(0, 1, 0, 3));
        statusPanel.add(connectionStatus);
        statusPanel.add(permissionStatus);
        statusPanel.add(autoJoinStatus);

        JPanel channelActions = new JPanel(new GridLayout(0, 3, 6, 6));
        channelActions.add(joinChannel);
        channelActions.add(leaveChannel);
        channelActions.add(restartAudio);
        channelActions.add(setAutoJoin);
        channelActions.add(clearAutoJoin);
        channelActions.add(refreshChannels);

        JPanel voicePanel = new JPanel(new BorderLayout(6, 6));
        voicePanel.setBorder(BorderFactory.createTitledBorder("Voice control"));
        voicePanel.add(statusPanel, BorderLayout.NORTH);
        voicePanel.add(new JScrollPane(audioChannels), BorderLayout.CENTER);
        voicePanel.add(channelActions, BorderLayout.SOUTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, guildPanel, voicePanel);
        splitPane.setResizeWeight(0.35d);
        splitPane.setBorder(BorderFactory.createEmptyBorder());
        add(splitPane, BorderLayout.CENTER);
    }

    private void updateGuilds() {
        final JDA jda = DiscordAudioStreamBot.getInstance().getJDA();
        Guild selected = guilds.getSelectedValue();
        String selectedGuildId = selected != null ? selected.getId() : null;

        guildsModel.clear();
        if (jda != null) {
            for (Guild guild : jda.getGuilds()) {
                guildsModel.addElement(guild);
                if (Objects.equals(guild.getId(), selectedGuildId)) {
                    selected = guild;
                }
            }
        }

        if (selected != null) {
            guilds.setSelectedValue(selected, true);
        } else if (!guildsModel.isEmpty()) {
            guilds.setSelectedIndex(0);
        }

        updateAudioChannels();
        updateGuildControls();
    }

    private void updateAudioChannels() {
        Guild guild = guilds.getSelectedValue();
        AudioChannel selected = audioChannels.getSelectedValue();
        String selectedChannelId = selected != null ? selected.getId() : null;

        audioChannelsModel.clear();
        if (guild != null) {
            for (AudioChannel channel : guild.getVoiceChannels()) {
                audioChannelsModel.addElement(channel);
            }
            for (AudioChannel channel : guild.getStageChannels()) {
                audioChannelsModel.addElement(channel);
            }
        }

        AudioChannel channelToSelect = null;
        for (int i = 0; i < audioChannelsModel.size(); i++) {
            AudioChannel channel = audioChannelsModel.get(i);
            if (Objects.equals(channel.getId(), selectedChannelId)) {
                channelToSelect = channel;
                break;
            }
        }

        if (channelToSelect == null && guild != null) {
            String autoJoinChannelId = DiscordAudioStreamBot.getConfig().getGuildConfig(guild).autoJoinAudioChannelId;
            for (int i = 0; i < audioChannelsModel.size(); i++) {
                AudioChannel channel = audioChannelsModel.get(i);
                if (Objects.equals(channel.getId(), autoJoinChannelId)) {
                    channelToSelect = channel;
                    break;
                }
            }
        }

        if (channelToSelect != null) {
            audioChannels.setSelectedValue(channelToSelect, true);
        }
    }

    public void updateLoginStatus(JDA.Status status) {
        switch (status) {
            case CONNECTED:
                updateGuilds();
                JDA jda = DiscordAudioStreamBot.getInstance().getJDA();
                if (!listening) {
                    jda.addEventListener(this);
                    listening = true;
                }
                break;
            case SHUTDOWN:
            case FAILED_TO_LOGIN:
                guildsModel.clear();
                audioChannelsModel.clear();
                listening = false;
                updateGuildControls();
                break;
        }
    }

    public void onVoiceStateChanged(Guild guild) {
        Guild selected = guilds.getSelectedValue();
        if (selected == null || guild == null || Objects.equals(selected.getId(), guild.getId())) {
            updateGuildControls();
        }
    }

    private void updateGuildControls() {
        Guild guild = guilds.getSelectedValue();
        AudioChannel selectedChannel = audioChannels.getSelectedValue();
        AudioChannel connectedChannel = guild != null ? guild.getAudioManager().getConnectedChannel() : null;
        boolean hasGuild = guild != null;
        boolean hasChannel = selectedChannel != null;
        boolean canConnect = hasChannel && guild.getSelfMember().hasPermission(selectedChannel, Permission.VOICE_CONNECT);
        boolean isConnected = connectedChannel != null;

        removeGuild.setEnabled(hasGuild);
        audioChannels.setEnabled(hasGuild);
        joinChannel.setEnabled(canConnect);
        leaveChannel.setEnabled(isConnected);
        restartAudio.setEnabled(isConnected);
        setAutoJoin.setEnabled(hasChannel);
        clearAutoJoin.setEnabled(hasGuild && DiscordAudioStreamBot.getConfig().getGuildConfig(guild).autoJoinAudioChannelId != null);
        refreshChannels.setEnabled(hasGuild);

        if (!hasGuild) {
            connectionStatus.setText("Connected channel: N/A");
            permissionStatus.setText("Permissions: N/A");
            autoJoinStatus.setText("Auto-join: N/A");
            return;
        }

        connectionStatus.setText("Connected channel: " + (connectedChannel != null ? connectedChannel.getName() : "none"));
        permissionStatus.setText("Permissions: " + (hasChannel ? (canConnect ? "can join selected channel" : "cannot join selected channel") : "select a channel"));

        GuildConfig guildConfig = DiscordAudioStreamBot.getConfig().getGuildConfig(guild);
        AudioChannel autoJoinChannel = formatConfiguredAudioChannel(guild, guildConfig.autoJoinAudioChannelId);
        autoJoinStatus.setText("Auto-join: " + (autoJoinChannel != null ? autoJoinChannel.getName() : "none"));
    }

    private AudioChannel formatConfiguredAudioChannel(Guild guild, String channelId) {
        if (channelId == null) {
            return null;
        }
        AudioChannel channel = guild.getVoiceChannelById(channelId);
        if (channel == null) {
            channel = guild.getStageChannelById(channelId);
        }
        return channel;
    }

    private void saveConfig() {
        try {
            DiscordAudioStreamBot.saveConfig();
        } catch (IOException ex) {
            Utils.guiError(this, "Failed to save config", ex);
        }
    }

    @Override
    public void onEvent(@Nonnull GenericEvent e) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> onEvent(e));
            return;
        }

        if(e instanceof GenericGuildEvent) {
            final Guild guild = ((GenericGuildEvent) e).getGuild();
            if (e instanceof GuildJoinEvent) {
                guildsModel.addElement(guild);
                updateGuildControls();
            }
            if(e instanceof GuildLeaveEvent) {
                guildsModel.removeElement(guild);
                int index = guilds.getSelectedIndex();
                if (index == guildsModel.size()) {
                    index--;
                }
                if (index >= 0) {
                    guilds.setSelectedIndex(index);
                }

                updateGuildControls();
                GuildListCellRenderer.getInstance().clearIconCache(guild);
            }
            if(e instanceof GuildUpdateIconEvent) {
                GuildListCellRenderer.getInstance().clearIconCache(guild);
            }

            Guild selected = guilds.getSelectedValue();
            if (selected != null && Objects.equals(selected.getId(), guild.getId())) {
                updateAudioChannels();
                updateGuildControls();
            }
        }
    }

    private static class AudioChannelListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof AudioChannel) {
                AudioChannel channel = (AudioChannel) value;
                label.setText(channel.getName());
            }
            return label;
        }
    }
}
