package com.zischase.discordbot;

import kotlin.jvm.functions.Function3;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public class MessageSendHandler
{
	private final Semaphore  semaphore         = new Semaphore(7);
	private static final int SUBMIT_TIMEOUT_MS = 350;
	private static final int DELETE_TIMEOUT_MS = 5000;
	
	public BiFunction<TextChannel, MessageCreateData, Message> sendAndRetrieveMessage = this::sendAndRetrieveMessage;
	public BiConsumer<TextChannel, MessageCreateData> sendAndDeleteMessage = this::sendAndDeleteMessage;
	public BiConsumer<TextChannel, CharSequence> sendAndDeleteMessageChars = this::sendAndDeleteMessageChars;
	public BiConsumer<TextChannel, MessageCreateData> sendMessage = this::sendMessage;
	public Function3<TextChannel, Message, MessageEditData, Void> editMessage = this::editMessage;
	public Function3<TextChannel, Message, MessageEditData, Message> editAndRetrieveMessage = this::editAndRetrieveMessage;
	
	private synchronized Message editAndRetrieveMessage(TextChannel t, Message m, MessageEditData data)
	{
		awaitThread();
		if (m == null)
		{
			semaphore.release();
			return null;
		}
		
		Message msg = t.editMessageById(m.getId(), data).complete();
		semaphore.release();
		return msg;
	}
	
	private synchronized Message sendAndRetrieveMessage(TextChannel t, MessageCreateData m)
	{
		awaitThread();
		Message msg = t.sendMessage(m).complete();
		semaphore.release();
		return msg;
	}
	
	private synchronized Void editMessage(TextChannel t, Message m, MessageEditData data)
	{
		awaitThread();
		if (m == null)
		{
			semaphore.release();
			return null;
		}
		
		t.editMessageById(m.getId(), data).complete();
		semaphore.release();
		return null;
	}
	
	private synchronized void sendMessage(TextChannel t, MessageCreateData m)
	{
		awaitThread();
		t.sendMessage(m).complete();
		semaphore.release();
	}
	
	private synchronized void sendAndDeleteMessageChars(TextChannel t, CharSequence cs)
	{
		awaitThread();
		Message result = t.sendMessage(cs).complete();
		result.delete().completeAfter(DELETE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
		semaphore.release();
	}
	private synchronized void sendAndDeleteMessage(TextChannel t, MessageCreateData m)
	{
		awaitThread();
		Message result = t.sendMessage(m).complete();
		result.delete().completeAfter(DELETE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
		semaphore.release();
	}
	
	private synchronized void awaitThread()
	{
		/* Backup release for thread in case - slightly higher time to allow thread chance to solve */
		CompletableFuture.runAsync(() -> {
			Instant start = Instant.now();
			while (start.isBefore(Instant.now().plusMillis(SUBMIT_TIMEOUT_MS)))
			{
				/* This condition means the thread was successful at waiting and no longer needs additional timeout.
				 return to avoid releasing a newly acquired thread */
				if (semaphore.availablePermits() != 1)
				{
					return;
				}
			}
			semaphore.release();
		});
		
		try
		{
			this.semaphore.acquire();
		} catch (InterruptedException e)
		{
			throw new RuntimeException(e);
		}
	}
}

