package com.zischase.discordbot.audioplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.zischase.discordbot.DBQueryHandler;
import net.dv8tion.jda.api.entities.Guild;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class TrackScheduler extends AudioEventAdapter {

	private static final Logger LOGGER = LoggerFactory.getLogger(TrackScheduler.class);

	static {
		LOGGER.info("TrackSchedulers Initialized");
	}

	private final AudioPlayer               player;
	private final BlockingQueue<AudioTrack> queue;
	private final String                    guildID;
	private       AudioTrack                lastTrack;
	private       boolean                   repeatQueue = false;
	private       boolean                   repeatSong  = false;

	public TrackScheduler(AudioPlayer player, Guild guild) {
		this.guildID = guild.getId();
		this.player  = player;
		this.queue   = new LinkedBlockingQueue<>();
	}

	public boolean isRepeatSong() {
		return repeatSong;
	}

	public void setRepeatSong(boolean repeatSong) {
		if (DBQueryHandler.getPremiumStatus(guildID)) {
			this.repeatSong = repeatSong;
		}
	}

	public boolean isRepeatQueue() {
		return repeatQueue;
	}

	public void setRepeatQueue(boolean repeatQueue) {
		if (DBQueryHandler.getPremiumStatus(guildID)) {
			this.repeatQueue = repeatQueue;
		}
	}

	public void queueAudio(AudioTrack track) {
		if (!player.startTrack(track, true)) { // noInterrupt: True == add to queue; Returns true if added
			this.queue.offer(track);
		}
	}

	public void queueList(AudioPlaylist playlist) {
		ArrayList<AudioTrack> tracks = (ArrayList<AudioTrack>) playlist.getTracks();
		queueList(tracks);
	}

	public void queueList(ArrayList<AudioTrack> tracks) {
		if (tracks.isEmpty()) {
			return;
		}
		this.queue.addAll(tracks);

		if (player.getPlayingTrack() == null) {
			this.player.startTrack(queue.poll(), false);
		}
	}

	public void clearQueue() {
		this.queue.clear();
	}

	public ArrayList<AudioTrack> getQueue() {
		ArrayList<AudioTrack> trackList = new ArrayList<>();

		if (!this.queue.isEmpty()) {
			trackList.addAll(queue);
		}

		return trackList;
	}

	public void prevTrack() {
		this.player.startTrack(lastTrack.makeClone(), false);
	}

	@Override
	public void onPlayerPause(AudioPlayer player) {
	}

	@Override
	public void onPlayerResume(AudioPlayer player) {
	}

	@Override
	public void onTrackStart(AudioPlayer player, AudioTrack track) {
//        if (!queue.isEmpty()) {
		/* Do Nothing */
//        }
	}

	@Override
	public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
		if (endReason.equals(AudioTrackEndReason.LOAD_FAILED)) {
			/* Do Nothing*/
			LOGGER.info("Loading failed for audio track {}", track.getInfo().title);
			return;
		}
		this.lastTrack = track;

		if (endReason.mayStartNext) {
			nextTrack();
		}
	}

	public void nextTrack() {
		if (repeatSong) {
			if (player.getPlayingTrack() != null) {
				this.lastTrack = player.getPlayingTrack();
			}
			this.player.startTrack(lastTrack.makeClone(), false);
			return;
		}

		if (repeatQueue) {
			if (this.player.getPlayingTrack() != null) {
				queue.add(this.player.getPlayingTrack().makeClone());
			} else if (lastTrack != null) {
				queue.add(lastTrack.makeClone());
			}
		}

		this.player.startTrack(queue.poll(), false);
	}

	@Override
	public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
		if (lastTrack != null && !lastTrack.getIdentifier().matches(track.getIdentifier())) {
			this.lastTrack = track;
			player.playTrack(track.makeClone());
		}
	}

	@Override
	public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
		player.stopTrack();

		if (!queue.isEmpty()) {
			nextTrack();
		}
	}

}
