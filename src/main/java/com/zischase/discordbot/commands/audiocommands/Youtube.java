package com.zischase.discordbot.commands.audiocommands;

import com.zischase.discordbot.audioplayer.Audio;
import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.commands.ResultSelector;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

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
    public void handle(CommandContext ctx) {
        String query = String.join("", ctx.getArgs())
                .trim()
                .replaceAll( "(\\s)", "+");

        String url = "http://youtube.com/results?search_query="+query;

        Document doc = null;
        try {
            doc = Jsoup.connect(url).get();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (doc != null) {
            Elements scripts = doc.select("script");

            Element aio = scripts.first();
            for (int i = 1; i < scripts.size(); i++)
                aio.append(scripts.get(i).html());

            // RegEx:
            // (?<="title":{"runs":\[{"text":") - Negative lookbehind prefix for finding title
            // (\w.+?)?                         - Title string
            // (?=".+?(\/watch\?v=\w+)")        - Negative lookahead until url found
            // (\/watch\?v=\w+)                 - youtube url extension
            Pattern p = Pattern.compile("(?<=\"title\":\\{\"runs\":\\[\\{\"text\":\")(\\w.+?)?(?=\".+?(/watch\\?v=\\w+)\")");
            Matcher m = p.matcher(aio.html());

            List<Audio> songList = new ArrayList<>();
            int i = 0;
            while (m.find()) {
                ++i;

                String uri = "www.youtube.com" + m.group(2);
                songList.add(new Audio(m.group(1), uri));

                if (i == 10)
                    break;
            }

            new ResultSelector(ctx.getEvent(), songList).setListener();
        }
    }

}
