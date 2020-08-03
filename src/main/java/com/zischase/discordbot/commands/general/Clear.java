package com.zischase.discordbot.commands.general;

import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import net.dv8tion.jda.api.entities.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Clear extends Command {

    private static final Logger LOGGER = LoggerFactory.getLogger(Clear.class);

    public Clear() {
        super(false);
    }

    @Override
    public String getHelp() {
        return "Clear [amount] ~ Deletes the last x messages from this channel. Default will purge";
    }

    @Override
    public void handle(CommandContext ctx) {
        int numOfMsgs = 0;

        if (ctx.getArgs().isEmpty())
            numOfMsgs = Integer.MAX_VALUE;
        else if (ctx.getArgs().get(0).matches("\\d+"))
            numOfMsgs = Integer.parseInt(ctx.getArgs().get(0));


        ctx.getChannel()
                .deleteMessageById(ctx.getEvent().getMessage().getId())
                .complete();


        int delete = numOfMsgs;

        while (numOfMsgs > 0) {
            if (delete >= 100) {
                delete = delete / 10;
                continue;
            }

            List<Message> messages = ctx.getChannel()
                    .getHistory()
                    .retrievePast(delete)
                    .complete();

            messages.removeIf(message ->
                    message.getTimeCreated()
                            .isBefore(OffsetDateTime.now().minusDays(14)));

            if (messages.size() == 1) {
                ctx.getChannel()
                        .deleteMessageById(messages.get(0).getId())
                        .complete();
                break;
            }
            else if (messages.isEmpty())
                break;

            ctx.getChannel()
                    .deleteMessages(messages)
                    .complete();
            numOfMsgs = numOfMsgs - delete;
        }

        ctx.getChannel()
                .sendMessage("Messages cleared !")
                .complete()
                .delete()
                .queueAfter(2000, TimeUnit.MILLISECONDS);
    }



}
