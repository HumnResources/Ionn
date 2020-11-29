package com.zischase.discordbot.commands.general;

import com.zischase.discordbot.DataBaseManager;
import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.commands.CommandManager;
import com.zischase.discordbot.guildcontrol.GuildManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;

import java.awt.*;
import java.util.List;

public class Help extends Command
{
	
	
	public Help()
	{
		super(false);
	}
	
	@Override
	public String getName()
	{
		return "Help [command]";
	}
	
	private MessageEmbed printCommandList(Guild guild)
	{
		EmbedBuilder cmdList = new EmbedBuilder();
		cmdList.setColor(Color.ORANGE);
		cmdList.setTitle("Commands");
		String prefix = Prefix.getPrefix(guild);

		cmdList.appendDescription(String.format("The current prefix is set to: `%s`\n", prefix));
		
		CommandManager.getCommandList()
					  .forEach(command ->
					  {
						  if ((! command.premiumCommand) || (GuildManager.getContext(guild)
																		 .isPremium()))
						  {
							  cmdList.appendDescription(String.format("`%s%s`\n", prefix, command.getName()));
						  }
					  });
		
		return cmdList.build();
	}
	
	@Override
	public String getHelp()
	{
		return "Help [command] ~ Get help about a specific command.";
	}
	
	@Override
	public void handle(CommandContext ctx)
	{
		TextChannel channel = ctx.getChannel();
		List<String> args = ctx.getArgs();
		
		if (args.isEmpty())
		{
			channel.sendMessage(printCommandList(ctx.getGuild()))
				   .queue();
			return;
		}
		else if (args.size() >= 1 && args.get(0).matches("(?i)audio | media | music"))
		{
			channel.sendMessage(DataBaseManager.get(ctx.getGuild().getId(), "PREFIX") +
					"youtube\n" +
					"`Youtube [Search Query] : Search youtube for a song. Then adds it to the queue`\\n\" + \"`Youtube -[search|s] : Provides a list of songs. Reply with a number to choose.`\\n" +
					"play\n" +
					"`Play/Pause : Play or pause the player.`\n" + "`Play [url] : Adds the specified song/playlist to queue.`\n" + "`Play -[next|n] [url] : Adds the specified song/playlist to next in queue`")
					.queue();
		}
		else
		{
			String cmdSearch = args.get(0);
			Command command = CommandManager.getCommand(cmdSearch);

			if (command == null)
			{
				channel.sendMessage("Command " + cmdSearch + " not found.")
						.queue();
				return;
			}
			channel.sendMessage(command.getHelp())
					.queue();
		}
	}
}
