package com.zischase.discordbot.commands.audiocommands;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.zischase.discordbot.DBQueryHandler;
import com.zischase.discordbot.audioplayer.TrackLoader;
import com.zischase.discordbot.audioplayer.TrackScheduler;
import com.zischase.discordbot.commands.*;
import com.zischase.discordbot.guildcontrol.GuildContext;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Youtube extends Command {

	private static final Logger LOGGER = LoggerFactory.getLogger(Youtube.class);

	public Youtube() {
		super(false);
	}

	@Override
	public @NotNull String shortDescription() {
		return "Plays or searches for a song from youtube";
	}

	@Override
	public List<String> getAliases() {
		return List.of("YT", "YTube");
	}

	@Override
	public String helpText() {
		return "`Youtube [Search Query] : Search youtube for a song. Then adds it to the queue`\n" + "`Youtube -[search|s] : Provides a list of songs. Reply with a number to choose.`\n" + "`Aliases: " + String
				.join(" ", getAliases()) + "`";
	}

	@Override
	public CommandData getCommandData() {
		return super.getCommandData().addSubcommands(
				new SubcommandData("list", "Display list of songs").addOption(OptionType.STRING, "query", "Displays a list from search result"),
				new SubcommandData("search", "Play a song").addOption(OptionType.STRING, "query", "Add first song from search result")
		);
	}

	@Override
	public void handle(CommandContext ctx) {
		List<String> args = ctx.getArgs();
		if (args.isEmpty()) {
			return;
		}

		String      guildID     = ctx.getGuild().getId();
		TextChannel textChannel = ctx.getChannel();
		VoiceChannel voiceChannel = ctx.getEventInitiator().getVoiceState() != null ?
				ctx.getEventInitiator().getVoiceState().getChannel() : null;

		List<ISearchable> songList = new ArrayList<>();
		String            videoUrl = "https://www.youtube.com/watch?v=";
		String            videoID  = "";
		Document          doc      = null;

		DBQueryHandler.set(guildID, "media_settings", "textChannel", textChannel.getId());

		boolean listResults = args.get(0).matches("(?i)-(list)");
		boolean hasNextFlag = args.stream().anyMatch(arg -> arg.matches("(?i)-(n|next)"));

		String query = String.join("+", args)
				.replaceFirst("(?i)-(list|url|name)", "")
				.replaceFirst("(?i)-(n)", "")
				.trim();

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
			doc.select("script")
					.forEach(e -> element.append(e.html()));

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
				if (videoID.matches(videoMatcher.group(0))) {
					continue;
				}
				videoID = videoMatcher.group(0);

//				RegEx. . . again . . . 				 - https://regex101.com/r/1c2wAQ/1
//				(?im)                                - caseInsensitive, Multiline
//				(?=i.ytimg.com/vi/"+uri+").{1,300}   - Positive lookahead to contain video ID near title. Arbitrarily up to 300 chars
//				(?<="title":\{"runs":\[\{"text":")   - Positive lookbehind to contain text prior to title.
//				(.+?(?=\"}]))                        - Extract song name. Any character up to the next "}]. - This closes the js object on YT end.
				songName_Pattern = Pattern.compile("(?im)(?=vi/" + videoID + "/).{1,300}(?<=\"title\":\\{\"runs\":\\[\\{\"text\":\")(.+?)(?=\"}])");
				nameMatcher      = songName_Pattern.matcher(element.html());
				if (nameMatcher.find()) {
					if (listResults) {
						songList.add(new SearchInfo(nameMatcher.group(1),
								"https://www.youtube.com/watch?v=" + videoID
						));

						if (songList.size() >= 12) {
							/* Waits for user input - blocking - commands handled asynchronously */
							ISearchable searchable = new ResultSelector(songList, ctx.getChannel(), ctx.getJDA(), ctx.getEventInitiator()).getChoice();
							videoUrl = searchable.getUrl();
							break;
						}
					} else {
						videoUrl = videoUrl.concat(videoID);
						break;
					}
				}
			}
			trackLoader.load(ctx.getChannel(), voiceChannel, videoUrl);
			/* while end */
		}
		if (ctx.getArgs().size() <= 2) {

			if (hasNextFlag) {
				TrackScheduler scheduler = GuildContext.get(guildID)
						.audioManager()
						.getScheduler();

				ArrayList<AudioTrack> queue = scheduler.getQueue();

				int index = queue.size() - 1; // Subtract 1 for '0' based numeration.

				queue.add(0, queue.get(index));
				queue.remove(index + 1); // Adding one to account for -> shift of list

				scheduler.clearQueue();
				scheduler.queueList(queue);

				GuildContext.get(guildID)
						.playerPrinter()
						.printQueue(GuildContext.get(guildID).audioManager(), ctx.getChannel());
			}
		}
	}

}
