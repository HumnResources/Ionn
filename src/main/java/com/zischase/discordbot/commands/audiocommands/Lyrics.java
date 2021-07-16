package com.zischase.discordbot.commands.audiocommands;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.commands.CommandHandler;
import com.zischase.discordbot.guildcontrol.GuildContext;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * TODO : Find a solution for server rejection from hosting service
 */

public class Lyrics extends Command {

	private static final Logger     LOGGER           = LoggerFactory.getLogger(Lyrics.class);
	private static final Timer      TIMER            = new Timer();
	private static final Playwright PLAYWRIGHT       = Playwright.create();
	private static final Browser    BROWSER          = PLAYWRIGHT.chromium().launch();
	private static final int        MAX_MESSAGE_SIZE = 2000;
	private static final int        TIMEOUT_MS       = 30000;

	public Lyrics() {
		super(false);
	}

	@Override
	public CommandData getCommandData() {
		return CommandData.fromData(DataObject.fromJson("""
				{
					"name": "lyrics",
					"description": "Displays the lyrics for the current song",
					"options": [
						{
							"name": "song",
							"description": "Search lyrics for specific song",
							"type": 3
						}
					]
				}
				"""));
	}

	@Override
	public @NotNull String shortDescription() {
		return "Gets the lyrics for the current/requested song.";
	}

	@Override
	public String helpText() {
		return """
				Searches for the lyrics on AZLyrics.
								
				Usage:
					lyrics 				# Search current song
					lyrics <song name>  # Request search
				""";
	}

	@Override
	public void handle(CommandContext ctx) {
		Page           page;
		String         query;
		String         selector;
		String         artist;
		String         title;
		String         lyrics;
		MessageBuilder messageBuilder;
		EmbedBuilder   embedBuilder;
		Message[]      messages;
		int            length;
		int            nMessages;

		if (ctx.getArgs().isEmpty()) {
			query = GuildContext.get(ctx.getGuild().getId())
					.audioManager()
					.getPlayer()
					.getPlayingTrack()
					.getInfo()
					.title
					.trim()
					.replaceAll("(?i)(music|video|official|lyrics|hd|sd|\\(.*\\)|\\[.*])", "");
			/* deletes matched words or anything inside perens/square braces */
		}
		else if (ctx.getArgs().size() == 1) {
			query = ctx.getArgs().get(0);
		}
		else {
			query = String.join(" ", ctx.getArgs());
		}

		if (query.isEmpty()) {
			LOGGER.warn("Query empty for lyrics search!");
			return;
		}

		query = query.trim().replaceAll("\\s+", " ");

		/* Set timeout for 30s from start before closing the page */
		page = BROWSER.newPage();
		TIMER.schedule(new TimerTask() {
			@Override
			public void run() {
				if (page != null && !page.isClosed()) {
					page.close();
				}
			}
		}, TIMEOUT_MS);

		page.navigate("https://search.azlyrics.com/search.php?q=" + query);

		/* This selects the first URL from search results - Obtained via Chrome */
		selector = "body > div.container.main-page > div > div > div > table > tbody > tr:nth-child(1) > td > a";
		if (page.querySelector(selector) == null) {
			ctx.getChannel().sendMessage("Could not find any lyrics for search `%s`. Please check spelling and try again.".formatted(query)).submit();
			page.close();
			return;
		}
		else {
//			ctx.getChannel().sendMessage("Looks like I found something, give me a second to prepare.").queue(message ->
//					message.delete().queueAfter(5, TimeUnit.SECONDS));
			ctx.getChannel()
					.sendMessage("Looks like I found something, give me a second to prepare.")
					.complete()
					.delete()
					.queueAfter(5, TimeUnit.SECONDS);
		}

		page.click(selector);

		artist         = page.innerText("body > div.container.main-page > div > div.col-xs-12.col-lg-8.text-center > div.lyricsh > h2 > a > b");
		title          = page.innerText("body > div.container.main-page > div > div.col-xs-12.col-lg-8.text-center > b");
		lyrics         = page.innerText("body > div.container.main-page > div > div.col-xs-12.col-lg-8.text-center > div:not([class])");
		messageBuilder = new MessageBuilder();
		embedBuilder   = new EmbedBuilder();
		length         = lyrics.length();
		nMessages      = (int) Math.ceil(((length + 0.0f) / MAX_MESSAGE_SIZE));
		messages       = new Message[nMessages];

		embedBuilder.setTitle("%s - %s".formatted(artist, title));

		if (length >= MAX_MESSAGE_SIZE) {
			for (int pageEnd, pageOffset, pageCount = 0; pageCount < nMessages; pageCount++) {
				/* Create the indexing offset to continue the message */
				pageOffset = (pageCount * MAX_MESSAGE_SIZE);
				/* Check to make sure we don't go past the end of the string */
				pageEnd = Math.min((pageOffset + MAX_MESSAGE_SIZE), (length - 1));

				String subString = lyrics.substring(pageOffset, pageEnd);

				/* Create new embed for the page */
				embedBuilder.appendDescription(subString);

				/* Add to array and start a new embed for next series of verse's */
				messages[pageCount] = messageBuilder.setEmbeds(embedBuilder.build()).build();
				embedBuilder        = new EmbedBuilder();
				messageBuilder      = new MessageBuilder();
			}
		} else {
			embedBuilder.appendDescription(lyrics);
			messages[0] = messageBuilder.setEmbeds(embedBuilder.build()).build();
		}

		for (Message m : messages) {
			ctx.getChannel().sendMessage(m).submit();
		}

		page.close();

		/* Calls the Now PLaying command for respective guild. This keeps the player visible TODO: Address this in track watcher */
		if (GuildContext.get(ctx.getGuild().getId()).audioManager().getPlayer().getPlayingTrack() != null) {
			Objects.requireNonNull(CommandHandler.getCommand("NowPlaying"))
				.handle(new CommandContext(ctx.getGuild(), ctx.getMember(), List.of(), ctx.getMessage(), ctx.getChannel(), null));
		}

	}

	@Deprecated
	private void customScrape(CommandContext ctx) {
		List<String> args          = ctx.getArgs();
		Element      lyricsElement = null;
		String       search;

		if (args.isEmpty()) {
			search = GuildContext.get(ctx.getGuild().getId())
					.audioManager()
					.getPlayer()
					.getPlayingTrack()
					.getInfo().title.strip()
					.replaceAll("\\s", "+");
		} else {
			search = String.join("+", args);
		}


		String query = "https://search.azlyrics.com/search.php?q=" + search;

		Document doc;
		try {
			Connection.Response response = Jsoup.connect(query)
					.userAgent("Mozilla")
					.referrer("http://www.google.com")
					.followRedirects(true)
					.execute();

			if (response.statusCode() == 403) {
				LOGGER.warn("Response Code - 403 : Forbidden");
				return;
			}

			doc = response.parse();
		} catch (IOException e) {
			LOGGER.warn("IOException - " + e.getCause().getMessage());
			return;
		}

		Element searchResultElement = doc.select("a[href]")
				.stream()
				.filter(element -> element.attributes().hasKeyIgnoreCase("href"))
				.filter(element -> element.attr("href").contains("lyrics/"))
				.findFirst()
				.orElse(null);

		if (searchResultElement == null) {
			ctx.getChannel().sendMessage("Sorry, could not find any results.").queue();
			return;
		}

		String lyricsURL = searchResultElement.attr("href");

		try {
			Connection.Response response = Jsoup.connect(lyricsURL)
					.userAgent("Mozilla")
					.referrer("http://www.google.com")
					.followRedirects(true)
					.execute();

			if (response.statusCode() == 403) {
				LOGGER.warn("Response Code - 403 : Forbidden");
				return;
			}

			doc = response.parse();
		} catch (IOException e) {
			LOGGER.warn("IOException - " + e.getCause().getMessage());
			return;
		}

		String songTitle = doc.select("body > div.container.main-page > div > div.col-xs-12.col-lg-8.text-center > b")
				.first()
				.toString()
				.replaceAll("(?i)(<.+?>)", "");
		String artist = doc.select(
				"body > div.container.main-page > div > div.col-xs-12.col-lg-8.text-center > div > h2 > a > b")
				.first()
				.toString()
				.replaceAll("(?i)(<.+?>)|(lyrics)", "");

		// Arbitrarily search up to '20' nodes.
		for (int i = 0; i < 20; i++) {
			lyricsElement = doc.select(
					"body > div.container.main-page > div > div.col-xs-12.col-lg-8.text-center > div:nth-child(" + i + ")")
					.first();

			if (lyricsElement == null) {
				continue;
			}

			if (lyricsElement.html()
					.contains("<br>")) {
				break;
			}
		}


		if (lyricsElement == null) {
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

		for (String str : lyrics) {
			if (str.isBlank() || str.isEmpty()) {
				continue;
			}

			// Reset the embed on the second verse, used to remove song title & name off subsequent messages.
			if (str.equalsIgnoreCase(lyrics[1])) {
				embed = new EmbedBuilder();
				embed.setColor(Color.lightGray);
			}

			if (str.length() > 2000) {
				Pattern p = Pattern.compile("(?ms)(?<=\\s).{1,2000}");
				Matcher m = p.matcher(str);
				while (m.find()) {
					ctx.getChannel()
							.sendMessageEmbeds(embed.setDescription(m.group())
									.build())
							.queue();
				}
			} else {
				ctx.getChannel()
						.sendMessageEmbeds(embed.setDescription(str)
								.build())
						.queue();
			}
		}

	}

	public static void shutdown() {
		TIMER.cancel();
		BROWSER.close();
		PLAYWRIGHT.close();
	}
}
