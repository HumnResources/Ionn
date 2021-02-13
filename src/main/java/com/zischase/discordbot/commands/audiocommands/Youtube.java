package com.zischase.discordbot.commands.audiocommands;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.zischase.discordbot.audioplayer.TrackLoader;
import com.zischase.discordbot.audioplayer.TrackScheduler;
import com.zischase.discordbot.commands.*;
import com.zischase.discordbot.guildcontrol.GuildManager;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Youtube extends Command
{
	private static final Logger LOGGER = LoggerFactory.getLogger(Youtube.class);
	
	public Youtube()
	{
		super(false);
	}
	
	@Override
	public List<String> getAliases()
	{
		return List.of("YT", "YTube");
	}
	
	@Override
	public String getHelp()
	{
		return "`Youtube [Search Query] : Search youtube for a song. Then adds it to the queue`\n" + "`Youtube -[search|s] : Provides a list of songs. Reply with a number to choose.`\n" + "`Aliases: " + String
				.join(" ", getAliases()) + "`";
	}
	
	@Override
	public void handle(CommandContext ctx)
	{
		List<String> args = ctx.getArgs();
		if (ctx.getArgs()
			   .isEmpty())
		{
			return;
		}
		
		boolean doSearch = args.stream().anyMatch(arg -> arg.matches("(?i)(-s|-search)"));
		String query = String.join(" ", args).replaceAll("(?i)(-(s|search|n|next))", "").replaceAll("\\w", "+");
		String url = "http://youtube.com/results?search_query=" + query;
		TrackLoader trackLoader = GuildManager.getContext(ctx.getGuild())
											  .audioManager()
											  .getTrackLoader();
		Document doc = null;
		
		try
		{
			doc = Jsoup.connect(url)
					   .get();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		
		if (doc != null)
		{
			Element element = new Element("script");
			doc.select("script")
			   .forEach(e -> element.append(e.html()));
			
			List<ISearchable> songList = new ArrayList<>();

//			RegEx
//			(?im)            - caseInsensitive, Multiline
//			(?<="videoId":") - Negative lookbehind for finding video ID key
//			.+?              - Any character up to ?.
//			(?=")            - ? = ".
			Pattern videoIDPattern = Pattern.compile("(?im)(?<=\"videoId\":\").+?(?=\")");
			Matcher videoMatcher = videoIDPattern.matcher(element.html());
			
			Pattern songName;
			Matcher nameMatcher;
			
			String videoID = "";
			while (videoMatcher.find())
			{
				if (videoID.matches(videoMatcher.group(0)))
				{
					continue;
				}
				videoID = videoMatcher.group(0);

//				RegEx. . . again . . . 				 - https://regex101.com/r/1c2wAQ/1
//				(?im)                                - caseInsensitive, Multiline
//				(?=i.ytimg.com/vi/"+uri+").{1,300}   - Positive lookahead to contain video ID near title. Arbitrarily up to 300 chars
//				(?<="title":\{"runs":\[\{"text":")   - Positive lookbehind to contain text prior to title.
//				(.+?(?=\"}]))                        - Extract song name. Any character up to the next "}]. - This closes the js object on YT end.
				songName = Pattern.compile("(?im)(?=vi/" + videoID + "/).{1,300}(?<=\"title\":\\{\"runs\":\\[\\{\"text\":\")(.+?)(?=\"}])");
				nameMatcher = songName.matcher(element.html());
				
				if (nameMatcher.find())
				{
					
					if (doSearch)
					{
						songList.add(new SearchInfo(nameMatcher.group(1), "https://www.youtube.com/watch?v=" + videoID));
						
						if (songList.size() >= 12)
						{
							try
							{
								ISearchable result = new ResultSelector(songList).getChoice(ctx.getEvent())
																				 .get();
								
								trackLoader.load(ctx.getChannel(), ctx.getMember(), result.getUrl());
							}
							catch (InterruptedException | ExecutionException e)
							{
								LOGGER.warn("Youtube result exception: \n" + e.getCause()
																			  .getLocalizedMessage());
							}
							break;
						}
					}
					else
					{
						String videoUrl = "https://www.youtube.com/watch?v=" + videoID;
						trackLoader.load(ctx.getChannel(), ctx.getMember(), videoUrl);
						break;
					}
				}
			}
		}
		if (ctx.getArgs().size() <= 2)
		{
			boolean hasNextFlag = args.stream().anyMatch(arg -> arg.matches("(?i)-(n|next)"));
			
			if (hasNextFlag) {
				TrackScheduler scheduler = GuildManager.getContext(ctx.getGuild())
													   .audioManager()
													   .getScheduler();
				
				ArrayList<AudioTrack> queue = scheduler.getQueue();
				
				int index = queue.size() - 1; // Subtract 1 for '0' based numeration.
				
				queue.add(0, queue.get(index));
				queue.remove(index + 1); // Adding one to account for -> shift of list
				
				scheduler.clearQueue();
				scheduler.queueList(queue, ctx.getChannel());
			}
		}
	}
	
	private void searchAndPlay(CommandContext ctx)
	{
	
	}
	
}
