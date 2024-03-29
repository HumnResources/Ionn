package com.zischase.discordbot.commands.general;

import com.zischase.discordbot.DBQuery;
import com.zischase.discordbot.DBQueryHandler;
import com.zischase.discordbot.MessageSendHandler;
import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.guildcontrol.GuildContext;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class Prefix extends Command
{
	
	public Prefix()
	{
		super(false);
	}
	
	@Override
	public SlashCommandData getCommandData()
	{
		return super.getCommandData().addOptions(new OptionData(OptionType.STRING, "set", "sets new prefix"));
	}
	
	@Override
	public @NotNull String shortDescription()
	{
		return "Sets the prefix to use for commands.";
	}
	
	@Override
	public String helpText()
	{
		return "Prefix [newPrefix] ~ Sets new prefix for commands.";
	}
	
	@Override
	public void handle(CommandContext ctx)
	{
		Guild        guild = ctx.getGuild();
		List<String>       args               = ctx.getArgs();
		MessageSendHandler messageSendHandler = GuildContext.get(ctx.getGuild().getId()).messageSendHandler();
		String             prefix             = DBQueryHandler.get(guild.getId(), DBQuery.PREFIX);
		
		if (args.isEmpty())
		{
			messageSendHandler.sendAndDeleteMessageChars.accept(ctx.getChannel(), "The current prefix is `" + prefix + "`");
			return;
		}
		
		DBQueryHandler.set(guild.getId(), DBQuery.PREFIX, args.get(0));
		prefix = DBQueryHandler.get(guild.getId(), DBQuery.PREFIX);
		
		messageSendHandler.sendAndDeleteMessageChars.accept(ctx.getChannel(), "The new prefix has been set to `" + prefix + "`");
	}
}
