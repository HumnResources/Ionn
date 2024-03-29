package com.zischase.discordbot.commands.audiocommands;

import com.zischase.discordbot.DBQuery;
import com.zischase.discordbot.DBQueryHandler;
import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.MessageSendHandler;
import com.zischase.discordbot.guildcontrol.GuildContext;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class Volume extends Command
{
	
	private final AtomicInteger maxVol = new AtomicInteger(-1);
	
	public Volume()
	{
		super(false);
	}
	
	@Override
	public SlashCommandData getCommandData()
	{
		return super.getCommandData().addOptions(new OptionData(OptionType.INTEGER, "num", "New volume level."));
	}
	
	@Override
	public @NotNull String shortDescription()
	{
		return "Sets or displays the volume level.";
	}
	
	@Override
	public List<String> getAliases()
	{
		return Arrays.asList("Vol", "V");
	}
	
	@Override
	public String helpText()
	{
		return "Volume [amount] ~ Sets the volume. 0-" + maxVol.get() + " | Leave empty to display current volume.";
	}
	
	@Override
	public void handle(CommandContext ctx)
	{
		String       guildID = ctx.getGuild().getId();
		List<String> args    = ctx.getArgs();
		MessageSendHandler messageSendHandler = GuildContext.get(guildID).messageSendHandler();
		
		if (this.maxVol.get() == -1)
		{
			if (ctx.isPremiumGuild())
				this.maxVol.set(100);
			else
				this.maxVol.set(25);
		}
		
		if (args.isEmpty())
		{
			messageSendHandler.sendAndDeleteMessageChars.accept(ctx.getChannel(), "Volume is currently at: `" + getVolume(guildID) + "`");
			return;
		}
		
		if (args.get(0).matches("\\d+"))
		{
			int     num      = Integer.parseInt(args.get(0));
			boolean validNum = (num >= 0 && num <= maxVol.get());
			if (validNum)
			{
				setVolume(guildID, num);
				
				messageSendHandler.sendAndDeleteMessageChars.accept(ctx.getChannel(), "The volume has been set to `" + num + "`");
				return;
			}
		}
		
		messageSendHandler.sendAndDeleteMessageChars.accept(ctx.getChannel(), "Please input a number between 0-" + maxVol.get());
	}
	
	private String getVolume(String guildID)
	{
		return String.valueOf(GuildContext.get(guildID)
				.audioManager()
				.getPlayer()
				.getVolume());
	}
	
	private void setVolume(String guildID, int value)
	{
		GuildContext.get(guildID)
				.audioManager()
				.getScheduler()
				.setVolume(value);
		DBQueryHandler.set(guildID, DBQuery.VOLUME, value);
	}
	
}
