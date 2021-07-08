package com.zischase.discordbot.commands;

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
import java.util.concurrent.Semaphore;

public class ResultSelector extends ListenerAdapter {

	//	private final int                            searchDelayMS       = Integer.parseInt(Config.get("SEARCH_RESULT_DELAY_MS")); // Where delay is the duration until listener gets terminated.
//	private final int                            searchDisplayTimeMS = Integer.parseInt(Config.get("SEARCH_RESULT_OFFSET_MS")) + searchDelayMS; // Where offset is approximate time to query result.
	private final CompletableFuture<ISearchable> futureResult = new CompletableFuture<>();
	private final List<ISearchable>              searches;
	private final TextChannel                    textChannel;
	private final JDA                            jda;
	private final Member                         initiator;
	private final Semaphore                      semaphore    = new Semaphore(1);

	private LocalDateTime start;
	private Message       resultMessage;

	public ResultSelector(List<ISearchable> searches, TextChannel textChannel, JDA jda, Member initiator) {
		this.searches    = searches;
		this.textChannel = textChannel;
		this.jda         = jda;
		this.initiator   = initiator;
	}

	@Override
	public void onGuildMessageReceived(@Nonnull GuildMessageReceivedEvent tmpEvent) {
		if (tmpEvent.getMember() == initiator) {
			Message tmpMessage = tmpEvent.getMessage();
			String  choice     = tmpMessage.getContentDisplay();

			if (tmpMessage.getChannel() == textChannel && choice.matches("(\\d+).?")) {
				int num = Integer.parseInt(choice);
				if (num > 0 && num <= searches.size()) {
					futureResult.complete(searches.get(num - 1));
					semaphore.release();
				}
			}
			jda.removeEventListener(this);
			semaphore.release();
		} else if (LocalDateTime.now().isAfter(start.plusSeconds(60))) {
			jda.removeEventListener(this);
			semaphore.release();
		}
	}

	public ISearchable getChoice() {
		ISearchable result = null;
		start = LocalDateTime.now();
		jda.addEventListener(this);

		resultMessage = textChannel.sendMessage(printList()).complete();

		try {
			semaphore.acquire();
			result = futureResult.get();
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}

		resultMessage.delete().complete();

		jda.removeEventListener(this);
		semaphore.release();
		return result;
	}

	private Message printList() {
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
		return new MessageBuilder().setEmbeds(embed.build()).build();
	}

}
