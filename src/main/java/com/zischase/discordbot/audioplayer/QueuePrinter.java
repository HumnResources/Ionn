package com.zischase.discordbot.audioplayer;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.zischase.discordbot.commands.audiocommands.Shuffle;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageType;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.guild.react.GenericGuildMessageReactionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.zischase.discordbot.audioplayer.MediaControls.*;

public class QueuePrinter extends ListenerAdapter {

	private final Semaphore                reactionTimeoutSemaphore = new Semaphore(1);
	private final Semaphore                queueSemaphore           = new Semaphore(1);
	private final AtomicReference<Message> queueMessage             = new AtomicReference<>(null);
	private final AudioManager             manager;

	private       int           queuePageNum = 0;
	private final List<Message> queuePages   = new ArrayList<>();

	public QueuePrinter(AudioManager manager) {
		this.manager = manager;
	}

	@Override
	public void onGenericGuildMessageReaction(@NotNull GenericGuildMessageReactionEvent event) {
		Member eventMember = event.getMember();
		if (eventMember == null || eventMember.getUser().isBot() || event.getReaction().isSelf()) {
			return;
		}
		Message eventMessage = event.retrieveMessage().complete();
		Message queueMessage = this.queueMessage.get();

		if (queueMessage != null && eventMessage.getId().equals(queueMessage.getId())) {
			if (reactionTimeoutSemaphore.tryAcquire()) {
				String reaction = event.getReaction().getReactionEmote().getName();
				switch (reaction) {
					case SHUFFLE -> Shuffle.shuffle(event.getGuild().getId(), manager);
					case REPEAT_QUEUE -> manager.getScheduler().setRepeatQueue(!manager.getScheduler().isRepeatQueue());
					case PREV -> printQueuePage(event.getChannel(), Math.max(getCurrentPageNum() - 1, 0)); // Subtract 2 due to 1 based numeration, then page offset of 1
					case NEXT -> {
						if (getCurrentPageNum() + 1 >= getMaxPages()) {
							printQueuePage(event.getChannel(), getMaxPages() - 1);
						}
						else {
							printQueuePage(event.getChannel(), getCurrentPageNum() + 1);
						}
					}
					case STOP -> manager.getScheduler().clearQueue();
				}
				try {
					Thread.sleep(REACTION_TIMEOUT_MS);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				reactionTimeoutSemaphore.release();
			}
		}
	}

	public void printQueue(@NotNull TextChannel textChannel) {
		printQueuePage(textChannel, 0);
	}

	public void printQueuePage(@NotNull TextChannel textChannel, int pageNum) {
		if (!queueSemaphore.tryAcquire()) {
			return;
		} else if (manager.getScheduler().getQueue().isEmpty() || pageNum < 0 || pageNum > getMaxPages()) {
			this.queueSemaphore.release();
			return;
		}

		Message message = getQueueMsg(textChannel.getHistory().retrievePast(HISTORY_POLL_LIMIT).complete());
		buildQueue(manager.getScheduler().getQueue());
		setPageNum(pageNum);

		if (message == null) {
			textChannel.sendMessage(queuePages.get(queuePageNum)).queue(msg -> {
						addReactions(msg);
						this.queueMessage.set(msg);
					});
		} else {
			textChannel.editMessageById(message.getId(), queuePages.get(queuePageNum))
					.queue(this.queueMessage::set);
		}

		try {
			Thread.sleep(PRINT_TIMEOUT_MS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		queueSemaphore.release();
	}

	public int getCurrentPageNum() {
		return this.queuePageNum;
	}

	private void setPageNum(int num) {
		if (num > getMaxPages()) {
			this.queuePageNum = getMaxPages();
		}
		else {
			this.queuePageNum = Math.max(num, 0);
		}
	}

	private int getMaxPages() {
		if (manager.getScheduler().getQueue().isEmpty()) {
			return 0;
		}

		return (int) Math.ceil(((float) manager.getScheduler().getQueue().size()) / QUEUE_PAGE_SIZE);
	}

	private void buildQueue(@NotNull List<AudioTrack> queue) {
		EmbedBuilder  eb        = new EmbedBuilder();
		int           size      = queue.size();
		int           pageCount = 1;
		queuePages.clear();

		for (int i = 0; i < size; i++) {
			eb.setColor(Color.WHITE);
			eb.appendDescription("`%d.` %s\n".formatted(i + 1, queue.get(i).getInfo().title));

			/* Starts a new page or adds last one */
			if (i != 0 && i % QUEUE_PAGE_SIZE == 0 || i == size - 1) {
				eb.setFooter("Page: %d/%d - Songs: %d".formatted(pageCount, getMaxPages(), size));
				pageCount++;
				queuePages.add(new MessageBuilder().append(QUEUE_MSG_NAME).setEmbeds(eb.build()).build());
				eb.clear();
			}
		}

	}

	@Nullable
	private Message getQueueMsg(List<Message> messages) {
		return messages.stream()
				.filter(msg -> msg.getAuthor().isBot())
				.filter(msg -> msg.getAuthor().getId().equals(msg.getJDA().getSelfUser().getId()))
				.filter(msg -> !msg.isPinned())
				.filter(msg -> msg.getTimeCreated().isBefore(OffsetDateTime.now()))
				.filter(msg -> msg.getTimeCreated().isAfter(OffsetDateTime.now().minusDays(14)))
				.filter(msg -> msg.getContentDisplay().contains(QUEUE_MSG_NAME))
				.findFirst()
				.flatMap(message -> {
					this.queueMessage.set(message);
					return Optional.of(message);
				})
				.orElse(null);
	}

	private void addReactions(Message queueMessage) {
		/* Small wait to allow printer to display song info if needed */

		if (queueMessage == null || queueMessage.getType() == MessageType.UNKNOWN) {
			return;
		}

		List<String> reactionsPresent = queueMessage.getReactions()
				.stream()
				.map(reaction -> reaction.getReactionEmote().getName())
				.collect(Collectors.toList());

		/* Only add a reaction if it's missing. Saves on queues submit to discord API */
		for (String reaction : MediaControls.getQueueReactions()) {
			if (!reactionsPresent.contains(reaction)) {
				queueMessage.addReaction(reaction).submit();
			}
		}
	}
}
