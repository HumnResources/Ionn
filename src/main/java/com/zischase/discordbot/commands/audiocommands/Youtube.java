package com.zischase.discordbot.commands.audiocommands;

import com.sedmelluq.discord.lavaplayer.player.FunctionalResultHandler;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.zischase.discordbot.commands.*;
import com.zischase.discordbot.guildcontrol.GuildContext;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

public class Youtube extends Command {

	public Youtube() {
		super(false);
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
	public CommandData getCommandData() {
		OptionData query = new OptionData(OptionType.STRING, "query", "Displays a list from search result", true);

		return super.getCommandData().addOptions(query);
	}

	@Override
	public void handle(CommandContext ctx) {
		List<String> args = ctx.getArgs();
		if (args.isEmpty()) {
			return;
		}
		String query = String.join(" ", args)
				.replaceFirst("(?i)-(list|url|name)", "")
				.replaceFirst("(?i)-(n|next)", "")
				.replaceFirst("(?i)-(search)", "")
				.trim();

		GuildContext g_ctx = GuildContext.get(ctx.getGuild().getId());

		VoiceChannel voiceChannel = ctx.getEventInitiator().getVoiceState() != null ?
				ctx.getEventInitiator().getVoiceState().getChannel() : null;

		g_ctx.audioManager().getPlayerManager().source(YoutubeAudioSourceManager.class).setPlaylistPageCount(30);

		g_ctx.audioManager().getPlayerManager()
				.loadItem("ytsearch: "+query,
						new FunctionalResultHandler(
								(audioTrack) -> g_ctx.audioManager().getTrackLoader().load(voiceChannel, ctx.getChannel(), audioTrack),
								(playlist) -> {
									List<ISearchable> searchables;
									searchables = playlist.getTracks()
											.stream()
											.map(SearchInfo::new)
											.collect(Collectors.toList());
									ISearchable choice = new ResultSelector(searchables, ctx.getChannel(), ctx.getJDA(), ctx.getEventInitiator(), Color.RED).getChoice();

									AudioTrack track = playlist.getTracks()
											.stream()
											.filter(audioTrack -> audioTrack.getInfo().title.equalsIgnoreCase(choice.getName()))
											.limit(1)
											.findFirst()
											.orElseThrow();

									g_ctx.audioManager().getTrackLoader().load(voiceChannel, ctx.getChannel(), track);
								},
								g_ctx.audioManager().getTrackLoader()::noMatches,
								g_ctx.audioManager().getTrackLoader()::loadFailed
						));



//		## DEPRECATED ##
//
//		String      guildID     = ctx.getGuild().getId();
//		TextChannel textChannel = ctx.getChannel();
//		VoiceChannel voiceChannel = ctx.getEventInitiator().getVoiceState() != null ?
//				ctx.getEventInitiator().getVoiceState().getChannel() : null;
//
//		List<ISearchable> songList = new ArrayList<>();
//		String            videoUrl = "https://www.youtube.com/watch?v=";
//		String            videoID  = "";
//		Document          doc      = null;
//
//		DBQueryHandler.set(guildID, "media_settings", "textChannel", textChannel.getId());
//
//		boolean listResults = args.get(0).matches("(?i)-(list)");
//		boolean hasNextFlag = args.stream().anyMatch(arg -> arg.matches("(?i)-(n|next)"));
//
////		String query = String.join("+", args)
////				.replaceFirst("(?i)-(list|url|name)", "")
////				.replaceFirst("(?i)-(n|next)", "")
////				.replaceFirst("(?i)-(search)", "")
////				.trim();
//
//		String url = "http://youtube.com/results?search_query=" + query;
//		TrackLoader trackLoader = GuildContext.get(guildID)
//				.audioManager()
//				.getTrackLoader();
//		try {
//			doc = Jsoup.connect(url)
//					.get();
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//
//		if (doc != null) {
//			Element element = new Element("script");
//			doc.select("script")
//					.forEach(e -> element.append(e.html()));
//
//			//			RegEx
////			(?im)            - caseInsensitive, Multiline
////			(?<="videoId":") - Negative lookbehind for finding video ID key
////			.+?              - Any character up to ?.
////			(?=")            - ? = ".
//			Pattern videoIDPattern = Pattern.compile("(?im)(?<=\"videoId\":\").+?(?=\")");
//			Matcher videoMatcher   = videoIDPattern.matcher(element.html());
//
//			Pattern songName_Pattern;
//			Matcher nameMatcher;
//
//			while (videoMatcher.find()) {
//				if (videoID.matches(videoMatcher.group(0))) {
//					continue;
//				}
//				videoID = videoMatcher.group(0);
//
////				RegEx. . . again . . . 				 - https://regex101.com/r/1c2wAQ/1
////				(?im)                                - caseInsensitive, Multiline
////				(?=i.ytimg.com/vi/"+uri+").{1,300}   - Positive lookahead to contain video ID near title. Arbitrarily up to 300 chars
////				(?<="title":\{"runs":\[\{"text":")   - Positive lookbehind to contain text prior to title.
////				(.+?(?=\"}]))                        - Extract song name. Any character up to the next "}]. - This closes the js object on YT end.
//				songName_Pattern = Pattern.compile("(?im)(?=vi/" + videoID + "/).{1,300}(?<=\"title\":\\{\"runs\":\\[\\{\"text\":\")(.+?)(?=\"}])");
//				nameMatcher      = songName_Pattern.matcher(element.html());
//				if (nameMatcher.find()) {
//					if (listResults) {
//						songList.add(new SearchInfo(nameMatcher.group(1),
//								"https://www.youtube.com/watch?v=" + videoID
//						));
//
//						if (songList.size() >= 12) {
//							/* Waits for user input - blocking - commands handled asynchronously */
//							ISearchable searchable = new ResultSelector(songList, ctx.getChannel(), ctx.getJDA(), ctx.getEventInitiator()).getChoice();
//
//							if (searchable == null) {
//								return;
//							}
//
//							videoUrl = searchable.getUrl();
//							break;
//						}
//					} else {
//						videoUrl = videoUrl.concat(videoID);
//						break;
//					}
//				}
//			}
//			trackLoader.load(ctx.getChannel(), voiceChannel, videoUrl);
//			/* while end */
//		}
//		if (ctx.getArgs().size() <= 2) {
//
//			if (hasNextFlag) {
//				TrackScheduler scheduler = GuildContext.get(guildID)
//						.audioManager()
//						.getScheduler();
//
//				ArrayList<AudioTrack> queue = scheduler.getQueue();
//
//				int index = queue.size() - 1; // Subtract 1 for '0' based numeration.
//
//				if (index < 0 || index > queue.size()) {
//					return;
//				}
//
//				queue.add(0, queue.get(index));
//				queue.remove(index + 1); // Adding one to account for -> shift of list
//
//				scheduler.clearQueue();
//				scheduler.queueList(queue);
//			}
//		}
	}

}
