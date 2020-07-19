package com.zischase.discordbot.commands.general;

import com.zischase.discordbot.Config;
import com.zischase.discordbot.PostgreSQL;
import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import net.dv8tion.jda.api.entities.Guild;
import org.jdbi.v3.core.Jdbi;

import java.util.List;

public class Prefix extends Command {
    private String prefix;

    public Prefix() {
        super(false);
        prefix = Config.get("PREFIX");
    }

    public String getPrefix(Guild guild) {

        String result = Jdbi.create(PostgreSQL::getConnection).withHandle(handle -> {
            String r = handle.createQuery("SELECT prefix FROM guild_settings where guild_id = ?")
                    .bind(0, guild.getId())
                    .mapTo(String.class)
                    .findFirst()
                    .orElse(null);
            handle.close();
            return r;
        });

        if (result != null)
            prefix = result;

        return prefix;
    }

    @Override
    public String getHelp() {
        return "Prefix [newPrefix] ~ Sets new prefix for commands.";
    }

    @Override
    public void handle(CommandContext ctx) {
        Guild guild =ctx.getGuild();
        List<String> args = ctx.getArgs();

        if (args.isEmpty()) {
            prefix = getPrefix(guild);
            ctx.getEvent().getChannel()
                    .sendMessage("The current prefix is `" + prefix + "`")
                    .queue();
            return;
        }

        Jdbi.create(PostgreSQL::getConnection).useHandle(handle ->
                handle.execute("UPDATE guild_settings SET prefix = ? WHERE guild_id = ?", args.get(0), guild.getId()));

        ctx.getEvent().getChannel()
                .sendMessage("The new prefix has been set to `" + args.get(0) + "`")
                .queue();
    }
}
