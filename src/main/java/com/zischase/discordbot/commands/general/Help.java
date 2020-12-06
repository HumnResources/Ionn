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
		
		CommandManager.getCommandList().forEach(command ->
					  {
						  if (! command.premiumCommand || GuildManager.getContext(guild).isPremium())
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
		}
		else if (args.get(0).matches("(?i)(-audio | -media | -music)"))
		{
			String prefix = DataBaseManager.get(ctx.getGuild().getId(), "PREFIX");
			channel.sendMessage(String.format("""
     				```
     				Prepend the prefix before the command ! `%s`
     				`YouTube [Search...] : Searches YouTube for the specified song and adds it to the queue. `
     				`YouTube [-search | -s] [Search...] : Searches YouTube for specified song and displays a list to choose.`
     				
     				`Shuffle` : Shuffles the current queue.*
     				`Repeat` : Displays current repeat state.*
     				`Repeat [-on | -off]` : Toggles repeat for the current queue.*
     				`Stop` : Clears the queue and has the bot leave the channel.
     				`Join` : Summons the bot to the channel.
     				`skip/next` : Plays the next song in the queue.
     				`NowPlaying/np` : Shows currently playing track.
     				`Pause/P` : Pauses/Resumes the currently playing track. 
     				
     				`Queue/Q` : Displays the currently playing song and remaining queue.
     				`Queue [-JumpTo | -Jump | -J] [queue position]` : Skips the queue to specified position. Maintaining play order.
     				`Queue [-Next | -N] [queue position]` : Moves the specified song to the next in queue.
     				
     				`Play` [url] : Adds the specified song/playlist to the queue from a provided link.
     				`Play [-next | -n] [url]` : Adds the specified song/playlist to the next in queue. 
     					Note: Currently playlist support is limited to YouTube playlist links only.
     					
     				`Volume/V` : Displays the current volume.
     				`Volume [0-25]` : Sets the volume to the specified level. Premium Users: [0-100]*
     				
     				`Playlist/Pl/Plist` : Displays a list of currently made playlists.*
     				`Playlist [-Current | -C | -Queue | -Q]` : Creates a playlist of the current queue.*
     				`Playlist [-Delete | -D | -Remove | -R] [Name...] : Deletes the playlist with the specified name.*
     				`Playlist [-add | -new] [Name...]` : Adds a new playlist with the specified name.*
     				`Playlist [-Play | -P] [Name...]` : Loads the prefix with the specified name.*
     						Note: Active development and the playlists are currently not persistent. Use at your own risk.
     				
     				*Premium Features.
     				```   	
					""", prefix))
			.queue();


//			channel.sendMessage(DataBaseManager.get(ctx.getGuild().getId(), "PREFIX") +
//					"youtube\n" +
//					"`Youtube [Search Query] : Search youtube for a song. Then adds it to the queue`\\n\" + \"`Youtube -[search|s] : Provides a list of songs. Reply with a number to choose.`\\n" +
//					"play\n" +
//					"`Play/Pause : Play or pause the player.`\n" + "`Play [url] : Adds the specified song/playlist to queue.`\n" + "`Play -[next|n] [url] : Adds the specified song/playlist to next in queue`")
//					.queue();
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
