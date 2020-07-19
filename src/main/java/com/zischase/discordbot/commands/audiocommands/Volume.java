package com.zischase.discordbot.commands.audiocommands;

import com.zischase.discordbot.Config;
import com.zischase.discordbot.PostgreSQL;
import com.zischase.discordbot.audioplayer.MusicManager;
import com.zischase.discordbot.commands.Command;
import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.guildcontrol.GuildManager;
import net.dv8tion.jda.api.entities.Guild;
import org.jdbi.v3.core.Jdbi;

import java.util.Arrays;
import java.util.List;

public class Volume extends Command {

    public Volume() {
        super(false);
    }

    public static void setVolume(Guild guild) {

        int defaultVolume = Integer.parseInt(Config.get("VOLUME"));

        int set = Jdbi.create(PostgreSQL::getConnection).withHandle(handle -> {
            int r = handle.createQuery("SELECT volume FROM guild_settings WHERE guild_id = ?")
                    .bind(0, guild.getId())
                    .mapTo(Integer.class)
                    .findFirst()
                    .orElse(defaultVolume);
            handle.close();
            return r;
        });

        GuildManager.getContext(guild)
                .getMusicManager()
                .getPlayer()
                .setVolume(set);
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("Vol", "V");
    }

    @Override
    public String getHelp() {
        return "Volume [amount] ~ Sets the volume. 0-100 | Leave blank for current vol.";
    }

    @Override
    public void handle(CommandContext ctx) {
        Guild guild =ctx.getGuild();
        List<String> args = ctx.getArgs();
        MusicManager manager = GuildManager.getContext(guild).getMusicManager();

        if (args.isEmpty()) {
            ctx.getEvent().getChannel()
                    .sendMessage("Volume is currently at: `" + manager.getPlayer().getVolume() + "`")
                    .queue();
            return;
        }

        boolean invalidNum = (Integer.parseInt(args.get(0)) <= 0) && (Integer.parseInt(args.get(0)) >= 100);

        if (!args.get(0).matches("\\d+") || invalidNum) {
            ctx.getEvent().getChannel()
                    .sendMessage("Please input a number between 0-100")
                    .queue();
            return;
        }

        Jdbi.create(PostgreSQL::getConnection).useHandle(handle ->
                handle.execute("UPDATE guild_settings SET volume = ? WHERE guild_id = ?", args.get(0), guild.getId()));

        manager.getPlayer().setVolume(
                Integer.parseInt(args.get(0)));

        ctx.getEvent().getChannel()
                .sendMessage("The volume has been set to `"+ manager.getPlayer().getVolume() +"`")
                .queue();
    }
}
