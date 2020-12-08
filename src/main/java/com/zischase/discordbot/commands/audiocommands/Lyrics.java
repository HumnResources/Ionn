package com.zischase.discordbot.commands.audiocommands;

import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.guildcontrol.GuildManager;
import net.dv8tion.jda.api.EmbedBuilder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Lyrics extends Command
{
	private static final Logger LOGGER = LoggerFactory.getLogger(Lyrics.class);

	public Lyrics()
	{
		super(false);
	}
	
	@Override
	public void handle(CommandContext ctx)
	{
		
		List<String> args = ctx.getArgs();
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
		
		
		String query = "https://search.azlyrics.com/search.php?q=" + search;

		Document doc;
		try {
			 doc = Jsoup.connect(query)
					.userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.88 Safari/537.36")
					 .referrer("http://www.google.com")
					 .get();
		}
		catch (IOException e)
		{
			e.printStackTrace();
			return;
		}

		Element searchResultElement = doc.select("a[href]")
				.stream()
				.filter(element -> element.attributes().hasKeyIgnoreCase("href"))
				.filter(element -> element.attr("href").contains("lyrics/"))
				.findFirst()
				.orElse(null);

		if (searchResultElement == null)
		{
			ctx.getChannel().sendMessage("Sorry, could not find any results.").queue();
			return;
		}

		String lyricsURL = searchResultElement.attr("href");

		try {
			doc = Jsoup.connect(lyricsURL)
					.userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.88 Safari/537.36")
					.referrer("http://www.google.com")
					.get();
		}
		catch (IOException e)
		{
			e.printStackTrace();
			return;
		}

		String songTitle = doc.select("body > div.container.main-page > div > div.col-xs-12.col-lg-8.text-center > b")
				.first()
				.toString()
				.replaceAll("(?i)(<.+?>)", "");
		String artist = doc.select("body > div.container.main-page > div > div.col-xs-12.col-lg-8.text-center > div > h2 > a > b")
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
