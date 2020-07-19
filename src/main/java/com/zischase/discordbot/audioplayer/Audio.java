package com.zischase.discordbot.audioplayer;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.zischase.discordbot.commands.ISearchable;
import de.sfuhrm.radiobrowser4j.Station;

public class Audio implements ISearchable {
    private final String url;
    private final String name;
    private final String author;

    public Audio(String name, String url) {
        this.name = name;
        this.url = url;
        this.author = "";
    }

    public Audio(Station station) {
        this.name = station.getName();
        this.url = station.getUrl();
        this.author = station.getCountry();
    }

    public Audio(AudioTrack track) {
        this.name = track.getInfo().title;
        this.url = track.getInfo().uri;
        this.author = track.getInfo().author;
    }

    public String getUrl() {
        return url;
    }

    public String getName() {
        return name;
    }

    public String getAuthor() {
        return author;
    }
}
