package com.zischase.discordbot.commands;

import com.github.ygimenez.exception.InvalidHandlerException;
import com.github.ygimenez.method.Pages;
import com.github.ygimenez.model.Page;
import com.github.ygimenez.model.Paginator;
import com.github.ygimenez.model.PaginatorBuilder;
import com.github.ygimenez.type.PageType;
import com.sun.istack.Nullable;
import com.zischase.discordbot.audioplayer.MediaControls;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.react.GenericGuildMessageReactionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class ResultSelector {

	/* Constants for defining behavior of searches */
	private static final int  MAX_SEARCHES_PER_GUILD        = 1;
	private static final long TIMEOUT_CHECK_STALE_LOCKS_SEC = 100;
	private static final Logger LOGGER = LoggerFactory.getLogger(ResultSelector.class);

	private final CompletableFuture<ISearchable> futureResult = new CompletableFuture<>();
	private final Semaphore                      semaphore    = new Semaphore(MAX_SEARCHES_PER_GUILD);
	private       Color                          embedColor   = Color.PINK;
	private final List<ISearchable>              searches;
	private final TextChannel                    textChannel;
	private final JDA                            jda;
	private final Member                         initiator;
	private       OffsetDateTime                 start;

	private final Paginator paginator;

	public ResultSelector(List<ISearchable> searches, TextChannel textChannel, JDA jda, Member initiator) throws InvalidHandlerException {
		this.searches    = searches;
		this.textChannel = textChannel;
		this.jda         = jda;
		this.initiator   = initiator;
//		new Timer().scheduleAtFixedRate(new TimerTask() {
//			@Override
//			public void run() {
//				if (OffsetDateTime.now().isAfter(start.plusSeconds(TIMEOUT_CHECK_STALE_LOCKS_SEC))) {
//					semaphore.release(MAX_SEARCHES_PER_GUILD);
//				}
//			}
//		}, TIMEOUT_CHECK_STALE_LOCKS_SEC, TIMEOUT_CHECK_STALE_LOCKS_SEC);

		paginator = PaginatorBuilder.createPaginator().setHandler(jda).build();
		Pages.activate(paginator);
		
		ArrayList<Page> pages = new ArrayList<>();
		MessageBuilder  mb    = new MessageBuilder();

		//ADDING 10 PAGES TO THE LIST
		for (int i = 0; i < 10; i++) {
			mb.clear();
			mb.setContent("This is entry Nº " + i);
			pages.add(new Page(mb.build()));
		}

		textChannel.sendMessage((Message) pages.get(0).getContent()).queue(success ->
				Pages.paginate(success, pages));
	}

	public ResultSelector(List<ISearchable> searches, TextChannel textChannel, JDA jda, Member initiator, Color color) throws InvalidHandlerException {
		this(searches, textChannel, jda, initiator);
		this.embedColor = color;
	}

	@Nullable
	public ISearchable get() {
		ISearchable result = null;
		start = OffsetDateTime.now();

//		EventWaiter waiter  = new EventWaiter();
//		Message     message = new MessageBuilder().append("Search Results").build();
//		message = textChannel.sendMessage(message).complete();
//
//		waiter.waitForEvent(GuildMessageReceivedEvent.class, this::checkValidEntry, this::selectEntry, TIMEOUT_CHECK_STALE_LOCKS_SEC, TimeUnit.SECONDS, semaphore::release);
//
//		waiter.waitForEvent(GenericGuildMessageReactionEvent.class, this::CheckUserStop, eventCallback -> semaphore.release());
//
//		Paginator.Builder builder = new Paginator.Builder()
//				.setText(initiator.getAsMention())
//				.setColor(embedColor)
//				.useNumberedItems(true)
//				.showPageNumbers(true)
//				.setColumns(1)
//				.waitOnSinglePage(true)
//				.setItemsPerPage(10)
//				.setUsers(initiator.getUser())
//				.setEventWaiter(waiter);
//
//		searches.forEach(s -> builder.addItems(s.getName()));
//
//		builder.build().display(message);
//		jda.addEventListener(waiter);


		try {
			semaphore.acquire();
			result = futureResult.get();
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}

//		jda.removeEventListener(waiter);
//		message.delete().queue((success) -> {/**/}, (err) -> LOGGER.warn("Message delete error - {}", err.getCause().toString()));

		semaphore.release();
		return result;
	}

	private boolean CheckUserStop(GenericGuildMessageReactionEvent event) {
		return event.getMember() == initiator && event.getReactionEmote().getName().equals(MediaControls.STOP);
	}

	private boolean checkValidEntry(GuildMessageReceivedEvent event) {
		return event.getMember() == initiator && event.getMessage().getContentDisplay().matches("(\\d+).?");
	}

	private void selectEntry(GuildMessageReceivedEvent event) {
		int num = Integer.parseInt(event.getMessage().getContentDisplay());
		if (num > 0 && num <= searches.size()) {
			futureResult.complete(searches.get(num - 1));
			semaphore.release();
		}
	}
}
