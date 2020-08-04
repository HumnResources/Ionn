package com.zischase.discordbot.commands;

import com.zischase.discordbot.Listener;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;

import javax.annotation.Nonnull;
import java.awt.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ResultSelector {
    private final int delayMS = 10000; // Where delay is the duration until listener gets terminated.
    private final int searchTimeOffset = 2500; // Where offset is approximate time to query result.
    private final List<ISearchable> searches;
    private ISearchable result = null;

    public ResultSelector(List<ISearchable> searches) {
        this.searches = searches;
    }

    public CompletableFuture<ISearchable> getChoice(GuildMessageReceivedEvent event) {
        printList(event.getChannel());

        Listener listener = new Listener() {
            @Override
            public void onGuildMessageReceived(@Nonnull GuildMessageReceivedEvent tmpEvent) {
                Message tmpMessage = tmpEvent.getMessage();
                String choice = tmpMessage.getContentDisplay();

                if (tmpEvent.getAuthor() == event.getAuthor()) {
                    if (tmpMessage.getChannel() == event.getChannel() && choice.matches("(\\d+).?")) {

                        int num = Integer.parseInt(choice);
                        if (num > 0 && num <= searches.size()) {

                            result = searches.get(num - 1);
                        }
                    }
                }
            }
        };

        event.getJDA().addEventListener(listener);

            return new CompletableFuture<ISearchable>().completeAsync(() -> {

            while (LocalDateTime.now().isAfter(LocalDateTime.now().plusSeconds(delayMS/1000))) {
                if (result != null) {
                    event.getJDA().removeEventListener(listener);
                    return result;
                }
            }

            if (event.getJDA().getEventManager().getRegisteredListeners().contains(listener))
                event.getJDA().removeEventListener(listener);

            return null;

        }, Listener.getExecutor());
    }

    private void printList(TextChannel textChannel) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(Color.DARK_GRAY);

        if (searches.isEmpty())
            embed.appendDescription("No results found !");
        else {
            embed.setFooter("Don't take long!");
            String length = "";
            for (ISearchable result : searches) {
                embed.appendDescription((searches.indexOf(result)+1) + ". `" + result.getName()+"`");
                embed.appendDescription(System.lineSeparator());

                length = length.concat((searches.indexOf(result)+1) + ". `" + result.getName()+"`" + System.lineSeparator());

                if (length.length() >= 2000) {
                    length = "";
                    textChannel.sendMessage(embed.build())
                            .queue();
                    embed = new EmbedBuilder();
                    embed.setColor(Color.DARK_GRAY);
                }
            }
        }
        Message message = new MessageBuilder()
                .setEmbed(embed.build())
                .build();

        textChannel.sendMessage(message)
                .complete()
                .delete()
                .queueAfter(delayMS + searchTimeOffset, TimeUnit.MILLISECONDS);
    }
}