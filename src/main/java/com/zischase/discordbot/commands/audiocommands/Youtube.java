package com.zischase.discordbot.commands.audiocommands;

import com.zischase.discordbot.audioplayer.AudioInfo;
import com.zischase.discordbot.audioplayer.AudioResultSelector;
import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Youtube extends Command {
    public Youtube() {
        super(false);
    }

    @Override
    public List<String> getAliases() {
        return List.of("YT", "YTube");
    }

    @Override
    public String getHelp() {
        return null;
    }

    @Override
    public void execute(CommandContext ctx) {
        String query = String.join("+", ctx.getArgs());
        String url = "http://youtube.com/results?search_query="+query;

        Document doc = null;
        try {
            doc = Jsoup.connect(url).get();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (doc != null) {
            Element element = new Element("script");
            doc.select("script").forEach(e -> element.append(e.html()));

            List<AudioInfo> songList = new ArrayList<>();

            // RegEx - caseInsensitive, Multiline
            // (?<="videoId":") - Negative lookbehind for finding video ID key
            // .+?              - Any character up to ?.
            // (?=")            - ? = ".
            Pattern videoId = Pattern.compile("(?im)(?<=\"videoId\":\").+?(?=\")");
            Matcher videoMatcher = videoId.matcher(element.html());

            String uri = "";
            while (videoMatcher.find()) {
                if (uri.matches(videoMatcher.group(0)))
                    continue;
                uri =  videoMatcher.group(0);

                // RegEx - caseInsensitive, Multiline
                // (?=i.ytimg.com/vi/"+uri+").{1,300}   - Positive lookahead to contain video ID near title. Arbitrarily up to 300 chars
                // (?<="title":\{"runs":\[\{"text":")   - Positive lookbehind to contain text prior to title.
                // (.+?(?=\"))                          - Extract song name. Any character up to the next ".
                Pattern songName = Pattern.compile("(?im)(?=i.ytimg.com/vi/"+uri+").{1,300}(?<=\"title\":\\{\"runs\":\\[\\{\"text\":\")(.+?(?=\"))");
                Matcher nameMatcher = songName.matcher(element.html());

                if (nameMatcher.find()) {
                    String name = nameMatcher.group(1);
                    songList.add(new AudioInfo(name, "https://www.youtube.com/watch?v=" + uri));
                }

                if (songList.size() >= 12)
                    break;
            }

            new AudioResultSelector(ctx.getEvent(), songList).setListener();
        }
    }

}
