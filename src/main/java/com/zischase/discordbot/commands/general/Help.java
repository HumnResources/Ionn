package com.zischase.discordbot.commands.general;

import com.zischase.discordbot.DBQueryHandler;
import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.commands.CommandHandler;
import com.zischase.discordbot.guildcontrol.GuildContext;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;

public class Help extends Command {

	public Help() {
		super(false);
	}

	@Override
	public @NotNull String shortDescription() {
		return "Displays list of commands.";
	}

	@Override
	public String helpText() {
		return null;
	}

	@Override
	public void handle(CommandContext ctx) {
		TextChannel  channel = ctx.getChannel();
		List<String> args    = ctx.getArgs();

		if (args.isEmpty()) {
			channel.sendMessageEmbeds(printCommandList(ctx.getGuild().getId()))
					.queue();
		} else if (args.get(0).matches("(?i)(audio|media|music)")) {
			EmbedBuilder embedBuilder = new EmbedBuilder();
			embedBuilder.appendDescription(getAudioHelp());
			embedBuilder.setColor(Color.magenta);

			channel.sendMessageEmbeds(embedBuilder.build()).queue();
		} else {
			String  cmdSearch = args.get(0);
			Command command   = GuildContext.get(ctx.getGuild().getId()).commandHandler().getCommand(cmdSearch);

			if (command == null) {
				channel.sendMessage("Command " + cmdSearch + " not found.")
						.queue();
			} else {
				channel.sendMessage(command.helpText())
						.queue();
			}
		}
	}

	private MessageEmbed printCommandList(String guildID) {
		EmbedBuilder cmdList = new EmbedBuilder();
		cmdList.setColor(Color.ORANGE);
		cmdList.setTitle("Commands");
		String prefix = DBQueryHandler.get(guildID, "prefix");

		cmdList.appendDescription(String.format("The current prefix is set to: `%s`\n", prefix));

		GuildContext.get(guildID)
				.commandHandler();
		CommandHandler.getCommandList().forEach(command ->
		{
			if (!command.isPremium() || GuildContext.get(guildID).isPremium()) {
				cmdList.appendDescription(String.format("`%s%s`\n", prefix, command.getName()));
			}
		});
		cmdList.appendDescription("\nUse `[Audio | Media | Music]` for more help.");

		return cmdList.build();
	}

	private String getAudioHelp() {
		return """     		
						  !! Use the prefix with the command !!
						  
				`Help [command]`				  : Get help about a specific command.
				`Help [-Audio | -Media | -Music]` : Displays a list of media commands.
				     		
				`YouTube [Search...]` 				 : Searches YouTube for the song and adds it to the queue.
				`YouTube [-search | -s] [Search...]` : Searches YouTube for song and displays a list to choose.
				     				
				`Shuffle`			  : Shuffles the current queue.*
				`Repeat` 			  : Displays current repeat state.*
				`Repeat [-on | -off]` : Toggles repeat for the current queue.*
				`Stop` 				  : Clears the queue and has the bot leave the channel.
				`Join` 				  : Summons the bot to the channel.
				`skip/next` 		  : Plays the next song in the queue.
				`NowPlaying/np` 	  : Shows currently playing track.
				`Pause/P` 			  : Pauses/Resumes the currently playing track. 
				     				
				`Queue/Q` : Displays the currently playing song and remaining queue.
				`Queue [-JumpTo | -Jump | -J] [queue position]` : Skips the queue to specified position. Maintaining play order.
				`Queue [-Next | -N] [queue position]` : Moves the specified song to the next in queue.
				     				
				`Play` [url] : Adds the specified song/playlist to the queue from a provided link.
				`Play [-next | -n] [url]` : Adds the specified song/playlist to the next in queue. 
					Note: Currently playlist support is limited to YouTube playlist links only.
				     					
				`Volume/V` : Displays the current volume.
				`Volume [0-25]` : Sets the volume to the specified level. Premium Users: `[0-100]`*
				     				
				`Playlist/Pl/Plist` : Displays a list of currently made playlists.*
				`Playlist [-Current | -C | -Queue | -Q]` : Creates a playlist of the current queue.*
				`Playlist [-Delete | -D | -Remove | -R] [Name...]` : Deletes the playlist with the specified name.*
				`Playlist [-add | -new] [Name...]` : Adds a new playlist with the specified name.*
				`Playlist [-Play | -P] [Name...]` : Loads the prefix with the specified name.*
				     				
					*Premium Features.
				""";
	}

}
