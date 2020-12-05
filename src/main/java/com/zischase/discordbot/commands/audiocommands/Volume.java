package com.zischase.discordbot.commands.audiocommands;

import com.zischase.discordbot.Config;
import com.zischase.discordbot.DataBaseManager;
import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.guildcontrol.GuildManager;
import net.dv8tion.jda.api.entities.Guild;

import java.util.Arrays;
import java.util.List;

public class Volume extends Command
{
	private final int maxVolume     = Integer.parseInt(Config.get("MAX_VOLUME"));
	
	
	public Volume()
	{
		super(false);
	}
	
	public static void init(Guild guild)
	{
		String guildVol = DataBaseManager.get(guild.getId(), "volume");
		
		if (guildVol == null)
		{
			guildVol = Config.get("DEFAULT_VOLUME");
			DataBaseManager.update(guild.getId(), "volume", guildVol);
		}
		
		GuildManager.getContext(guild)
					.audioManager()
					.getPlayer()
					.setVolume(Integer.parseInt(guildVol));
	}
	
	@Override
	public List<String> getAliases()
	{
		return Arrays.asList("Vol", "V");
	}
	
	@Override
	public String getHelp()
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

			if (DataBaseManager.get(guild.getId(), "ispremium").equalsIgnoreCase("true"))
				max = 100;

			boolean validNum = (num >= 0 && num <= max);
			
			if (validNum)
			{
				setVolume(guild, String.valueOf(num));
				
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
	
	private void setVolume(Guild guild, String value)
	{
		DataBaseManager.update(guild.getId(), "volume", value);
		GuildManager.getContext(guild)
					.audioManager()
					.getPlayer()
					.setVolume(Integer.parseInt(value));
	}
	
	private String getVolume(Guild guild)
	{
		return String.valueOf(GuildManager.getContext(guild)
										  .audioManager()
										  .getPlayer()
										  .getVolume());
	}
}
