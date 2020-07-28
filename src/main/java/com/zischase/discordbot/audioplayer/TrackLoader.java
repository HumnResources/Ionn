package com.zischase.discordbot.audioplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.zischase.discordbot.guildcontrol.GuildManager;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.VoiceChannel;

import java.util.ArrayList;

public class TrackLoader implements AudioLoadResultHandler {
    private Member member;
    private TextChannel textChannel;
    private String url;
    private boolean loaded = false;

    public TrackLoader() {
        this.member = null;
        this.textChannel = null;
        this.url = "";
    }

    public void load(TextChannel channel, Member member, String url) {
        this.textChannel = channel;
        this.member = member;
        this.url = url;

        if (connectVoice())
            GuildManager.getContext(member.getGuild())
                    .getAudioManager()
                    .getAudioPlayerManager()
                    .loadItem(url, this);
        else
            textChannel.sendMessage("You must be in a voice channel to listen to music silly.").queue();
    }

    private boolean connectVoice() {
        for (VoiceChannel channel : member.getGuild().getVoiceChannels()) {
            if (channel.getMembers().contains(member)) {
                member.getJDA()
                        .getDirectAudioController()
                        .connect(channel);
                return true;
            }
        }
        return false;
    }

    @Override
    public void trackLoaded(AudioTrack audioTrack) {
        GuildManager.getContext(textChannel.getGuild())
                .getAudioManager()
                .getScheduler()
                .queue(audioTrack, textChannel);
        loaded = true;
    }

    @Override
    public void playlistLoaded(AudioPlaylist audioPlaylist) {
        GuildManager.getContext(textChannel.getGuild())
                .getAudioManager()
                .getScheduler()
                .queueList((ArrayList<AudioTrack>) audioPlaylist.getTracks(), textChannel);
        loaded = true;
    }

    @Override
    public void noMatches() {
        textChannel.sendMessage("Darn, no matches found !").queue();
        loaded = false;
    }

    @Override
    public void loadFailed(FriendlyException e) {
        int retry = 0;

        for (; retry < 3; retry++) {
            GuildManager.getContext(textChannel.getGuild())
                    .getAudioManager()
                    .getAudioPlayerManager()
                    .loadItem(url, this);
            if (loaded)
                return;
        }

        loaded = false;
    }
}
