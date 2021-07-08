package com.zischase.discordbot.commands.audiocommands;

import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.guildcontrol.GuildContext;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class Repeat extends Command {

	public Repeat() {
		super(true);
	}

	@Override
	public CommandData getCommandData() {
		return super.getCommandData().addOptions(
				new OptionData(OptionType.STRING, "queue", "Sets repeat on or off for current queue").addChoices(
						new Choice("on", "-q on"),
						new Choice("off", "-q off")
				),
				new OptionData(OptionType.STRING, "song", "Sets repeat on or off for current song").addChoices(
						new Choice("on", "-s on"),
						new Choice("off", "-s off")
				)
		);
	}

	@Override
	public @NotNull String shortDescription() {
		return "Sets repeat on/off for the current queue or song.";
	}

	@Override
	public String helpText() {
		return """
				%s
								
				Usage:
					`repeat`
					`repeat -s [on|off]` # Sets repeat for current song.
					`repeat -q [on|off]` # Sets repeat for current queue.
				""";
	}

	@Override
	public void handle(CommandContext ctx) {
		boolean repeatQueue = GuildContext.get(ctx.getGuild().getId())
				.audioManager()
				.getScheduler()
				.isRepeatQueue();
		boolean repeatSong = GuildContext.get(ctx.getGuild().getId())
				.audioManager()
				.getScheduler()
				.isRepeatSong();

		List<String> args = ctx.getArgs();
		if (!args.isEmpty()) {
			if (args.get(0).equals("-s")) {
				if (args.get(1).equalsIgnoreCase("on")) {
					repeatSong = true;
				} else if (args.get(1).equalsIgnoreCase("off")) {
					repeatSong = true;
				}
			}else if (args.get(0).equals("-q")) {
				if (args.get(1).equalsIgnoreCase("on")) {
					repeatQueue = true;
				} else if (args.get(1).equalsIgnoreCase("off")) {
					repeatQueue = true;
				}
			}

			GuildContext.get(ctx.getGuild().getId())
					.audioManager()
					.getScheduler()
					.setRepeatQueue(repeatQueue);
			GuildContext.get(ctx.getGuild().getId())
					.audioManager()
					.getScheduler()
					.setRepeatSong(repeatSong);
		}

		String message = """
				Repeat Queue: `%s`
				Repeat Song : `%s`
				""".formatted(repeatQueue, repeatSong);
		ctx.getMessage()
				.getChannel()
				.sendMessage(message)
				.queue();
	}

}
