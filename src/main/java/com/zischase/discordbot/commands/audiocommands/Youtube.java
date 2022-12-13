package com.zischase.discordbot.commands.audiocommands;

import com.github.ygimenez.exception.InvalidHandlerException;
import com.sedmelluq.discord.lavaplayer.player.FunctionalResultHandler;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.zischase.discordbot.DBQueryHandler;
import com.zischase.discordbot.audioplayer.TrackLoader;
import com.zischase.discordbot.audioplayer.TrackScheduler;
import com.zischase.discordbot.commands.*;
import com.zischase.discordbot.guildcontrol.GuildContext;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Youtube extends Command {

	private static final Logger LOGGER = LoggerFactory.getLogger(Youtube.class);

	public Youtube() {
		super(false);
	}

	@Override
	public SlashCommandData getCommandData() {
		OptionData query = new OptionData(OptionType.STRING, "query", "Displays a list from search result", true);

		return super.getCommandData().addOptions(query);
	}

	@Override
	public @NotNull String shortDescription() {
		return "Searches YouTube - Reply with number";
	}

	@Override
	public List<String> getAliases() {
		return List.of("YT", "YTube");
	}

	@Override
	public String helpText() {
		return "`Youtube [Search Query] : Search youtube, then adds selected choice to the queue. Reply with number to choose`\n`Aliases: " + String
				.join(" ", getAliases()) + "`";
	}

	@Override
	public void handle(CommandContext ctx) {
		if (!youtubeWebScrape(ctx))
			if (!youtubeLavaplayer(ctx))
				LOGGER.warn("Failed both search attempts! - {}:{}", ctx.getMember().getUser().getName(), String.join(" ", ctx.getArgs()));
	}

	private boolean youtubeLavaplayer(CommandContext ctx) {
		List<String> args = ctx.getArgs();
		if (args.isEmpty()) return false;


		String query = String.join(" ", args)
				.trim();

		GuildContext g_ctx = GuildContext.get(ctx.getGuild().getId());

		g_ctx.audioManager().getPlayerManager().source(YoutubeAudioSourceManager.class);

		AtomicReference<AudioTrack> track = new AtomicReference<>(null);

		g_ctx.audioManager()
				.getPlayerManager()
				.loadItem("ytsearch: " + query,
						new FunctionalResultHandler(
								(audioTrack) -> g_ctx.audioManager().getTrackLoader().load(ctx.getVoiceChannel(), ctx.getChannel(), audioTrack),
								(playlist) -> {
									List<ISearchResult> searchables;
									searchables = playlist.getTracks()
											.stream()
											.map(SearchInfo::new)
											.collect(Collectors.toList());

									try {
										ISearchResult choice = new ResultSelector(ctx.getEvent(), searchables, ctx.getChannel(), ctx.getJDA(), ctx.getMember()).get();

										if (choice == null) return;

										track.set(playlist.getTracks()
												.stream()
												.filter(audioTrack -> audioTrack.getInfo().title.equalsIgnoreCase(choice.getName()))
												.limit(1)
												.findFirst()
												.orElseThrow());

									} catch (InvalidHandlerException e) {
										e.printStackTrace();
									}

								},
								g_ctx.audioManager().getTrackLoader()::noMatches,
								g_ctx.audioManager().getTrackLoader()::loadFailed
						));

		boolean success = track.get() != null;
		if (success) {
			g_ctx.audioManager().getTrackLoader().load(ctx.getVoiceChannel(), ctx.getChannel(), track.get());
		}
		return success;
	}

	private boolean youtubeWebScrape(CommandContext ctx) {
		List<String> args = ctx.getArgs();
		if (args.isEmpty()) {
			return false;
		}
		String query = String.join(" ", args)
				.trim();

		String      guildID     = ctx.getGuild().getId();
		TextChannel textChannel = ctx.getChannel();
		VoiceChannel voiceChannel = ctx.getMember().getVoiceState() != null ?
				ctx.getMember().getVoiceState().getChannel().asVoiceChannel() : null;

		List<ISearchResult> songList = new ArrayList<>();
		String              videoUrl = "https://www.youtube.com/watch?v=";
		String            videoID  = "";
		Document          doc      = null;

		DBQueryHandler.set(guildID, "media_settings", "textChannel", textChannel.getId());

		boolean hasNextFlag = args.get(0).equalsIgnoreCase("-n");

		String url = "http://youtube.com/results?search_query=" + query;
		TrackLoader trackLoader = GuildContext.get(guildID)
				.audioManager()
				.getTrackLoader();
		try {
			doc = Jsoup.connect(url)
					.get();
		} catch (IOException e) {
			e.printStackTrace();
		}

		if (doc != null) {
			Element element = new Element("script");
			doc.select("script").forEach(e -> element.append(e.html()));

			//			RegEx
//			(?im)            - caseInsensitive, Multiline
//			(?<="videoId":") - Negative lookbehind for finding video ID key
//			.+?              - Any character up to ?.
//			(?=")            - ? = ".
			Pattern videoIDPattern = Pattern.compile("(?im)(?<=\"videoId\":\").+?(?=\")");
			Matcher videoMatcher   = videoIDPattern.matcher(element.html());

			Pattern songName_Pattern;
			Matcher nameMatcher;

			while (videoMatcher.find()) {
				if (videoID.matches(videoMatcher.group(0))) continue;

//				RegEx. . . again . . . 				 - https://regex101.com/r/1c2wAQ/1
//				(?im)                                - caseInsensitive, Multiline
//				(?=i.ytimg.com/vi/"+uri+").{1,300}   - Positive lookahead to contain video ID near title. Arbitrarily up to 300 chars
//				(?<="title":\{"runs":\[\{"text":")   - Positive lookbehind to contain text prior to title.
//				(.+?(?=\"}]))                        - Extract song name. Any character up to the next "}]. - This closes the js object on YT end.
				videoID = videoMatcher.group(0);
				songName_Pattern = Pattern.compile("(?im)(?=vi/" + videoID + "/).{1,300}(?<=\"title\":\\{\"runs\":\\[\\{\"text\":\")(.+?)(?=\"}])");
				nameMatcher      = songName_Pattern.matcher(element.html());

				if (nameMatcher.find()) songList.add(new SearchInfo(nameMatcher.group(1), "https://www.youtube.com/watch?v=" + videoID));

			}

			/* Waits for user input - blocking - commands handled asynchronously */
			ISearchResult searchResult;
			try {
				searchResult = new ResultSelector(ctx.getEvent(), songList, ctx.getChannel(), ctx.getJDA(), ctx.getMember()).get();

				if (searchResult == null) return false;

				videoUrl = searchResult.getUrl();
			} catch (InvalidHandlerException e) {
				e.printStackTrace();
			}

			trackLoader.load(ctx.getChannel(), voiceChannel, videoUrl);
			/* while end */
		}
		if (ctx.getArgs().size() <= 2 && hasNextFlag) {

			TrackScheduler scheduler = GuildContext.get(guildID)
					.audioManager()
					.getScheduler();

			ArrayList<AudioTrack> queue = scheduler.getQueue();

			int index = queue.size() - 1; // Subtract 1 for '0' based numeration.

			if (index < 0) return false;

			queue.add(0, queue.get(index));
			queue.remove(index + 1); // Adding one to account for -> shift of list

			scheduler.clearQueue();
			scheduler.queueList(queue);
		}
		return true;
	}
}
