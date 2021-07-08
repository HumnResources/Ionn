package com.zischase.discordbot.commands.audiocommands;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.zischase.discordbot.DBQueryHandler;
import com.zischase.discordbot.audioplayer.AudioManager;
import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.guildcontrol.GuildContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;

public class Shuffle extends Command {


	public Shuffle() {
		super(true);
	}

	@Override
	public @NotNull String shortDescription() {
		return "Shuffles current playlist.";
	}

	@Override
	public String helpText() {
		return """
				%s
								
				Usage:
					`shuffle`
				""";
	}

	@Override
	public void handle(CommandContext ctx) {
		shuffle(ctx.getGuild().getId(), GuildContext.get(ctx.getGuild().getId()).audioManager());
	}

	public void shuffle(String guildID, AudioManager audioManager) {
		if (!DBQueryHandler.getPremiumStatus(guildID)) {
			return;
		}

		GuildContext guildContext = GuildContext.get(guildID);

		ArrayList<AudioTrack> currentQueue = guildContext.audioManager()
				.getScheduler()
				.getQueue();

		Collections.shuffle(currentQueue);
		audioManager.getScheduler().clearQueue();
		audioManager.getScheduler().queueList(currentQueue);
	}
}
