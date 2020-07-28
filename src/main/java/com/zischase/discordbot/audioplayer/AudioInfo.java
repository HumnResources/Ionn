package com.zischase.discordbot.audioplayer;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import de.sfuhrm.radiobrowser4j.Station;

public class AudioInfo {
    private final String url;
    private final String name;
    private final String author;

    public AudioInfo(String name, String url) {
        this.url = url;
        this.author = "";
        this.name = name;
    }

    public AudioInfo(Station station) {
        this.name = station.getName();
        this.url = station.getUrl();
        this.author = station.getCountry();
    }

    public AudioInfo(AudioTrack track) {
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
