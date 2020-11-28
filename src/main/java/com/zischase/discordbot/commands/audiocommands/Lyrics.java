package com.zischase.discordbot.commands.audiocommands;

import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.guildcontrol.GuildManager;
import net.dv8tion.jda.api.EmbedBuilder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Lyrics extends Command
{
	
	public Lyrics()
	{
		super(false);
	}
	
	@Override
	public void handle(CommandContext ctx)
	{
		
		List<String> args = ctx.getArgs();
		String songTitle = null;
		String artist = null;
		Element lyricsElement = null;
		String search;
		
		if (args.isEmpty())
		{
			search = GuildManager.getContext(ctx.getGuild())
								 .audioManager()
								 .getPlayer()
								 .getPlayingTrack()
								 .getInfo().title.strip()
												 .replaceAll("\\s", "+");
		}
		else
		{
			search = String.join("+", args);
		}
		
		
		String query = "https://www.google.com/search?q=www.azlyrics.com+" + search;
		
		try
		{
			Document doc = Jsoup.connect(query)
								.get();
			Element result = doc.select("#rso > div:nth-child(1) > div > div.r > a")
								.first();
			String lyricsURL;
			
			if (result.hasAttr("href"))
			{
				lyricsURL = result.attr("href");
			}
			else
			{
				throw new IOException();
			}
			
			doc = Jsoup.connect(lyricsURL)
					   .get();
			
			songTitle = doc.select("body > div.container.main-page > div > div.col-xs-12.col-lg-8.text-center > b")
						   .first()
						   .toString()
						   .replaceAll("(?i)(<.+?>)", "");
			artist = doc.select("body > div.container.main-page > div > div.col-xs-12.col-lg-8.text-center > div.lyricsh > h2 > a > b")
						.first()
						.toString()
						.replaceAll("(?i)(<.+?>)|(lyrics)", "");
			
			// Arbitrarily search up to '20' nodes.
			for (int i = 0; i < 20; i++)
			{
				lyricsElement = doc.select("body > div.container.main-page > div > div.col-xs-12.col-lg-8.text-center > div:nth-child(" + i + ")")
								   .first();
				
				if (lyricsElement == null)
				{
					continue;
				}
				
				if (lyricsElement.html()
								 .contains("<br>"))
				{
					break;
				}
			}
		}
		catch (IOException ignored)
		{
		}
		
		if (lyricsElement == null)
		{
			ctx.getChannel()
			   .sendMessage("Sorry, i couldn't find anything :c")
			   .queue();
			return;
		}
		
		String[] lyrics = lyricsElement.html()
									   .replaceAll("(?i)(<.+?>\\s)", "")
									   .split("(?m)(\\s\\n)");
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setTitle(songTitle + " - " + artist);
		embed.setColor(Color.lightGray);
		
		for (String str : lyrics)
		{
			if (str.isBlank() || str.isEmpty())
			{
				continue;
			}
			
			// Reset the embed on the second verse, used to remove song title & name off subsequent messages.
			if (str.equalsIgnoreCase(lyrics[1]))
			{
				embed = new EmbedBuilder();
				embed.setColor(Color.lightGray);
			}
			
			if (str.length() > 2000)
			{
				Pattern p = Pattern.compile("(?ms)(?<=\\s).{1,2000}");
				Matcher m = p.matcher(str);
				while (m.find())
				{
					ctx.getChannel()
					   .sendMessage(embed.setDescription(m.group())
										 .build())
					   .queue();
				}
			}
			else
			{
				ctx.getChannel()
				   .sendMessage(embed.setDescription(str)
									 .build())
				   .queue();
			}
		}
		
	}
}
