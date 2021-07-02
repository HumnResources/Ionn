package com.zischase.discordbot;

import com.zischase.discordbot.commands.CommandContext;
import com.zischase.discordbot.commands.CommandThreadFactory;
import com.zischase.discordbot.guildcontrol.GuildContext;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.ShutdownEvent;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public class CommandEventListener extends ListenerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommandEventListener.class);
    private final AtomicReference<Member> proxyCallMember = new AtomicReference<>(null);
    private ThreadPoolExecutor poolExecutor;

    public CommandEventListener() {
    }

    @Override
    public void onReady(@Nonnull ReadyEvent event) {
        event.getJDA()
                .getGuilds()
                .forEach(GuildContext::new);

        LOGGER.info("{} is ready", event.getJDA()
                .getSelfUser()
                .getAsTag());

        JDA jda = event.getJDA();

        int defaultPoolCount = Integer.parseInt(Config.get("DEFAULT_COMMAND_THREADS"));
        int POOL_COUNT = jda.getGuilds().size() * 2;

        if (POOL_COUNT > defaultPoolCount)
        {
            poolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(POOL_COUNT);
        }
        else
        {
            poolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(defaultPoolCount);
        }

        poolExecutor.setThreadFactory(new CommandThreadFactory(poolExecutor));
        poolExecutor.setKeepAliveTime(30000, TimeUnit.MILLISECONDS);
    }

    @Override
    public void onShutdown(@NotNull ShutdownEvent event) {
        LOGGER.info("Deleting event listener.");
        event.getJDA().removeEventListener(this);
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        event.isAcknowledged();

        event.deferReply(false).queue((m) -> {
            MessageBuilder mb = new MessageBuilder();

            /* Using getGuildChannel() instead of getGuild() directly from event. This ensures we have null safety*/
            mb.append(DBQueryHandler.get(event.getGuildChannel().getGuild().getId(), "prefix"));
            mb.append(event.getName());

            if (event.getSubcommandName() != null) {
                mb.append(" ".concat(event.getSubcommandName()));
            }

            event.getOptions().forEach((opt) -> {
                if (opt.getType() == OptionType.STRING) {
                    mb.append(" ".concat(opt.getAsString()));
                }
            });

            /* Ensure we skip detection of bot message in channel until we start processing the command. */
            proxyCallMember.set(event.getMember());

            /* Delete the command issued by the bot */
            event.getChannel().sendMessage(mb.build()).queue((cmdMsg) -> cmdMsg.delete().queue());
            event.getHook().deleteOriginal().queue();
        });
    }

    @Override
    public void onGuildMessageReceived(@Nonnull GuildMessageReceivedEvent event) {
        if (proxyCallMember.get() == null && event.getAuthor().isBot() || event.isWebhookMessage()) {
            return;
        }

        String prefix = DBQueryHandler.get(event.getGuild().getId(), "prefix");
        String raw = event.getMessage().getContentRaw();

        if (raw.startsWith(prefix)) {
            String[] msgArr = raw.replaceFirst("(?i)" + Pattern.quote(prefix), "").split("\\s");
            List<String> args = Arrays.asList(msgArr).subList(1, msgArr.length);

            if (event.getAuthor().getId().equals(Config.get("OWNER_ID"))) {
                if (msgArr[0].equalsIgnoreCase("reslash")) {
                    resetSlashCommands(event.getJDA());
                    LOGGER.info("Resetting slash commands.");
                    return;
                }
            }

            CommandContext ctx = proxyCallMember.get() != null ? new CommandContext(event, args, proxyCallMember.get()) : new CommandContext(event, args);
            proxyCallMember.set(null);
            poolExecutor.execute(() -> GuildContext.get(ctx.getGuild().getId()).commandHandler().invoke(ctx));
        }
    }

    @Override
    public void onGuildJoin(@NotNull GuildJoinEvent event) {
        new GuildContext(event.getGuild());
        LOGGER.info("Joined a new guild : " + event.getGuild().getName() + " " + event.getGuild().getId());
    }

    @Override
    public void onGuildLeave(@NotNull GuildLeaveEvent event) {
        GuildContext.get(event.getGuild().getId());
        super.onGuildLeave(event);
    }

    private void resetSlashCommands(JDA jda) {

        CompletableFuture.runAsync(() -> {
            jda.retrieveCommands().queue(commands -> commands.forEach(command -> command.delete().queue()));
            jda.getGuilds()
                    .forEach(guild -> guild.retrieveCommands()
                            .queue(commands -> commands.forEach(command -> command.delete().queue())));
        }).thenRun(() -> {
            for (Guild g : jda.getGuilds()) {
                GuildContext.get(g.getId())
                        .commandHandler()
                        .getCommandList()
                        .forEach(command -> {
                            if (command.getCommandData() != null) {
                                g.upsertCommand(command.getCommandData()).queue();
                            }
                        });
            }
        });
    }
}