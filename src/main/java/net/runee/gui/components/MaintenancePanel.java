package net.runee.gui.components;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class MaintenancePanel extends JPanel implements EventListener {
    private JList<Guild> guilds;
    private JList<AudioChannel> audioChannels;
    private JList<Member> audioUsers;
    private JList<MutedAudioUserItem> mutedAudioUsers;
    private JLabel connectionStatus;
    private JLabel permissionStatus;
    private JLabel autoJoinStatus;
    private JLabel mutedAudioStatus;
    private JTextField audioUserSearch;
    private JButton addGuild;
    private JButton removeGuild;
    private JButton joinChannel;
    private JButton leaveChannel;
    private JButton setAutoJoin;
    private JButton clearAutoJoin;
    private JButton restartAudio;
    private JButton refreshChannels;
    private JButton muteSelectedAudioUser;
    private JButton muteSearchedAudioUser;
    private JButton unmuteSelectedAudioUser;
    private JButton clearMutedAudioUsers;
    private JButton refreshAudioUsers;

    private DefaultListModel<Guild> guildsModel;
    private DefaultListModel<AudioChannel> audioChannelsModel;
    private DefaultListModel<Member> audioUsersModel;
    private DefaultListModel<MutedAudioUserItem> mutedAudioUsersModel;
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
        audioUsersModel = new DefaultListModel<>();
        mutedAudioUsersModel = new DefaultListModel<>();
    }

    private void initComponents() {
        guilds = new JList<>();
        guilds.setModel(guildsModel);
        guilds.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        guilds.setCellRenderer(GuildListCellRenderer.getInstance());
        guilds.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateAudioChannels();
                updateAudioUsers();
                updateMutedAudioUsers();
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
                updateAudioUsers();
                updateGuildControls();
            }
        });

        audioUsers = new JList<>();
        audioUsers.setModel(audioUsersModel);
        audioUsers.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        audioUsers.setVisibleRowCount(5);
        audioUsers.setCellRenderer(new MemberListCellRenderer());
        audioUsers.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateGuildControls();
            }
        });

        mutedAudioUsers = new JList<>();
        mutedAudioUsers.setModel(mutedAudioUsersModel);
        mutedAudioUsers.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        mutedAudioUsers.setVisibleRowCount(5);
        mutedAudioUsers.setCellRenderer(new MutedAudioUserListCellRenderer());
        mutedAudioUsers.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateGuildControls();
            }
        });

        connectionStatus = new JLabel();
        permissionStatus = new JLabel();
        autoJoinStatus = new JLabel();
        mutedAudioStatus = new JLabel();

        audioUserSearch = new JTextField();
        audioUserSearch.setToolTipText("User mention, user ID, username, or nickname");
        Utils.addChangeListener(audioUserSearch, e -> updateGuildControls());

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
            updateAudioUsers();
            updateMutedAudioUsers();
            updateGuildControls();
        });

        muteSelectedAudioUser = new JButton("Mute selected", Utils.getIcon("icomoon/32px/299-volume-mute2.png", 16, true));
        muteSelectedAudioUser.addActionListener(e -> muteSelectedAudioUser());

        muteSearchedAudioUser = new JButton("Mute by search", Utils.getIcon("icomoon/32px/135-search.png", 16, true));
        muteSearchedAudioUser.addActionListener(e -> muteSearchedAudioUser());

        unmuteSelectedAudioUser = new JButton("Unmute selected", Utils.getIcon("icomoon/32px/295-volume-high.png", 16, true));
        unmuteSelectedAudioUser.addActionListener(e -> unmuteSelectedAudioUser());

        clearMutedAudioUsers = new JButton("Clear muted", Utils.getIcon("icomoon/32px/272-cross.png", 16, true));
        clearMutedAudioUsers.addActionListener(e -> clearMutedAudioUsers());

        refreshAudioUsers = new JButton("Refresh users", Utils.getIcon("icomoon/32px/303-loop2.png", 16, true));
        refreshAudioUsers.addActionListener(e -> {
            updateAudioUsers();
            updateMutedAudioUsers();
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

        JPanel audioUserLists = new JPanel(new GridLayout(1, 2, 6, 6));
        audioUserLists.add(buildTitledPanel("Users in channel", new JScrollPane(audioUsers)));
        audioUserLists.add(buildTitledPanel("Muted users", new JScrollPane(mutedAudioUsers)));

        JPanel audioUserSearchPanel = new JPanel(new BorderLayout(6, 0));
        audioUserSearchPanel.add(audioUserSearch, BorderLayout.CENTER);
        audioUserSearchPanel.add(muteSearchedAudioUser, BorderLayout.EAST);

        JPanel audioUserActions = new JPanel(new GridLayout(0, 4, 6, 6));
        audioUserActions.add(muteSelectedAudioUser);
        audioUserActions.add(unmuteSelectedAudioUser);
        audioUserActions.add(clearMutedAudioUsers);
        audioUserActions.add(refreshAudioUsers);

        JPanel audioUserControls = new JPanel(new BorderLayout(0, 6));
        audioUserControls.add(audioUserSearchPanel, BorderLayout.NORTH);
        audioUserControls.add(audioUserActions, BorderLayout.SOUTH);

        JPanel audioUserPanel = new JPanel(new BorderLayout(6, 6));
        audioUserPanel.setBorder(BorderFactory.createTitledBorder("Audio user mute"));
        audioUserPanel.add(mutedAudioStatus, BorderLayout.NORTH);
        audioUserPanel.add(audioUserLists, BorderLayout.CENTER);
        audioUserPanel.add(audioUserControls, BorderLayout.SOUTH);

        JSplitPane audioSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(audioChannels), audioUserPanel);
        audioSplitPane.setResizeWeight(0.45d);
        audioSplitPane.setBorder(BorderFactory.createEmptyBorder());

        JPanel voicePanel = new JPanel(new BorderLayout(6, 6));
        voicePanel.setBorder(BorderFactory.createTitledBorder("Voice control"));
        voicePanel.add(statusPanel, BorderLayout.NORTH);
        voicePanel.add(audioSplitPane, BorderLayout.CENTER);
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
        updateAudioUsers();
        updateMutedAudioUsers();
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

    private void updateAudioUsers() {
        Guild guild = guilds.getSelectedValue();
        Member selected = audioUsers.getSelectedValue();
        String selectedUserId = selected != null ? selected.getId() : null;

        audioUsersModel.clear();
        if (guild != null) {
            AudioChannel channel = getAudioUserSourceChannel(guild);
            if (channel != null) {
                List<Member> members = getMembersInAudioChannel(guild, channel);
                members.sort(Comparator.comparing(Member::getEffectiveName, String.CASE_INSENSITIVE_ORDER));
                for (Member member : members) {
                    if (!Objects.equals(member.getId(), guild.getSelfMember().getId())) {
                        audioUsersModel.addElement(member);
                    }
                }
            }
        }

        if (selectedUserId != null) {
            for (int i = 0; i < audioUsersModel.size(); i++) {
                Member member = audioUsersModel.get(i);
                if (Objects.equals(member.getId(), selectedUserId)) {
                    audioUsers.setSelectedIndex(i);
                    break;
                }
            }
        }
    }

    private void updateMutedAudioUsers() {
        Guild guild = guilds.getSelectedValue();
        MutedAudioUserItem selected = mutedAudioUsers.getSelectedValue();
        String selectedUserId = selected != null ? selected.userId : null;

        mutedAudioUsersModel.clear();
        if (guild != null) {
            GuildConfig guildConfig = DiscordAudioStreamBot.getConfig().getGuildConfig(guild);
            Set<String> mutedUserIds = guildConfig.getMutedAudioUserIdsSnapshot();
            List<MutedAudioUserItem> items = new ArrayList<>();
            for (String userId : mutedUserIds) {
                items.add(new MutedAudioUserItem(userId, formatUser(guild, userId)));
            }
            items.sort(Comparator.comparing(item -> item.label, String.CASE_INSENSITIVE_ORDER));
            for (MutedAudioUserItem item : items) {
                mutedAudioUsersModel.addElement(item);
            }
        }

        if (selectedUserId != null) {
            for (int i = 0; i < mutedAudioUsersModel.size(); i++) {
                MutedAudioUserItem item = mutedAudioUsersModel.get(i);
                if (Objects.equals(item.userId, selectedUserId)) {
                    mutedAudioUsers.setSelectedIndex(i);
                    break;
                }
            }
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
                audioUsersModel.clear();
                mutedAudioUsersModel.clear();
                listening = false;
                updateGuildControls();
                break;
        }
    }

    public void onVoiceStateChanged(Guild guild) {
        Guild selected = guilds.getSelectedValue();
        if (selected == null || guild == null || Objects.equals(selected.getId(), guild.getId())) {
            updateAudioUsers();
            updateMutedAudioUsers();
            updateGuildControls();
        }
    }

    private void updateGuildControls() {
        Guild guild = guilds.getSelectedValue();
        AudioChannel selectedChannel = audioChannels.getSelectedValue();
        AudioChannel connectedChannel = guild != null ? guild.getAudioManager().getConnectedChannel() : null;
        Member selectedAudioUser = audioUsers.getSelectedValue();
        MutedAudioUserItem selectedMutedAudioUser = mutedAudioUsers.getSelectedValue();
        boolean hasGuild = guild != null;
        boolean hasChannel = selectedChannel != null;
        boolean canConnect = hasChannel && guild.getSelfMember().hasPermission(selectedChannel, Permission.VOICE_CONNECT);
        boolean isConnected = connectedChannel != null;
        boolean hasAudioUser = selectedAudioUser != null;
        boolean hasMutedAudioUser = selectedMutedAudioUser != null;
        boolean hasAudioUserSearch = !audioUserSearch.getText().trim().isEmpty();
        int mutedAudioUserCount = hasGuild ? DiscordAudioStreamBot.getConfig().getGuildConfig(guild).getMutedAudioUserIdsSnapshot().size() : 0;

        removeGuild.setEnabled(hasGuild);
        audioChannels.setEnabled(hasGuild);
        audioUsers.setEnabled(hasGuild);
        mutedAudioUsers.setEnabled(hasGuild);
        audioUserSearch.setEnabled(hasGuild);
        joinChannel.setEnabled(canConnect);
        leaveChannel.setEnabled(isConnected);
        restartAudio.setEnabled(isConnected);
        setAutoJoin.setEnabled(hasChannel);
        clearAutoJoin.setEnabled(hasGuild && DiscordAudioStreamBot.getConfig().getGuildConfig(guild).autoJoinAudioChannelId != null);
        refreshChannels.setEnabled(hasGuild);
        muteSelectedAudioUser.setEnabled(hasGuild && hasAudioUser);
        muteSearchedAudioUser.setEnabled(hasGuild && hasAudioUserSearch);
        unmuteSelectedAudioUser.setEnabled(hasGuild && hasMutedAudioUser);
        clearMutedAudioUsers.setEnabled(hasGuild && mutedAudioUserCount > 0);
        refreshAudioUsers.setEnabled(hasGuild);

        if (!hasGuild) {
            connectionStatus.setText("Connected channel: N/A");
            permissionStatus.setText("Permissions: N/A");
            autoJoinStatus.setText("Auto-join: N/A");
            mutedAudioStatus.setText("Muted audio users: N/A");
            return;
        }

        connectionStatus.setText("Connected channel: " + (connectedChannel != null ? connectedChannel.getName() : "none"));
        permissionStatus.setText("Permissions: " + (hasChannel ? (canConnect ? "can join selected channel" : "cannot join selected channel") : "select a channel"));

        GuildConfig guildConfig = DiscordAudioStreamBot.getConfig().getGuildConfig(guild);
        AudioChannel autoJoinChannel = formatConfiguredAudioChannel(guild, guildConfig.autoJoinAudioChannelId);
        autoJoinStatus.setText("Auto-join: " + (autoJoinChannel != null ? autoJoinChannel.getName() : "none"));

        AudioChannel audioUserSourceChannel = getAudioUserSourceChannel(guild);
        mutedAudioStatus.setText("Users shown from: " + (audioUserSourceChannel != null ? audioUserSourceChannel.getName() : "none") + " | Muted: " + mutedAudioUserCount);
    }

    private void muteSelectedAudioUser() {
        Guild guild = guilds.getSelectedValue();
        Member member = audioUsers.getSelectedValue();
        if (guild == null || member == null) {
            return;
        }
        muteAudioUser(guild, member.getId());
    }

    private void muteSearchedAudioUser() {
        Guild guild = guilds.getSelectedValue();
        if (guild == null) {
            return;
        }

        String search = audioUserSearch.getText().trim();
        if (search.isEmpty()) {
            return;
        }

        String userId = findAudioUserId(guild, search);
        if (userId == null) {
            return;
        }

        muteAudioUser(guild, userId);
        audioUserSearch.setText("");
    }

    private void muteAudioUser(Guild guild, String userId) {
        GuildConfig guildConfig = DiscordAudioStreamBot.getConfig().getGuildConfig(guild);
        if (guildConfig.addMutedAudioUser(userId)) {
            saveConfig();
        }
        updateMutedAudioUsers();
        updateGuildControls();
    }

    private void unmuteSelectedAudioUser() {
        Guild guild = guilds.getSelectedValue();
        MutedAudioUserItem item = mutedAudioUsers.getSelectedValue();
        if (guild == null || item == null) {
            return;
        }

        GuildConfig guildConfig = DiscordAudioStreamBot.getConfig().getGuildConfig(guild);
        if (guildConfig.removeMutedAudioUser(item.userId)) {
            saveConfig();
        }
        updateMutedAudioUsers();
        updateGuildControls();
    }

    private void clearMutedAudioUsers() {
        Guild guild = guilds.getSelectedValue();
        if (guild == null) {
            return;
        }

        GuildConfig guildConfig = DiscordAudioStreamBot.getConfig().getGuildConfig(guild);
        if (!guildConfig.getMutedAudioUserIdsSnapshot().isEmpty()) {
            guildConfig.clearMutedAudioUsers();
            saveConfig();
        }
        updateMutedAudioUsers();
        updateGuildControls();
    }

    private String findAudioUserId(Guild guild, String search) {
        List<Member> members = Utils.findMember(guild, search);
        if (members.size() == 1) {
            return members.get(0).getId();
        }
        if (members.size() > 1) {
            JOptionPane.showMessageDialog(this, "Multiple users match that search. Use a mention or user ID.", "Audio user mute", JOptionPane.WARNING_MESSAGE);
            return null;
        }

        String userId = parseUserId(search);
        if (userId != null) {
            return userId;
        }

        JOptionPane.showMessageDialog(this, "No user was found for that search.", "Audio user mute", JOptionPane.WARNING_MESSAGE);
        return null;
    }

    private String parseUserId(String search) {
        Long userId = Utils.tryParseLong(search);
        if (userId == null && search.endsWith(">")) {
            if (search.startsWith("<@!")) {
                userId = Utils.tryParseLong(search.substring(3, search.length() - 1));
            } else if (search.startsWith("<@")) {
                userId = Utils.tryParseLong(search.substring(2, search.length() - 1));
            }
        }
        return userId != null ? userId.toString() : null;
    }

    private AudioChannel getAudioUserSourceChannel(Guild guild) {
        AudioChannel selectedChannel = audioChannels.getSelectedValue();
        if (selectedChannel != null) {
            return selectedChannel;
        }
        return guild != null ? guild.getAudioManager().getConnectedChannel() : null;
    }

    private List<Member> getMembersInAudioChannel(Guild guild, AudioChannel channel) {
        List<Member> result = new ArrayList<>();
        for (GuildVoiceState voiceState : guild.getVoiceStates()) {
            AudioChannel voiceChannel = voiceState.getChannel();
            if (voiceChannel != null && Objects.equals(voiceChannel.getId(), channel.getId())) {
                result.add(voiceState.getMember());
            }
        }
        return result;
    }

    private String formatUser(Guild guild, String userId) {
        Member member = guild.getMemberById(userId);
        User user = member != null ? member.getUser() : DiscordAudioStreamBot.getInstance().getJDA().getUserById(userId);
        return user != null ? Utils.formatUser(user) : userId;
    }

    private JPanel buildTitledPanel(String title, JComponent content) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.add(content, BorderLayout.CENTER);
        return panel;
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
                updateAudioUsers();
                updateMutedAudioUsers();
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

    private static class MemberListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof Member) {
                Member member = (Member) value;
                label.setText(member.getEffectiveName() + " (" + Utils.formatUser(member.getUser()) + ")");
            }
            return label;
        }
    }

    private static class MutedAudioUserListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof MutedAudioUserItem) {
                MutedAudioUserItem item = (MutedAudioUserItem) value;
                label.setText(item.label);
            }
            return label;
        }
    }

    private static class MutedAudioUserItem {
        private final String userId;
        private final String label;

        private MutedAudioUserItem(String userId, String label) {
            this.userId = userId;
            this.label = label;
        }
    }
}
