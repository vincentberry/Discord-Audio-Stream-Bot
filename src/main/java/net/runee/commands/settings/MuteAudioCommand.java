package net.runee.commands.settings;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.runee.errors.CommandException;
import net.runee.misc.Utils;
import net.runee.misc.discord.Command;
import net.runee.model.GuildConfig;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class MuteAudioCommand extends Command {
    public MuteAudioCommand() {
        super(Commands.slash("mute-audio", "Manage users ignored by the bot audio listener"));
        data.addOption(OptionType.STRING, "op", "Operation to perform (add|remove|clear|show)", true);
        data.addOption(OptionType.USER, "user", "User to mute/unmute. Only valid if op = 'add' or 'remove'", false);
        _public = true;
    }

    @Override
    public void run(SlashCommandInteractionEvent ctx) throws CommandException {
        Guild guild = ensureAdminOrOwnerPermission(ctx);

        String op = ensureOptionPresent(ctx, "op").getAsString().toLowerCase(Locale.ROOT);
        User user = null;
        switch (op) {
            case "add":
            case "remove":
                user = ensureOptionPresent(ctx, "user").getAsUser();
                break;
            case "clear":
            case "show":
                ensureOptionAbsent(ctx, "user");
                break;
            default:
                reply(ctx, "Unrecognized operation: `" + op + "`.", Utils.colorRed);
                return;
        }

        GuildConfig guildConfig = getConfig().getGuildConfig(guild);
        switch (op) {
            case "add":
                addMutedAudioUser(ctx, guildConfig, user);
                break;
            case "remove":
                removeMutedAudioUser(ctx, guildConfig, user);
                break;
            case "clear":
                clearMutedAudioUsers(ctx, guildConfig);
                break;
            case "show":
                showMutedAudioUsers(ctx, guild, guildConfig);
                break;
            default:
                throw new IndexOutOfBoundsException();
        }
    }

    private void addMutedAudioUser(SlashCommandInteractionEvent ctx, GuildConfig guildConfig, User user) {
        if (guildConfig.addMutedAudioUser(user)) {
            saveConfig();
            reply(ctx, "User audio muted for the bot.", Utils.colorGreen);
        } else {
            reply(ctx, "User audio is already muted for the bot.", Utils.colorRed);
        }
    }

    private void removeMutedAudioUser(SlashCommandInteractionEvent ctx, GuildConfig guildConfig, User user) {
        if (guildConfig.removeMutedAudioUser(user)) {
            saveConfig();
            reply(ctx, "User audio unmuted for the bot.", Utils.colorGreen);
        } else {
            reply(ctx, "User audio is not muted for the bot.", Utils.colorRed);
        }
    }

    private void clearMutedAudioUsers(SlashCommandInteractionEvent ctx, GuildConfig guildConfig) {
        if (guildConfig.getMutedAudioUserIdsSnapshot().isEmpty()) {
            reply(ctx, "No users are muted for the bot.", Utils.colorGreen);
            return;
        }

        guildConfig.clearMutedAudioUsers();
        saveConfig();
        reply(ctx, "All muted audio users cleared.", Utils.colorGreen);
    }

    private void showMutedAudioUsers(SlashCommandInteractionEvent ctx, Guild guild, GuildConfig guildConfig) {
        Set<String> mutedAudioUserIds = guildConfig.getMutedAudioUserIdsSnapshot();
        if (mutedAudioUserIds.isEmpty()) {
            reply(ctx, "No users are muted for the bot.", Utils.colorGreen);
            return;
        }

        String users = mutedAudioUserIds
                .stream()
                .sorted()
                .map(userId -> formatUser(ctx.getJDA(), guild, userId))
                .collect(Collectors.joining("\n" + Utils.ucListItem));
        reply(ctx, "Muted audio users:\n" + Utils.ucListItem + users, Utils.colorGreen);
    }

    private String formatUser(JDA jda, Guild guild, String userId) {
        Member member = guild.getMemberById(userId);
        User user = member != null ? member.getUser() : jda.getUserById(userId);
        return "`" + (user != null ? Utils.formatUser(user) : userId) + "`";
    }
}
