package com.zischase.discordbot.commands.general;

import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import net.dv8tion.jda.api.entities.Message;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/*
 *  Recursively delete 100 messages at a time until none available.
 *
 *  Loop through deleting batches of 100 - Limit
 *
 *  Set deleteNumber to number within valid range, (1-100)
 *  by dividing if over 100.
 *  Check that the delete number is valid.
 *  Recursive call with number.
 *
 *  Get the last message sent and delete it.
 *
 *  If number of messages to delete was less than 100. Delete bulk.
 */

public class Clear extends Command {

    public Clear() {
        super(false);
    }

    @Override
    public String getHelp() {
        return "Clear [amount] ~ Deletes the last x messages from this channel. Default will purge";
    }

    @Override
    public void execute(CommandContext ctx) {



        if (ctx.getArgs().isEmpty()) {


//            List<Message> messages = ctx.getChannel().getHistory().retrievePast(500).complete();

//            messages.removeIf(message ->
//                    message.getTimeCreated()
//                            .isBefore(OffsetDateTime.now().minusDays(14))
//            );


            ctx.getChannel().purgeMessages(ctx.getChannel().getHistory().retrievePast(500).complete());

        }
//        else
//            ctx.getChannel()
//                    .sendMessage(ctx.getArgs().get(0) + " deleted!")
//                    .complete()
//                    .delete()
//                    .queueAfter(2, TimeUnit.SECONDS);
    }


    private void deleteMessage(CommandContext ctx, List<String> args) {
        if (args.isEmpty())
            while (true)
            {
                List<Message> messages = ctx.getChannel()
                        .getHistory()
                        .retrievePast(100)
                        .complete();
                messages.removeIf(msg -> msg.getTimeCreated()
                                .isBefore(OffsetDateTime.now().minusDays(14)));

                if (messages.isEmpty())
                    break;

                ctx.getChannel().purgeMessages(messages);
                deleteMessage(ctx, args);
            }
        else if (args.get(0).matches("\\d+"))
        {
            int deleteNumber = Integer.parseInt(args.get(0)) + 1;

            if (deleteNumber <= 0)
                ctx.getChannel()
                        .sendMessage("Well this is awkward. I can't delete 0 messages!")
                        .queue();

            else if (deleteNumber > 100)
            {
                int remainingMessages = deleteNumber;
                List<String> argsCopy = new ArrayList<>(List.copyOf(args));

                while (true) {
                    if (deleteNumber > 100)
                    {
                        deleteNumber = deleteNumber / 2;
                        continue;
                    }
                    if (deleteNumber > remainingMessages)
                        deleteNumber = remainingMessages;
                    if (deleteNumber == 0)
                        break;

                    List<Message> messages = ctx.getChannel()
                            .getHistory()
                            .retrievePast(deleteNumber)
                            .complete();

                    messages.removeIf(msg -> msg.getTimeCreated()
                            .isBefore(OffsetDateTime.now().minusDays(13).minusHours(23)));

                    if (messages.isEmpty())
                        break;

                    ctx.getChannel().purgeMessages(messages);
                    remainingMessages -= deleteNumber;

                    argsCopy.set(0, Integer.toString(deleteNumber));
                    deleteMessage(ctx, argsCopy);
                }
            }
            else if (deleteNumber == 1)
                ctx.getChannel()
                        .getHistory()
                        .retrievePast(1)
                        .complete()
                        .get(0)
                        .delete()
                        .complete();
            else
                ctx.getChannel()
                        .deleteMessages(ctx.getChannel()
                                .getHistory()
                                .retrievePast(deleteNumber)
                                .complete())
                        .complete();

        }
    }

}
