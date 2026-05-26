package net.runee.model;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class GuildConfig {
    public String guildId;
    public Set<String> commandChannelIds;
    public String autoJoinAudioChannelId;
    public String followedUserId;
    public Set<String> mutedAudioUserIds;

    public GuildConfig() {

    }

    public GuildConfig(Guild guild) {
        this.guildId = guild.getId();
    }

    public GuildConfig(GuildConfig copy) {
        this.guildId = copy.guildId;
        this.autoJoinAudioChannelId = copy.autoJoinAudioChannelId;
        this.followedUserId = copy.followedUserId;
        if(copy.commandChannelIds != null) {
            this.commandChannelIds = new HashSet<>(copy.commandChannelIds);
        }
        if(copy.mutedAudioUserIds != null) {
            this.mutedAudioUserIds = new HashSet<>(copy.mutedAudioUserIds);
        }
    }

    public void addCommandChannel(MessageChannel channel) {
        if(commandChannelIds == null) {
            commandChannelIds = new HashSet<>();
        }
        commandChannelIds.add(channel.getId());
    }

    public void removeCommandChannel(MessageChannel channel) {
        if(commandChannelIds != null) {
            commandChannelIds.remove(channel.getId());
            if(commandChannelIds.isEmpty()) {
                commandChannelIds = null;
            }
        }
    }

    public boolean isCommandChannel(MessageChannel channel) {
        if(commandChannelIds != null) {
            return commandChannelIds.contains(channel.getId());
        } else {
            return true; // default is that every channel is a command channel!
        }
    }

    public synchronized boolean addMutedAudioUser(User user) {
        return addMutedAudioUser(user.getId());
    }

    public synchronized boolean addMutedAudioUser(String userId) {
        if(mutedAudioUserIds == null) {
            mutedAudioUserIds = new HashSet<>();
        }
        return mutedAudioUserIds.add(userId);
    }

    public synchronized boolean removeMutedAudioUser(User user) {
        return removeMutedAudioUser(user.getId());
    }

    public synchronized boolean removeMutedAudioUser(String userId) {
        if(mutedAudioUserIds == null) {
            return false;
        }
        boolean removed = mutedAudioUserIds.remove(userId);
        if(mutedAudioUserIds.isEmpty()) {
            mutedAudioUserIds = null;
        }
        return removed;
    }

    public synchronized void clearMutedAudioUsers() {
        mutedAudioUserIds = null;
    }

    public synchronized boolean isAudioUserMuted(User user) {
        return mutedAudioUserIds != null && mutedAudioUserIds.contains(user.getId());
    }

    public synchronized Set<String> getMutedAudioUserIdsSnapshot() {
        if(mutedAudioUserIds == null) {
            return Collections.emptySet();
        }
        return new HashSet<>(mutedAudioUserIds);
    }
}
