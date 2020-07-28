package com.zischase.discordbot.commands;

import com.zischase.discordbot.commands.audiocommands.*;
import com.zischase.discordbot.commands.general.Clear;
import com.zischase.discordbot.commands.general.Help;
import com.zischase.discordbot.commands.general.Prefix;
import com.zischase.discordbot.commands.general.Spam;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CommandManager {
    private static final List<Command> commands = new ArrayList<>();
    private final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

    public List<Command> getCommandList() {
        return commands;
    }

    {
        addCommand(new Help(this));
        addCommand(new Radio());
        addCommand(new Play());
        addCommand(new Volume());
        addCommand(new Stop());
        addCommand(new Skip());
        addCommand(new Previous());
        addCommand(new NowPlaying());
        addCommand(new Youtube());
        addCommand(new Prefix());
        addCommand(new Playlist());
        addCommand(new Lyrics());
        addCommand(new Clear());
        addCommand(new Spam());
        addCommand(new Queue());
        addCommand(new Join());
        LOGGER.info("Command Manager Initialized");
    }

//    public CommandManager() {}

    public void execute(GuildMessageReceivedEvent event) {

        String prefix = new Prefix().getPrefix(event.getGuild());

        String[] split = event.getMessage().getContentRaw()
                .replaceFirst("(?i)" + Pattern.quote(prefix), "")
                .split("\\s");

        String invoke = split[0].toLowerCase();
        Command cmd = this.getCommand(invoke);

        if (cmd != null)
        {
            List<String> args = Arrays.asList(split).subList(1, split.length);
            CommandContext ctx = new CommandContext(event, args);
            cmd.execute(ctx);
        }
    }

    @Nullable
    public Command getCommand(String search) {

        for (Command cmd : commands) {

            List<String> aliases = cmd.getAliases().stream()
                    .map(String::toLowerCase)
                    .collect(Collectors.toList());

            if (cmd.getClass().getSimpleName().equalsIgnoreCase(search) || aliases.contains(search))
                return cmd;
        }

        return null;
    }

    private void addCommand(Command command) {
        boolean commandFound = commands
                .stream()
                .anyMatch(cmd -> cmd.getName().equalsIgnoreCase(command.getName()));

        if (commandFound) {
            LOGGER.warn("Command '{}' already present !", command.getName());
            return;
        }

        commands.add(command);
        LOGGER.info("New command {} added!", command.getName());
    }
}
