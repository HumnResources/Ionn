package com.zischase.discordbot.commands.audiocommands;

import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.guildcontrol.GuildManager;

import net.dv8tion.jda.api.entities.Guild;

import java.util.List;

public class Play extends Command {

    public Play() {
        super(false);
    }

    @Override
    public List<String> getAliases() {
        return List.of("P");
    }

    @Override
    public String getHelp() {
        String aliases = "";

        for (String s : getAliases())
            aliases = aliases.concat(s).concat(" ");

        return "Play [URL] ~ Plays audio provided.";
    }

    @Override
    public void handle(CommandContext ctx) {
        List<String> args = ctx.getArgs();
        Guild guild =  ctx.getGuild();

        if (args.isEmpty())
            return;

        GuildManager.getContext(guild)
                .getMusicManager()
                .getAudioPlayerManager()
                .loadItem(args.get(0), GuildManager.getContext(guild).getMusicManager().getScheduler());

    }



}
