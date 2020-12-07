package com.zischase.discordbot.commands.general;

import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import net.dv8tion.jda.api.entities.Message;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Clear extends Command
{
	public Clear()
	{
		super(false);
	}
	
	@Override
	public String getHelp()
	{
		return "Clear [amount] ~ Deletes the last x messages from this channel. Default will purge";
	}
	
	@Override
	public void handle(CommandContext ctx)
	{
		int numOfMsgs = 0;
		
		if (ctx.getArgs()
			   .isEmpty())
		{
			numOfMsgs = Integer.MAX_VALUE;
		}
		else if (ctx.getArgs()
					.get(0)
					.matches("\\d+"))
		{
			numOfMsgs = Integer.parseInt(ctx.getArgs()
											.get(0));
		}
		
		
		ctx.getChannel()
		   .deleteMessageById(ctx.getEvent()
								 .getMessage()
								 .getId())
		   .complete();
		
		
		int delete = numOfMsgs;
		OffsetDateTime start = OffsetDateTime.now();

		while (numOfMsgs > 0)
		{
			if (delete >= 100)
			{
				delete = 100;
			}
			
			List<Message> messages = ctx.getChannel()
										.getHistory()
										.retrievePast(delete)
										.complete();
			
			messages.removeIf(message -> message.getTimeCreated().isBefore(OffsetDateTime.now().minusDays(14)));
			messages.removeIf(Message::isPinned);
			messages.removeIf(message -> message.getTimeCreated().isAfter(start));



			if (messages.size() == 1)
			{
				ctx.getChannel()
				   .deleteMessageById(messages.get(0)
											  .getId())
				   .complete();
				break;
			}
			else if (messages.isEmpty())
			{
				break;
			}
			
			ctx.getChannel()
			   .deleteMessages(messages)
			   .complete();
			numOfMsgs = numOfMsgs - delete;
			delete = numOfMsgs;
		}
		
		ctx.getChannel()
		   .sendMessage("Messages cleared !")
		   .complete()
		   .delete()
		   .queueAfter(2000, TimeUnit.MILLISECONDS);
	}
	
	
}
