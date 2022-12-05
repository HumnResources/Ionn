package com.zischase.discordbot.commands;

import com.github.ygimenez.exception.InvalidHandlerException;
import com.github.ygimenez.method.Pages;
import com.github.ygimenez.model.Page;
import com.github.ygimenez.model.Paginator;
import com.github.ygimenez.model.PaginatorBuilder;
import com.sun.istack.NotNull;
import com.sun.istack.Nullable;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.awt.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

public class ResultSelector {

	/* Constants for defining behavior of searches */
	private static final int    MAX_SEARCHES_PER_GUILD    = 1;
	private static final long   TIMEOUT_SEC               = 100;
	private static final int    PAGE_SIZE                 = 12;
	private static final String SEARCH_RESULT_EMBED_TITLE = "Search Results";

	private final CompletableFuture<ISearchable> futureResult  = new CompletableFuture<>();
	private final List<Page>                     pages         = new ArrayList<>();
	private final CountDownLatch                  latch         = new CountDownLatch(MAX_SEARCHES_PER_GUILD);
	private final JDA                            jda;
	private final Member                         initiator;
	private final List<ISearchable>              searches;
	private       OffsetDateTime                 start         = OffsetDateTime.now();
	private       Color                         embedColor    = Color.PINK;
	private       Message                        resultMessage = null;

	public ResultSelector(List<ISearchable> searches, TextChannel textChannel, JDA jda, Member initiator, Color color) throws InvalidHandlerException {
		this(searches, textChannel, jda, initiator);
		this.embedColor = color;
	}

	public ResultSelector(@NotNull List<ISearchable> searches, TextChannel textChannel, JDA jda, Member initiator) throws InvalidHandlerException {
		this.searches  = searches;
		this.jda       = jda;
		this.initiator = initiator;
		Paginator paginator = PaginatorBuilder
				.createPaginator()
				.setHandler(jda)
				.shouldEventLock(true)
				.setDeleteOnCancel(true)
				.build();

		if (!Pages.isActivated()) {
			Pages.activate(paginator);
		}

		/*
		 * Set timeout timer to avoid persistent thread locking
		 */
		new Timer().scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				if (OffsetDateTime.now().isAfter(start.plusSeconds(TIMEOUT_SEC))) {
					latch.countDown();
				}
			}
		}, TIMEOUT_SEC, TIMEOUT_SEC);

		EmbedBuilder eb = new EmbedBuilder();
		eb.setTitle(SEARCH_RESULT_EMBED_TITLE)
				.setColor(embedColor)
				.setAuthor(initiator.getEffectiveName());

		if (!Pages.isActivated()) {
			Pages.activate(new Paginator(jda));
		}

		Page page;
		int  pageCount = 1;
		for (int i = 0; i < searches.size(); i++) {
			eb.appendDescription("`%d.` %s\n".formatted(i + 1, searches.get(i).getName()));

			/* Starts a new page or adds last one */
			if (i != 0 && i % PAGE_SIZE == 0 || i == searches.size() - 1) {
				eb.setFooter("Page %d/%d".formatted(pageCount, (int) Math.ceil((0.0d + searches.size()) / PAGE_SIZE)));
				pageCount++;
				page = new Page(new MessageBuilder().setEmbeds(eb.build()).build());
				pages.add(page);
				eb.clear();
				eb.setTitle(SEARCH_RESULT_EMBED_TITLE)
						.setColor(embedColor)
						.setAuthor(initiator.getEffectiveName());
			}
		}

		textChannel.sendMessage((Message) pages.get(0).getContent()).queue(success -> {
			Pages.paginate(success, pages);
			resultMessage = success;
		});
	}

	@Nullable
	public ISearchable awaitForResult() throws InterruptedException, ExecutionException {
		ISearchable result;
		start = OffsetDateTime.now();
		ListenerAdapter resultListener = new ListenerAdapter() {
			@Override
			public void onGuildMessageReceived(@org.jetbrains.annotations.NotNull GuildMessageReceivedEvent event) {
				if (checkValidEntry(event)) {
					selectEntry(event);
					event.getMessage().delete().submit();
				} else if (event.getMessage().getAuthor() == initiator.getUser()) {
					latch.countDown();
				}
			}
		};

		jda.addEventListener(resultListener);

		latch.await();

		result = futureResult.get();

		resultMessage.delete().queue();
		jda.removeEventListener(resultListener);
		latch.countDown();
		return result;
	}

	private boolean checkValidEntry(GuildMessageReceivedEvent event) {
		return event.getMember() == initiator && event.getMessage().getContentDisplay().matches("(\\d+).?");
	}

	private void selectEntry(GuildMessageReceivedEvent event) {
		int num = Integer.parseInt(event.getMessage().getContentDisplay());
		if (num > 0 && num <= searches.size()) {
			futureResult.complete(searches.get(num - 1));
			latch.countDown();
		}
	}
}
