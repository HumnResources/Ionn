package com.zischase.discordbot.commands.audiocommands;

import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.commands.CommandHandler;
import com.zischase.discordbot.guildcontrol.GuildContext;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.*;

/*
 * TODO : Find a solution for server rejection from hosting service
 */

public class Lyrics extends Command {

	private static final String PROXY_LIST_URL       = "https://raw.githubusercontent.com/clarketm/proxy-list/master/proxy-list-raw.txt";
	private static final String PROXY_LIST_FILE_NAME = "proxy-list.txt";
	private static final Logger LOGGER               = LoggerFactory.getLogger(Lyrics.class);
	private static final Timer  TIMER                = new Timer();
	private static final int    MAX_MESSAGE_SIZE     = 2000;
	private static final Map<String, Integer> proxies = new HashMap<>();

	/* Initialize list of free proxy forwarders */
	static {
		try {
			File proxyList = new File(PROXY_LIST_FILE_NAME);
			if (!proxyList.exists() && !proxyList.isDirectory()) {
				URL                 url          = new URL(PROXY_LIST_URL);
				ReadableByteChannel byteChannel  = Channels.newChannel(url.openStream());
				FileOutputStream    outputStream = new FileOutputStream(PROXY_LIST_FILE_NAME);
				outputStream.getChannel().transferFrom(byteChannel, 0, Integer.MAX_VALUE);
			}

			Scanner  scanner = new Scanner(proxyList);
			String[] tmp;
			while (scanner.hasNextLine()) {
				tmp = scanner.nextLine().split(":");

				proxies.put(tmp[0], Integer.valueOf(tmp[1]));
			}
			scanner.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

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
		String search;

		if (ctx.getArgs().isEmpty()) {
			search = GuildContext.get(ctx.getGuild().getId())
					.audioManager()
					.getPlayer()
					.getPlayingTrack()
					.getInfo()
					.title
					.strip()
					.replaceAll("(?i)(music|video|official|lyrics|hd|sd|\\(.*\\)|\\[.*])", "")
					.replaceAll("\\s+", "+");
			/* deletes matched words or anything inside perens/square braces */
		} else {
			search = String.join("+", ctx.getArgs());
		}

		if (search.isEmpty()) {
			LOGGER.warn("No search provided for lyrics.");
			return;
		}

		jSoupScrape(search, ctx.getChannel());

		/* Calls the Now PLaying command for respective guild. This keeps the player visible TODO: Address this in track watcher */
		if (GuildContext.get(ctx.getGuild().getId()).audioManager().getPlayer().getPlayingTrack() != null) {
			Objects.requireNonNull(CommandHandler.getCommand("NowPlaying"))
					.handle(new CommandContext(ctx.getGuild(), ctx.getMember(), List.of(), ctx.getMessage(), ctx.getChannel(), null));
		}
	}

	private void jSoupScrape(String search, TextChannel textChannel) {
		String         lyricsURL;
		String         title;
		String         artist;
		String         query;
		String         lyrics;
		EmbedBuilder   embedBuilder;
		MessageBuilder messageBuilder;
		Document       doc;
		Element        searchResultElement;
		Message[]      messages;
		int            length;
		int            nMessages;

		/* Build URI */
		query = "https://search.azlyrics.com/search.php?q=" + search;
		String proxy = (String) Arrays.asList(proxies.keySet().toArray()).get((int) (Math.random() * proxies.size()));

		/* Connect to and retrieve a DOM from host provider */
		try {
			Connection.Response response = Jsoup.connect(query)
					.proxy(proxy, proxies.get(proxy))
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

		/* Checks for an available search result */
		searchResultElement = doc.selectFirst("body > div.container.main-page > div > div > div > table > tbody > tr:nth-child(1) > td > a");

		if (searchResultElement == null) {
			textChannel.sendMessage("Sorry, could not find any results.").queue();
			return;
		}

		/* Connect to and retrieve a DOM from first result */
		lyricsURL = searchResultElement.attr("href");
		try {
			Connection.Response response = Jsoup.connect(lyricsURL)
					.proxy(proxy, proxies.get(proxy))
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

		/* Query DOM for meta data */
		artist = doc.select("body > div.container.main-page > div > div.col-xs-12.col-lg-8.text-center > div.lyricsh > h2 > a > b").text();
		title  = doc.select("body > div.container.main-page > div > div.col-xs-12.col-lg-8.text-center > b").text();
		lyrics = doc.select("body > div.container.main-page > div > div.col-xs-12.col-lg-8.text-center > div:not([class])").toString().replaceAll("(<!--.*-->|<br>|<i>|</i>|<div>|</div.*)", "");
		length = lyrics.length();

		/* Get number of messages needed to meet Discord criteria */
		nMessages      = (int) Math.ceil(((float) length / MAX_MESSAGE_SIZE));
		messages       = new Message[nMessages];
		embedBuilder   = new EmbedBuilder();
		messageBuilder = new MessageBuilder();
		embedBuilder.setTitle("%s - %s".formatted(artist, title));

		/* Handle multiple messages */
		if (length >= MAX_MESSAGE_SIZE) {
			int pageEndIndex, pageStartIndex, endOffset, startOffset = 0;
			for (int pageCount = 0; pageCount < nMessages; pageCount++) {
				/* Create the indexing offset to continue the message */
				pageStartIndex = (pageCount * MAX_MESSAGE_SIZE);

				/* Check to make sure we don't go past the end of the string */
				pageEndIndex = Math.min((pageStartIndex + MAX_MESSAGE_SIZE), (length - 1));

				/* Set offset and account for preserving line, make sure we pass if at EOF or start */
				endOffset = 0;
				if (lyrics.charAt(pageEndIndex) != '\n' && pageEndIndex != length - 1) {
					do {
						endOffset++;
					} while (lyrics.charAt(pageEndIndex - endOffset) != '\n');
				}

				/* Create new embed for the page */
				String subString = lyrics.substring(pageStartIndex - startOffset, pageEndIndex - endOffset);
				embedBuilder.appendDescription(subString);

				/* Stores the value of the previous end offset, to use at the start of the next page */
				startOffset = endOffset;

				/* Add to array and start a new embed for next series of verse's */
				messages[pageCount] = messageBuilder.setEmbeds(embedBuilder.build()).build();

				/* Reset for next set of lyrics */
				embedBuilder   = new EmbedBuilder();
				messageBuilder = new MessageBuilder();
			}
		} else {
			/* Only need one message */
			embedBuilder.appendDescription(lyrics);
			messages[0] = messageBuilder.setEmbeds(embedBuilder.build()).build();
		}

		/* Sends any messages we received */
		for (Message m : messages) {
			textChannel.sendMessage(m).submit();
		}
	}

	public static void shutdown() {
		TIMER.cancel();
	}
}
