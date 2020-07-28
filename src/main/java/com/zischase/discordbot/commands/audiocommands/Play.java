package com.zischase.discordbot.commands.audiocommands;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.zischase.discordbot.audioplayer.TrackLoader;
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
    public String getName() {
        return "Play/Pause";
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

        return String.format("`Play/Pause : Play or pause the player.` \n" +
                "`Play [url] : Adds the specified song to queue.` \n" +
                "`Aliases    : %s`", aliases);
    }

    @Override
    public void execute(CommandContext ctx) {
        List<String> args = ctx.getArgs();
        Guild guild =  ctx.getGuild();

        if (args.isEmpty()) {
            AudioPlayer player = GuildManager.getContext(guild)
                    .getAudioManager()
                    .getPlayer();
            boolean isPaused = player.isPaused();
            player.setPaused(!isPaused);
            return;
        }

        new TrackLoader().load(ctx.getChannel(), ctx.getMember(), args.get(0));
    }



}
