package com.zischase.discordbot.commands.general;

import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.commands.CommandManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;

import java.awt.*;
import java.util.List;

public class Help extends Command {
    private final CommandManager manager;

    public Help(CommandManager manager) {
        super(false);
        this.manager = manager;
    }

    private MessageEmbed printCommandList(Guild guild) {
        EmbedBuilder cmdList = new EmbedBuilder();
        cmdList.setColor(Color.CYAN);
        cmdList.setTitle("Command List");

        String prefix = new Prefix().getPrefix(guild);
        manager.getCommandList()
                .forEach(command -> cmdList.appendDescription("`" + prefix + command.getHelp() + "`")
                        .appendDescription(System.lineSeparator())
                );

        return cmdList.build();
    }

    @Override
    public String getHelp() {
        return "Help [command] ~ Get help about a specific command.";
    }

    @Override
    public void handle(CommandContext ctx) {
        TextChannel channel = ctx.getChannel();
        List<String> args = ctx.getArgs();

        if (args.isEmpty()) {
            channel.sendMessage(printCommandList(ctx.getGuild())).queue();
            return;
        }

        String cmdSearch = args.get(0);
        Command command = manager.getCommand(cmdSearch);

        if (command == null) {
            channel.sendMessage("Command " + cmdSearch + " not found.").queue();
            return;
        }
        channel.sendMessage(command.getHelp()).queue();
    }
}
