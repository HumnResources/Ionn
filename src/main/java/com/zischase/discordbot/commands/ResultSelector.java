package com.zischase.discordbot.commands;

import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import com.jagrosh.jdautilities.menu.Paginator;
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
import java.util.concurrent.TimeUnit;

public class ResultSelector extends ListenerAdapter {

	private final CompletableFuture<ISearchable> futureResult = new CompletableFuture<>();
	private final Semaphore                      semaphore    = new Semaphore(1);
//	private final Paginator.Builder              builder      = new Paginator.Builder();
//	private final EventWaiter                    waiter       = new EventWaiter();
	private final List<ISearchable>              searches;
	private final TextChannel                    textChannel;
	private final JDA                            jda;
	private final Member                         initiator;
	private       LocalDateTime                  start;

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
			semaphore.release();
		} else if (LocalDateTime.now().isAfter(start.plusSeconds(60))) {
			semaphore.release();
		}
	}

	public ISearchable getChoice() {
		ISearchable result = null;
		start = LocalDateTime.now();

		EventWaiter waiter = new EventWaiter();
		Message message = new MessageBuilder().setEmbeds(new EmbedBuilder().setTitle("Search Results").build()).build();
		message = textChannel.sendMessage(message).complete();

		Paginator.Builder builder = new Paginator.Builder()
				.setText("@"+initiator.getUser().getName())
				.setColor(Color.PINK)
				.useNumberedItems(true)
				.showPageNumbers(true)
				.setColumns(1)
				.waitOnSinglePage(true)
				.setItemsPerPage(10)
				.setUsers(initiator.getUser())
				.setTimeout(60, TimeUnit.SECONDS)
				.setEventWaiter(waiter);

		searches.forEach(s -> builder.addItems(s.getName()));

		builder.build().display(message);
		jda.addEventListener(this);
		jda.addEventListener(waiter);

		try {
			semaphore.acquire();
			result = futureResult.get();
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}

		message.delete().queue();

		jda.removeEventListener(this);
		jda.addEventListener(waiter);
		semaphore.release();
		return result;
	}
}
