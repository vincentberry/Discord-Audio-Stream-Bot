package net.runee.commands.bot;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.runee.DiscordAudioStreamBot;
import net.runee.errors.CommandException;
import net.runee.misc.Utils;
import net.runee.misc.discord.Command;

public class RestartAudioCommand extends Command {
    public RestartAudioCommand() {
        super(Commands.slash("restart-audio", "Restart the audio pipeline without restarting the bot"));
        data.addOption(OptionType.BOOLEAN, "all", "Restart audio for all connected guilds. Owner only.", false);
        data.addOption(OptionType.BOOLEAN, "public", "Whether to show this command to others or not", false);
    }

    @Override
    public void run(SlashCommandInteractionEvent ctx) throws CommandException {
        _public = getOptionalBoolean(ctx, "public", false);

        boolean all = getOptionalBoolean(ctx, "all", false);
        DiscordAudioStreamBot bot = DiscordAudioStreamBot.getInstance();

        if (all || ctx.getGuild() == null) {
            ensureOwnerPermission(ctx);
            bot.restartAudio();
            reply(ctx, "Audio pipeline restarted for all connected guilds.", Utils.colorGreen);
            return;
        }

        Guild guild = ensureAdminOrOwnerPermission(ctx);
        bot.restartAudio(guild);
        reply(ctx, "Audio pipeline restarted for this guild.", Utils.colorGreen);
    }
}
