package com.zischase.discordbot.commands.audiocommands;

import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.guildcontrol.GuildContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class NowPlaying extends Command
{
	
	public NowPlaying()
	{
		super(false);
	}
	
	@Override
	public String helpText() {
		return """
				Displays the currently playing song.
				
				Usage:
					np/nowplaying/playing/currentsong
				""";
	}
	
	@Override
	public @NotNull String shortDescription() {
		return "Displays the currently playing song.";
	}
	
	@Override
	public List<String> getAliases()
	{
		return List.of("np", "Playing", "currentsong", "nowplaying");
	}
	
	@Override
	public void handle(CommandContext ctx)
	{
		GuildContext.get(ctx.getGuild())
                    .playerPrinter()
                    .printNowPlaying(GuildContext.get(ctx.getGuild()).audioManager(), ctx.getChannel());

	}
	
}
