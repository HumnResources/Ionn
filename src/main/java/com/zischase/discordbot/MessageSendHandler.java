package com.zischase.discordbot;

import kotlin.jvm.functions.Function3;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public class MessageSendHandler
{
	private final Semaphore  semaphore         = new Semaphore(7);
	private static final int SUBMIT_TIMEOUT_MS = 200;
	private static final int DELETE_TIMEOUT_MS = 5000;
	
	public BiFunction<TextChannel, MessageCreateData, Message> sendAndRetrieveMessage = this::sendAndRetrieveMessage;
	public BiConsumer<TextChannel, MessageCreateData> sendAndDeleteMessage = this::sendAndDeleteMessage;
	public BiConsumer<TextChannel, CharSequence> sendAndDeleteMessageChars = this::sendAndDeleteMessageChars;
	public BiConsumer<TextChannel, MessageCreateData> sendMessage = this::sendMessage;
	public Function3<TextChannel, Message, MessageEditData, Void> editMessage = this::editMessage;
	public Function3<TextChannel, Message, MessageEditData, Message> editAndRetrieveMessage = this::editAndRetrieveMessage;
	
	@Nullable
	private synchronized Message editAndRetrieveMessage(TextChannel t, Message m, MessageEditData data)
	{
		AtomicReference<Message> msg = new AtomicReference<>();
		awaitThread(() ->
		{
			if (m == null)
			{
				semaphore.release();
				return;
			}
			
			msg.set(t.editMessageById(m.getId(), data).complete());
			semaphore.release();
		});
		return msg.get();
	}
	
	@Nullable
	private synchronized Message sendAndRetrieveMessage(TextChannel t, MessageCreateData m)
	{
		AtomicReference<Message> msg = new AtomicReference<>();
		awaitThread(() ->
		{
			msg.set(t.sendMessage(m).complete());
			semaphore.release();
		});
		return msg.get();
	}
	
	private synchronized Void editMessage(TextChannel t, Message m, MessageEditData data)
	{
		awaitThread(() ->
		{
			if (m == null)
			{
				semaphore.release();
				return;
			}
			
			t.editMessageById(m.getId(), data).complete();
			semaphore.release();
		});
		return null;
	}
	
	private synchronized void sendMessage(TextChannel t, MessageCreateData m)
	{
		awaitThread(() ->
		{
			t.sendMessage(m).complete();
			semaphore.release();
		});
	}
	
	private synchronized void sendAndDeleteMessageChars(TextChannel t, CharSequence cs)
	{
		awaitThread(() ->
		{
			Message result = t.sendMessage(cs).complete();
			result.delete().completeAfter(DELETE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
			semaphore.release();
		});
	}
	private synchronized void sendAndDeleteMessage(TextChannel t, MessageCreateData m)
	{
		awaitThread(() ->
		{
			Message result = t.sendMessage(m).complete();
			result.delete().completeAfter(DELETE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
			semaphore.release();
		});
	}

	private void awaitThread(Runnable r)
	{
		Instant start = Instant.now();
		
		while (!semaphore.tryAcquire() && Instant.now().isBefore(start.plusMillis(SUBMIT_TIMEOUT_MS)))
		{
			if (Instant.now().isAfter(start.plusMillis(DELETE_TIMEOUT_MS)))
			{
				semaphore.release();
				return;
			}
		}
		
		r.run();
	}
}

