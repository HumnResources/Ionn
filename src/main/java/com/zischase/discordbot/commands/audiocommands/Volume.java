package com.zischase.discordbot.commands.audiocommands;

import com.zischase.discordbot.Config;
import com.zischase.discordbot.DatabaseHandler;
import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.guildcontrol.GuildHandler;
import net.dv8tion.jda.api.entities.Guild;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class Volume extends Command
{
	private final int maxVolume     = Integer.parseInt(Config.get("MAX_VOLUME"));
	
	public Volume()
	{
		super(false);
	}
	
	@Override
	public List<String> getAliases()
	{
		return Arrays.asList("Vol", "V");
	}
	
	@Override
	public @NotNull String shortDescription() {
		return "Sets or displays the volume level.";
	}
	
	@Override
	public String helpText()
	{
		return "Volume [amount] ~ Sets the volume. 0-" + maxVolume + " | Leave empty to display current volume.";
	}
	
	@Override
	public void handle(CommandContext ctx)
	{
		Guild guild = ctx.getGuild();
		List<String> args = ctx.getArgs();
		
		if (args.isEmpty())
		{
			ctx.getEvent()
			   .getChannel()
			   .sendMessage("Volume is currently at: `" + getVolume(guild) + "`")
			   .queue();
			return;
		}
		
		if (args.get(0).matches("\\d+"))
		{
			int num = Integer.parseInt(args.get(0));
			int max = maxVolume;

			if (GuildHandler.getContext(guild).isPremium())
				max = 100;

			boolean validNum = (num >= 0 && num <= max);
			
			if (validNum)
			{
				setVolume(guild, num);
				
				ctx.getEvent()
				   .getChannel()
				   .sendMessage("The volume has been set to `" + getVolume(guild) + "`")
				   .queue();
				
				return;
			}
		}
		ctx.getEvent()
		   .getChannel()
		   .sendMessage("Please input a number between 0-" + maxVolume)
		   .queue();
	}
	
	private void setVolume(Guild guild, int value)
	{
		DatabaseHandler.update(guild.getId(), "volume", value);
		GuildHandler.getContext(guild)
                    .audioManager()
                    .getPlayer()
                    .setVolume(value);
	}
	
	private String getVolume(Guild guild)
	{
		return String.valueOf(GuildHandler.getContext(guild)
                                          .audioManager()
                                          .getPlayer()
                                          .getVolume());
	}
}
