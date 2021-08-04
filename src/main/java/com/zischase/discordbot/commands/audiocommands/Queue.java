package com.zischase.discordbot.commands.audiocommands;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.zischase.discordbot.audioplayer.TrackScheduler;
import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.guildcontrol.GuildContext;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class Queue extends Command {

	public Queue() {
		super(false);
	}

	@Override
	public CommandData getCommandData() {
		OptionData index = new OptionData(OptionType.STRING, "index", "Use queue command to get index numbers", true);

		return super.getCommandData().addSubcommands(
				new SubcommandData("show", "Displays the current queue"),
				new SubcommandData("clear", "Clears the current queue").addOptions(index.setRequired(false).setDescription("Delete specific song using index")),
				new SubcommandData("next", "Moves the song at the current index to next in queue").addOptions(index),
				new SubcommandData("jump", "Shifts the queue to the index number - See queue").addOptions(index),
				new SubcommandData("page", "Goes to page number").addOptions(index.setRequired(true))
		);
	}

	@Override
	public @NotNull String shortDescription() {
		return "Adds audio to the current queue.";
	}

	@Override
	public List<String> getAliases() {
		return List.of("Q", "Qu");
	}

	@Override
	public String helpText() {
		return """
							%s
				    
							Usage:
								`Queue 			  # Show current songs in the queue.`
								`Queue -[clear|c] # Clears the current queue.`
								
				`Aliases : %s`
				""".formatted(shortDescription(), String.join(" ", getAliases()));
	}

	@Override
	public void handle(CommandContext ctx) {
		List<String> args = ctx.getArgs();
		TrackScheduler scheduler = GuildContext.get(ctx.getGuild().getId())
				.audioManager()
				.getScheduler();

		ArrayList<AudioTrack> queue = scheduler.getQueue();

		if (args.isEmpty()) {
			return;
		}

		if (args.size() == 1 && args.get(0).contains("-clear")) {
			EmbedBuilder embed = new EmbedBuilder();
			embed.setColor(Color.BLUE);
			scheduler.clearQueue();
		}
		else if (args.size() == 2 && args.get(1).matches("(?i)(\\d+)")) {
			int                   index = Integer.parseInt(args.get(1));
			String arg = args.get(0);

			if (index < 0 || index > queue.size()) {
				return;
			}
			index = index - 1; // Subtract 1 for '0' based numeration.

			switch (arg) {
				case "-next" -> {
					queue.add(0, queue.get(index));
					queue.remove(index + 1); // Adding one to account for -> shift of list
					scheduler.clearQueue();
					scheduler.queueList(queue);
				}
				case "-jump" -> {
					queue.addAll(queue.subList(0, index));
					ArrayList<AudioTrack> newQueue = new ArrayList<>(queue.subList(index, queue.size()));
					scheduler.clearQueue();
					scheduler.queueList(newQueue);
				}
				case "-clear" -> {
					queue.remove(index);
					scheduler.clearQueue();
					scheduler.queueList(queue);
				}
				case "-page" -> {
					GuildContext.get(ctx.getGuild().getId())
							.audioManager()
							.getQueueMessageHandler()
							.printQueuePage(ctx.getChannel(), index + 1); // Add one to account for 1 based numeration of pages
					return;
				}
			}
		}

		GuildContext.get(ctx.getGuild().getId())
				.audioManager()
				.getQueueMessageHandler()
				.printQueue(ctx.getChannel());
		GuildContext.get(ctx.getGuild().getId())
				.audioManager()
				.getNowPlayingMessageHandler()
				.printNowPlaying(ctx.getChannel());

	}

}
