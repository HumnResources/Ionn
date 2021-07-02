package com.zischase.discordbot.commands.audiocommands;

import com.zischase.discordbot.audioplayer.AudioManager;
import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.guildcontrol.GuildContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class Skip extends Command {

	public Skip() {
		super(false);
	}

	@Override
	public @NotNull String shortDescription() {
		return "Skips the current song.";
	}

	@Override
	public List<String> getAliases() {
		return List.of("next", "n", "s");
	}

	@Override
	public String helpText() {
		return "Skip ~ Skip current track and play next in queue.";
	}

	@Override
	public void handle(CommandContext ctx) {
		AudioManager audioManager = GuildContext.get(ctx.getGuild().getId())
				.audioManager();

		audioManager.getScheduler()
				.nextTrack();

	}

}
