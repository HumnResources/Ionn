package com.zischase.discordbot;

import kotlin.jvm.functions.Function3;
import kotlin.jvm.functions.Function4;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public class MessageSendHandler
{
	private final        Semaphore semaphore         = new Semaphore(2);
	private static final int       SUBMIT_TIMEOUT_MS = 600;
	private static final int       DELETE_TIMEOUT_MS = 5000;
	
	public Function4<TextChannel, Message, Object, Boolean, Object> sendOrEditMessage         = this::sendOrEditMessage;
	public BiFunction<TextChannel, MessageCreateData, Message>      sendAndRetrieveMessage    = (t, data) -> sendOrEditMessage(t, null, data, false);
	public BiConsumer<TextChannel, MessageCreateData>               sendAndDeleteMessage      = (t, data) -> sendOrEditMessage(t, null, data, true);
	public BiConsumer<TextChannel, CharSequence>                    sendAndDeleteMessageChars = (t, data) -> sendOrEditMessage(t, null, data, true);
	public BiConsumer<TextChannel, MessageCreateData>               sendMessage               = (t, data) -> sendOrEditMessage(t, null, data, false);
	public Function3<TextChannel, Message, Object, Message>         editAndRetrieveMessage    = (t, m, data) -> sendOrEditMessage(t, m, data, false);
	public Function3<TextChannel, Message, MessageEditData, Void>   editMessage               = (t, m, data) -> { sendOrEditMessage(t, m, data, false); return null; };
	
	/* @Param{delete} consist of either options listed below */
	private Message sendOrEditMessage(TextChannel t, @Nullable Message oldMessage, Object data, boolean delete)
	{
		var ref = new Object()
		{
			MessageCreateData createData = null;
			MessageEditData editData = null;
			CharSequence charSequence = null;
			Message msg = null;
		};
		Runnable r;
		
		switch (data.getClass().getSimpleName())
		{
			case "MessageCreateData" -> ref.createData = (MessageCreateData) data;
			case "MessageEditData" -> ref.editData = (MessageEditData) data;
			case "String" -> ref.charSequence = (CharSequence) data;
			default ->
			{
				ref.msg = null;
				return null;
			}
		}
		
		r = () ->
		{
			if (ref.createData != null)
			{
				ref.msg = t.sendMessage(ref.createData).complete();
			}
			else if (ref.editData != null && oldMessage != null)
			{
				ref.msg = t.editMessageById(oldMessage.getId(), ref.editData).complete();
			}
			else
			{
				ref.charSequence = (CharSequence) data;
				if (oldMessage != null)
				{
					ref.msg = t.editMessageById(oldMessage.getId(), ref.charSequence).complete();
				}
				else
				{
					ref.msg = t.sendMessage(ref.charSequence).complete();
				}
			}
			
			deleteMessage(delete, t, ref.msg.getId());
			semaphore.release();
		};
		
		awaitThread(r);
		return ref.msg;
	}
	
	private void deleteMessage(boolean delete, TextChannel t, String messageID)
	{
		if (delete)
		{
			t.deleteMessageById(messageID).queueAfter(DELETE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
		}
	}
	
	private void awaitThread(Runnable r)
	{
		try
		{
			semaphore.acquire();
		} catch (InterruptedException e)
		{
			throw new RuntimeException(e);
		}
		
		Instant start = Instant.now();
		
		while (Instant.now().isBefore(start.plusMillis(SUBMIT_TIMEOUT_MS)))
		{
			if (Instant.now().isAfter(start.plusMillis(DELETE_TIMEOUT_MS)))
			{
				break;
			}
		}
		
		r.run();
	}
}

