package com.zischase.discordbot.audioplayer;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.zischase.discordbot.commands.audiocommands.Shuffle;
import com.zischase.discordbot.MessageSendHandler;
import com.zischase.discordbot.guildcontrol.GuildContext;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.MessageType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.react.GenericMessageReactionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.zischase.discordbot.audioplayer.MediaControls.*;

public class QueueMessageHandler extends ListenerAdapter
{
	
	private final AtomicReference<Message> queueMessage = new AtomicReference<>(null);
	private final List<MessageCreateData>  queuePages   = new ArrayList<>();
	private final AudioManager             manager;
	
	private final AtomicInteger queuePageNum = new AtomicInteger(0);
	
	public QueueMessageHandler(AudioManager manager)
	{
		this.manager = manager;
	}
	
	@Override
	public void onGenericMessageReaction(@NotNull GenericMessageReactionEvent event)
	{
		Message qMessage     = this.queueMessage.get();
		Message eventMessage = event.retrieveMessage().complete();
		
		if (qMessage == null)
		{
			return;
		}
		
		MessageReaction reaction = eventMessage.getReaction(event.getEmoji());
		
		/* Validates message and user entry */
		if (qMessage.getIdLong() != eventMessage.getIdLong() || event.getMember() == null || event.getMember().getUser().isBot() || (reaction != null && reaction.getCount() <= 1))
		{
			return;
		}
		
		String      reactionName = event.getReaction().getEmoji().getName();
		TextChannel textChannel  = event.getChannel().asTextChannel();
		
		switch (reactionName)
		{
			case SHUFFLE ->
			{
				Shuffle.shuffle(event.getGuild().getId(), manager);
				printQueuePage(textChannel, getCurrentPageNum());
			}
			case REPEAT_QUEUE ->
			{
				manager.getScheduler().setRepeatQueue(!manager.getScheduler().isRepeatQueue());
				printQueuePage(textChannel, getCurrentPageNum());
			}
			case REVERSE -> printQueuePage(textChannel, Math.max(getCurrentPageNum() - 1, 0));
			case PREV_TRACK -> printQueuePage(textChannel, 0);
			case PLAY -> printQueuePage(textChannel, Math.min(getCurrentPageNum() + 1, getMaxPages()));
			case NEXT_TRACK -> printQueuePage(textChannel, getMaxPages());
			case STOP -> manager.getScheduler().clearQueue();
			
		}
		
		qMessage.removeReaction(Emoji.fromFormatted(reactionName), Objects.requireNonNull(event.getUser())).queue();

//		for (MessageReaction r : eventMessage.getReactions())
//		{
//			for (User u : r.retrieveUsers().complete())
//			{
//				if (event.getJDA().getSelfUser() == u)
//				{
//					continue;
//				}
//				eventMessage.removeReaction(event.getEmoji(), u).queue();
//			}
//		}
		
		/* Sleep thread to add all reactions / prevent overflow in responses */
		try
		{
			Thread.sleep(REACTION_TIMEOUT_MS);
		} catch (InterruptedException e)
		{
			e.printStackTrace();
		}
	}
	
	public synchronized void printQueuePage(@NotNull TextChannel textChannel, int pageNum)
	{
		if (pageNum < 0 || pageNum > getMaxPages())
		{
			return;
		}
		List<Message> messages = textChannel.getHistory().retrievePast(HISTORY_POLL_LIMIT).complete();
		Message message = getQueueMsg(messages);
		buildQueue(manager.getScheduler().getQueue());
		setPageNum(pageNum - 1);
		
		MessageSendHandler messageSendHandler = GuildContext.get(textChannel.getGuild().getId()).messageSendHandler();
		
		if (message == null)
		{
			messageSendHandler.sendAndRetrieveMessage.apply(textChannel, queuePages.get(0));
		}
		else
		{
			messageSendHandler.editAndRetrieveMessage.invoke(textChannel, message, MessageEditData.fromCreateData(queuePages.get(this.queuePageNum.get())));
		}
		
		this.queueMessage.set(message);
		addReactions(message);
	}
	
	public int getCurrentPageNum()
	{
		if (this.queuePageNum.get() > getMaxPages())
		{
			this.queuePageNum.set(getMaxPages() - 1);
		}
		
		return this.queuePageNum.get() + 1;
	}
	
	private int getMaxPages()
	{
		if (manager.getScheduler().getQueue().isEmpty())
		{
			return 1;
		}
		
		return (int) Math.ceil(((float) manager.getScheduler().getQueue().size()) / QUEUE_PAGE_SIZE);
	}
	
	@Nullable
	private Message getQueueMsg(List<Message> messages)
	{
		return messages.stream()
				.filter(msg -> msg.getAuthor().isBot())
				.filter(msg -> msg.getAuthor().getId().equals(msg.getJDA().getSelfUser().getId()))
				.filter(msg -> !msg.isPinned())
				.filter(msg -> msg.getTimeCreated().isBefore(OffsetDateTime.now()))
				.filter(msg -> msg.getTimeCreated().isAfter(OffsetDateTime.now().minusDays(14)))
				.filter(msg -> msg.getContentDisplay().contains(QUEUE_MSG_NAME))
				.findFirst()
				.flatMap(message ->
				{
					this.queueMessage.set(message);
					return Optional.of(message);
				})
				.orElse(null);
	}
	
	private void buildQueue(@NotNull List<AudioTrack> queue)
	{
		EmbedBuilder eb        = new EmbedBuilder();
		int          size      = queue.size();
		int          pageCount = 1;
		String       repeat    = manager.getScheduler().isRepeatQueue() ? "- ".concat(REPEAT_QUEUE) : "";
		MessageCreateBuilder messageCreateBuilder = new MessageCreateBuilder();
		queuePages.clear();
		
		
		if (size == 0)
		{
			eb.setColor(Color.cyan);
			eb.appendDescription("Empty...");
			eb.setFooter("Page: %d/%d - Tracks: %d %s".formatted(pageCount, getMaxPages(), size, repeat));
			queuePages.add(new MessageCreateBuilder().addContent(QUEUE_MSG_NAME).setEmbeds(eb.build()).build());
			return;
		}
		
		long runtime = (queue.stream()
				.mapToLong(audioTrack -> audioTrack.getInfo().length / 1000)
				.sum()) + (manager.getPlayer().getPlayingTrack().getInfo().length / 1000);
		
		String runtimeFormatted = String.format("%d:%02d:%02d", runtime / 3600, (runtime % 3600) / 60, (runtime % 60));
		
		for (int i = 1; i <= size; i++)
		{
			eb.setColor(Color.WHITE);
			eb.appendDescription("`%d.` %s\n".formatted(i, queue.get(i - 1).getInfo().title));
			
			/* Starts a new page or adds last one */
			if (i % QUEUE_PAGE_SIZE == 0 || i == size)
			{
				eb.setFooter("Page: %d/%d - Tracks: %d - Runtime: %s %s".formatted(pageCount, getMaxPages(), size, runtimeFormatted, repeat));
				pageCount++;
				queuePages.add(messageCreateBuilder.addContent(QUEUE_MSG_NAME).setEmbeds(eb.build()).build());
				eb.clear();
				messageCreateBuilder.clear();
			}
		}
	}
	
	private void setPageNum(int num)
	{
		if (num > getMaxPages())
		{
			this.queuePageNum.set(getMaxPages());
		}
		else
		{
			this.queuePageNum.set(Math.max(num, 0));
		}
	}
	
	private void addReactions(Message queueMessage)
	{
		/* Small wait to allow printer to display song info if needed */
		
		if (queueMessage == null || queueMessage.getType() == MessageType.UNKNOWN)
		{
			return;
		}
		
		List<String> reactionsPresent = queueMessage.getReactions()
				.stream()
				.map(reaction -> reaction.getEmoji().getName())
				.collect(Collectors.toList());
		
		/* Only add a reaction if it's missing. Saves on queues submit to discord API */
		for (String reaction : MediaControls.getQueueReactions())
		{
			if (!reactionsPresent.contains(reaction))
			{
				queueMessage.addReaction(Emoji.fromFormatted(reaction)).submit();
			}
		}
	}
	
	public void printQueue(@NotNull TextChannel textChannel)
	{
		printQueuePage(textChannel, 0);
	}
}
