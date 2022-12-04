package com.zischase.discordbot.commands.audiocommands;

import com.zischase.discordbot.DBQueryHandler;
import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.guildcontrol.GuildContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class NowPlaying extends Command {

	public NowPlaying() {
		super(false);
	}

	@Override
	public @NotNull String shortDescription() {
		return "Displays the currently playing song.";
	}

	@Override
	public List<String> getAliases() {
		return List.of("np", "Playing", "currentsong", "nowplaying");
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
	public void handle(CommandContext ctx) {
		DBQueryHandler.set(ctx.getGuild().getId(), "media_settings", "textchannel", ctx.getChannel().getId());
		GuildContext.get(ctx.getGuild().getId())
				.audioManager()
				.getNowPlayingMessageHandler()
				.printNowPlaying(ctx.getChannel(), true);
	}

}
