package com.zischase.discordbot.commands;

import com.zischase.discordbot.Listener;
import com.zischase.discordbot.audioplayer.Audio;
import com.zischase.discordbot.guildcontrol.GuildManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import java.awt.*;
import java.util.List;

public class ResultSelector {

    GuildMessageReceivedEvent event;
    private final List<Audio> searchList;

    public ResultSelector(GuildMessageReceivedEvent event, @NotNull List<Audio> list) {
        this.event = event;
        this.searchList = list;
    }

    public void setListener() {
        printList();
        addListener();
    }

    private void addListener() {
        event.getJDA().addEventListener(new Listener() {
            @Override
            public void onGuildMessageReceived(@Nonnull GuildMessageReceivedEvent tmpEvent) {
                Message tmpMessage = tmpEvent.getMessage();
                String choice = tmpMessage.getContentDisplay();

                if (tmpEvent.getAuthor() == event.getAuthor()) {
                    if (tmpMessage.getChannel() == event.getChannel() && choice.matches("(\\d+).?")) {

                        int num = Integer.parseInt(choice);
                        if (num > 0 && num <= searchList.size()) {
                            GuildManager.getContext(event.getGuild())
                                    .getMusicManager()
                                    .getScheduler()
                                    .load(event.getChannel(), event.getMember(), searchList.get(num-1));
                        }
                    }
                    event.getJDA().removeEventListener(this);
                }
            }
        });
    }

    private void printList() {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(Color.DARK_GRAY);

        if (searchList.isEmpty())
            embed.appendDescription("No results found !");
        else {
            for (Audio result : searchList) {
                embed.appendDescription((searchList.indexOf(result)+1) + ". `" + result.getName()+"`");
                embed.appendDescription(System.lineSeparator());
            }
        }
        event.getChannel().sendMessage(embed.build()).queue();
    }
}
