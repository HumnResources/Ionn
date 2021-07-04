package com.zischase.discordbot.commands;

import com.zischase.discordbot.Config;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import javax.annotation.Nonnull;
import java.awt.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class ResultSelector {

	private final int                            searchDelayMS       = Integer.parseInt(Config.get("SEARCH_RESULT_DELAY_MS")); // Where delay is the duration until listener gets terminated.
	private final int                            searchDisplayTimeMS = Integer.parseInt(Config.get("SEARCH_RESULT_OFFSET_MS")) + searchDelayMS; // Where offset is approximate time to query result.
	private final CompletableFuture<ISearchable> futureResult        = new CompletableFuture<>();
	private final List<ISearchable>              searches;
	private final TextChannel                    textChannel;
	private final JDA                            jda;
	private final Member                         initiator;

	public ResultSelector(List<ISearchable> searches, TextChannel textChannel, JDA jda, Member initiator) {
		this.searches    = searches;
		this.textChannel = textChannel;
		this.jda         = jda;
		this.initiator   = initiator;
	}

	public ISearchable getChoice() {
		ISearchable result = null;


		final LocalDateTime start = LocalDateTime.now();
		ListenerAdapter response = new ListenerAdapter() {
			@Override
			public void onGuildMessageReceived(@Nonnull GuildMessageReceivedEvent tmpEvent) {
				if (tmpEvent.getMember() == initiator) {
					Message tmpMessage = tmpEvent.getMessage();
					String  choice     = tmpMessage.getContentDisplay();

					if (tmpMessage.getChannel() == textChannel && choice.matches("(\\d+).?")) {
						int num = Integer.parseInt(choice);
						if (num > 0 && num <= searches.size()) {
							futureResult.complete(searches.get(num - 1));
						}
					}
					jda.removeEventListener(this);

				} else if (LocalDateTime.now().isAfter(start.plusSeconds(searchDisplayTimeMS / 1000))) {
					jda.removeEventListener(this);
				}
			}
		};
		jda.addEventListener(response);
		printList();

		while (LocalDateTime.now().isBefore(start.plusSeconds(searchDisplayTimeMS / 1000))) {
			if (futureResult.isDone()) {
				try {
					result = futureResult.get();
				} catch (InterruptedException | ExecutionException e) {
					e.printStackTrace();
				}
				break;
			}
		}

		jda.removeEventListener(response);
		return result;
	}

	private void printList() {
		EmbedBuilder embed = new EmbedBuilder();
		embed.setColor(Color.DARK_GRAY);

		if (searches.isEmpty()) {
			embed.appendDescription("No results found !");
		} else {
			embed.setFooter("Don't take long!");
			String length = "";
			for (ISearchable result : searches) {
				embed.appendDescription((searches.indexOf(result) + 1) + ". `" + result.getName() + "`");
				embed.appendDescription(System.lineSeparator());

				length = length.concat((searches.indexOf(result) + 1) + ". `" + result.getName() + "`" + System.lineSeparator());

				if (length.length() >= 2000) {
					length = "";
					textChannel.sendMessageEmbeds(embed.build())
							.queue();
					embed = new EmbedBuilder();
					embed.setColor(Color.DARK_GRAY);
				}
			}
		}
		Message message = new MessageBuilder().setEmbeds(embed.build()).build();

		textChannel.sendMessage(message).queue((m) -> {
			while (LocalDateTime.now().isBefore(LocalDateTime.now().plusSeconds(searchDisplayTimeMS / 1000))) {
				if (futureResult.isDone()) {
					break;
				}
			}
			if (m != null) {
				m.delete().queue();
			}
		});
	}

}
