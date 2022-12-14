//package com.zischase.discordbot.commands.audiocommands;
//
//import com.google.gson.Gson;
//import com.zischase.discordbot.commands.Command;
//import com.zischase.discordbot.commands.CommandContext;
//import com.zischase.discordbot.commands.CommandHandler;
//import com.zischase.discordbot.guildcontrol.GuildContext;
//import net.dv8tion.jda.api.EmbedBuilder;
//import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
//import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
//import net.dv8tion.jda.api.utils.data.DataObject;
//import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
//import net.dv8tion.jda.api.utils.messages.MessageCreateData;
//import org.jetbrains.annotations.NotNull;
//import org.jsoup.Connection;
//import org.jsoup.Jsoup;
//import org.jsoup.nodes.Document;
//import org.jsoup.nodes.Element;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.io.File;
//import java.io.FileOutputStream;
//import java.io.IOException;
//import java.net.ConnectException;
//import java.net.URL;
//import java.nio.channels.Channels;
//import java.nio.channels.ReadableByteChannel;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.time.OffsetDateTime;
//import java.util.*;
//import java.util.concurrent.ScheduledExecutorService;
//import java.util.concurrent.ScheduledThreadPoolExecutor;
//import java.util.concurrent.atomic.AtomicReference;
//
//public final class Lyrics extends Command {
//
//	private static final int    MAX_MESSAGE_SIZE           = 2000;
//	private static final int    TIMEOUT_MS                 = 30000;
//	private static final int    PROXY_CHECK_TIMER_MS       = 30 * 60000; // Get millis for timeout: mins * 60000 = millis;
//	private static final int    PROXY_REFRESH_RATE_HR      = 4;
//	private static final String LYRICS_PROVIDER_SEARCH_URI = "https://search.azlyrics.com/search.php?q=";
//	private static final String PROXY_LIST_URL             = "https://proxylist.geonode.com/api/proxy-list?limit=2000&page=1&sort_by=lastChecked&sort_type=desc&speed=fast"; //"https://proxylist.geonode.com/api/proxy-list?limit=1000&page=1&sort_by=lastChecked&sort_type=desc&google=true";
//	private static final String PROXY_LIST_FILE_NAME       = "proxy-list.csv";
//	private static final String USER_AGENT                 = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.169 Safari/537.36";
//	private static final Logger                                       LOGGER           = LoggerFactory.getLogger(Lyrics.class);
//	private static final Timer                                        TIMER            = new Timer();
//	private static final AtomicReference<List<GeonodeProxyList.Data>> ACTIVE_PROXIES   = new AtomicReference<>(new ArrayList<>());
//	private static final List<GeonodeProxyList.Data>                  INACTIVE_PROXIES = new ArrayList<>();
//	private static final ScheduledExecutorService                     EXEC             = new ScheduledThreadPoolExecutor(10);
//	private static OffsetDateTime lastUpdateTime = OffsetDateTime.now();
//	private static final TimerTask                                    TIMER_TASK       = new TimerTask() {
//		@Override
//		public void run() {
//			checkProxies(OffsetDateTime.now().isAfter(lastUpdateTime.plusHours(PROXY_REFRESH_RATE_HR)));
//		}
//	};
//
//	/* Initialize list of free proxy forwarders */
//	static {
//		checkProxies(false);
//
//		LOGGER.info("Done parsing proxy list in memory. Initializing timer to check proxies.");
//
//		TIMER.scheduleAtFixedRate(TIMER_TASK, PROXY_CHECK_TIMER_MS, PROXY_CHECK_TIMER_MS);
//	}
//
//	public Lyrics() {
//		super(false);
//	}
//
//	public static void shutdown() {
//		TIMER.cancel();
//	}
//
//	private static void checkProxies(boolean forceUpdate) {
//		try {
//			StringBuilder csv           = new StringBuilder();
//			File          proxyListFile = new File(PROXY_LIST_FILE_NAME);
//			if ((ACTIVE_PROXIES.get().size() == 0 && INACTIVE_PROXIES.size() == 0) || !proxyListFile.exists() || forceUpdate) {
//				if (proxyListFile.exists()) {
//					Files.delete(Path.of(proxyListFile.getAbsolutePath()));
//				}
//
//				if (!proxyListFile.exists() && !proxyListFile.isDirectory()) {
//					URL                 url          = new URL(PROXY_LIST_URL);
//					ReadableByteChannel byteChannel  = Channels.newChannel(url.openStream());
//					FileOutputStream    outputStream = new FileOutputStream(PROXY_LIST_FILE_NAME);
//					outputStream.getChannel().transferFrom(byteChannel, 0, Integer.MAX_VALUE);
//					LOGGER.info("Downloaded proxy list to file.");
//				}
//
//				List<String> stringList = Files.readAllLines(Path.of(PROXY_LIST_FILE_NAME));
//				for (String s : stringList) {
//					csv.append(s);
//				}
//				lastUpdateTime = OffsetDateTime.now();
//			} else {
//				LOGGER.info("Proxy list found!");
//			}
//
//			GeonodeProxyList geonodeProxyList = new Gson().fromJson(csv.toString(), GeonodeProxyList.class);
//
//			if (geonodeProxyList == null) {
//				LOGGER.warn("Geonode proxy list is null!");
//				return;
//			}
//
//			INACTIVE_PROXIES.addAll(geonodeProxyList.data);
//
//			for (GeonodeProxyList.Data data : INACTIVE_PROXIES) {
//				EXEC.submit(() -> {
//					try {
//						Jsoup.connect(LYRICS_PROVIDER_SEARCH_URI)
//								.proxy(data.ip, Integer.parseInt(data.port))
//								.userAgent(USER_AGENT)
//								.timeout(TIMEOUT_MS)
//								.get();
//
//						ACTIVE_PROXIES.get().add(data);
//						LOGGER.info("Proxy - {}:{} - Added", data.ip, data.port);
//
//					} catch (IOException e) {
//						INACTIVE_PROXIES.add(data);
//						ACTIVE_PROXIES.get().remove(data);
//					}
//				});
//			}
//
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//	}
//
//	@Override
//	public SlashCommandData getCommandData() {
//		return SlashCommandData.fromData(DataObject.fromJson("""
//				{
//					"name": "lyrics",
//					"description": "Displays the lyrics for the current song",
//					"options": [
//						{
//							"name": "song",
//							"description": "Search lyrics for specific song",
//							"type": 3
//						}
//					]
//				}
//				"""));
//	}
//
//	@Override
//	public @NotNull String shortDescription() {
//		return "Gets the lyrics for the current/requested song.";
//	}
//
//	@Override
//	public String helpText() {
//		return """
//				Searches for the lyrics on AZLyrics.
//
//				Usage:
//					lyrics 				# Search current song
//					lyrics <song name>  # Request search
//				""";
//	}
//
//	@Override
//	public void handle(CommandContext ctx) {
//		String search;
//
//		if (ctx.getArgs().isEmpty()) {
//			search = GuildContext.get(ctx.getGuild().getId())
//					.audioManager()
//					.getPlayer()
//					.getPlayingTrack()
//					.getInfo()
//					.title
//					.strip()
//					.replaceAll("(?i)(music|video|official|lyrics|hd|sd|\\(.*\\)|\\[.*])", "")
//					.replaceAll("\\s+", "+");
//			/* deletes matched words or anything inside perens/square braces */
//		} else {
//			search = String.join("+", ctx.getArgs());
//		}
//
//		if (search.isEmpty()) {
//			LOGGER.warn("No search provided for lyrics.");
//			return;
//		}
//
//		jSoupScrape(search, ctx.getChannel());
//
//		/* Calls the Now PLaying command for respective guild. This keeps the player visible TODO: Address this in track watcher */
//		if (GuildContext.get(ctx.getGuild().getId()).audioManager().getPlayer().getPlayingTrack() != null) {
//			Objects.requireNonNull(CommandHandler.getCommand("NowPlaying"))
//					.handle(new CommandContext(ctx.getGuild(), ctx.getMember(), List.of(), ctx.getMessage(), ctx.getChannel(), null, null));
//		}
//	}
//
//	private void jSoupScrape(String search, TextChannel textChannel) {
//		if (ACTIVE_PROXIES.get().size() == 0) {
//			textChannel.sendMessage("No servers available for routing. Please wait a few minutes and try again.").submit();
//			checkProxies(true);
//			return;
//		}
//
//		String         lyricsURL;
//		String         title;
//		String         artist;
//		String         query;
//		String         lyrics;
//		EmbedBuilder   embedBuilder;
//		MessageCreateBuilder messageBuilder;
//		Document       doc;
//		Element        searchResultElement;
//		MessageCreateData[]      messages;
//		int            length;
//		int            nMessages;
//
//		/* Build URI */
//		query = LYRICS_PROVIDER_SEARCH_URI + search;
//
//		int proxyChoice = (int) (Math.random() * ACTIVE_PROXIES.get().size());
//
//
//		GeonodeProxyList.Data data = ACTIVE_PROXIES.get().get(proxyChoice);
//
//		/* Connect to and retrieve a DOM from host provider */
//		try {
//			Connection.Response response = Jsoup
//					.connect(query)
//					.proxy(data.id, Integer.parseInt(data.port))
//					.userAgent(USER_AGENT)
//					.timeout(TIMEOUT_MS)
//					.execute();
//
//			if (response.statusCode() == 403) {
//				LOGGER.warn("Response Code - 403 : Forbidden");
//				return;
//			}
//
//			doc = response.parse();
//		} catch (IOException e) {
//			if (e instanceof ConnectException) {
//				LOGGER.warn("Connect Exception - {}", e.getCause().getMessage());
//			}
//			ACTIVE_PROXIES.get().remove(data);
//			jSoupScrape(search, textChannel);
//			return;
//		}
//
//		/* Checks for an available search result */
//		searchResultElement = doc.selectFirst("body > div.container.main-page > div > div > div > table > tbody > tr:nth-child(1) > td > a");
//
//		if (searchResultElement == null) {
//			textChannel.sendMessage("Sorry, could not find any results.").queue();
//			return;
//		}
//
//		/* Connect to and retrieve a DOM from first result */
//		lyricsURL = searchResultElement.attr("href");
//		try {
//			Connection.Response response = Jsoup.connect(lyricsURL)
//					.proxy(data.ip, Integer.parseInt(data.port))
//					.userAgent(USER_AGENT)
//					.timeout(0)
//					.execute();
//
//			if (response.statusCode() == 403) {
//				LOGGER.warn("Response Code - 403 : Forbidden");
//				return;
//			}
//
//			doc = response.parse();
//		} catch (IOException e) {
//			e.printStackTrace();
//			return;
//		}
//
//		/* Query DOM for meta data */
//		artist = doc.select("body > div.container.main-page > div > div.col-xs-12.col-lg-8.text-center > div.lyricsh > h2 > a > b").text();
//		title  = doc.select("body > div.container.main-page > div > div.col-xs-12.col-lg-8.text-center > b").text();
//		lyrics = doc.select("body > div.container.main-page > div > div.col-xs-12.col-lg-8.text-center > div:not([class])").toString().replaceAll("(<!--.*-->|<br>|<i>|</i>|<div>|</div.*)", "");
//		length = lyrics.length();
//
//		/* Get number of messages needed to meet Discord criteria */
//		nMessages      = (int) Math.ceil(((float) length / MAX_MESSAGE_SIZE));
//		messages       = new MessageCreateData[nMessages];
//		embedBuilder   = new EmbedBuilder();
//		messageBuilder = new MessageCreateBuilder();
//		embedBuilder.setTitle("%s - %s".formatted(artist, title));
//
//		/* Handle multiple messages */
//		if (length >= MAX_MESSAGE_SIZE) {
//			int pageEndIndex, pageStartIndex, endOffset, startOffset = 0;
//			for (int pageCount = 0; pageCount < nMessages; pageCount++) {
//				/* Create the indexing offset to continue the message */
//				pageStartIndex = (pageCount * MAX_MESSAGE_SIZE);
//
//				/* Check to make sure we don't go past the end of the string */
//				pageEndIndex = Math.min((pageStartIndex + MAX_MESSAGE_SIZE), (length - 1));
//
//				/* Set offset and account for preserving line, make sure we pass if at EOF or start */
//				endOffset = 0;
//				if (lyrics.charAt(pageEndIndex) != '\n' && pageEndIndex != length - 1) {
//					do {
//						endOffset++;
//					} while (lyrics.charAt(pageEndIndex - endOffset) != '\n');
//				}
//
//				/* Create new embed for the page */
//				String subString = lyrics.substring(pageStartIndex - startOffset, pageEndIndex - endOffset);
//				embedBuilder.appendDescription(subString);
//
//				/* Stores the value of the previous end offset, to use at the start of the next page */
//				startOffset = endOffset;
//
//				/* Add to array and start a new embed for next series of verse's */
//				messages[pageCount] = messageBuilder.setEmbeds(embedBuilder.build()).build();
//
//				/* Reset for next set of lyrics */
//				embedBuilder   = new EmbedBuilder();
//				messageBuilder = new MessageCreateBuilder();
//			}
//		} else {
//			/* Only need one message */
//			embedBuilder.appendDescription(lyrics);
//			messages[0] = messageBuilder.setEmbeds(embedBuilder.build()).build();
//		}
//
//		/* Sends any messages we received */
//		for (MessageCreateData m : messages) {
//			textChannel.sendMessage(m).submit();
//		}
//	}
//
//	private static final class GeonodeProxyList {
//		private final ArrayList<Data> data = new ArrayList<>();
//		private       int             total;
//		private       int             page;
//		private       int             limit;
//
//		private static final class Data {
//			private String            id;
//			private String            ip;
//			private String            level;
//			private String            asn;
//			private String            city;
//			private String            country;
//			private String            createdAt;
//			private String            google;
//			private String            isp;
//			private String            lastChecked;
//			private String            latency;
//			private String            org;
//			private String            port;
//			private ArrayList<String> protocols;
//			private String            region;
//			private String            responseTime;
//			private String            speed;
//			private String            updatedAt;
//			private String            workingPercent;
//		}
//	}
//}
