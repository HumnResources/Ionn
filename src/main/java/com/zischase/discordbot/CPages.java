package com.zischase.discordbot;

import com.github.ygimenez.exception.AlreadyActivatedException;
import com.github.ygimenez.exception.InvalidHandlerException;
import com.github.ygimenez.exception.InvalidStateException;
import com.github.ygimenez.exception.NullPageException;
import com.github.ygimenez.model.Page;
import com.github.ygimenez.model.Paginator;
import com.github.ygimenez.type.Emote;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.events.message.react.GenericMessageReactionEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.sharding.ShardManager;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;


/*
*
* 	This is a slimmed down version of Pages.class provided
*	by https://github.com/ygimenez/Pagination-Utils
*
*	Added flag to not delete existing reactions.
*
* */

public class CPages {
	private static final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
	private static final CMessageHandler           handler  = new CMessageHandler();
	private static       Paginator                paginator;

	public CPages() {
	}

	public static void activate(@Nonnull Paginator paginator) throws InvalidHandlerException {
		if (isActivated()) {
			throw new AlreadyActivatedException();
		} else {
			Object hand = paginator.getHandler();
			if (hand instanceof JDA) {
				((JDA) hand).addEventListener(handler);
			} else {
				if (!(hand instanceof ShardManager)) {
					throw new InvalidHandlerException();
				}

				((ShardManager) hand).addEventListener(handler);
			}

			CPages.paginator = paginator;
		}
	}

	public static void deactivate() {
		if (isActivated()) {
			Object hand = paginator.getHandler();
			if (hand instanceof JDA) {
				((JDA) hand).removeEventListener(handler);
			} else if (hand instanceof ShardManager) {
				((ShardManager) hand).removeEventListener(handler);
			}

			paginator = null;
		}
	}

	public static boolean isActivated() {
		return paginator != null && paginator.getHandler() != null;
	}

	public static Paginator getPaginator() {
		return paginator;
	}

	public static CMessageHandler getHandler() {
		return handler;
	}

	public static void paginate(@Nonnull final Message msg, @Nonnull List<Page> pages, boolean refreshEmotes, int time, TimeUnit unit) throws ErrorResponseException, InsufficientPermissionException {
		if (!isActivated()) {
			throw new InvalidStateException();
		} else {
			final List<Page> pgs = Collections.unmodifiableList(pages);

			if (refreshEmotes) {
				clearReactions(msg);
				msg.addReaction(paginator.getEmotes().get(Emote.PREVIOUS)).submit();
				msg.addReaction(paginator.getEmotes().get(Emote.CANCEL)).submit();
				msg.addReaction(paginator.getEmotes().get(Emote.NEXT)).submit();
			}

			handler.addEvent((msg.getChannelType().isGuild() ? msg.getGuild().getId() : msg.getPrivateChannel().getId()) + msg.getId(), new Consumer<>() {
				private final AtomicReference<ScheduledFuture<?>> timeout = new AtomicReference(null);
				private final int maxP = pgs.size() - 1;
				private int p = 0;
				private final Consumer<Void> success = (s) -> {
					if (this.timeout.get() != null) {
						this.timeout.get().cancel(true);
					}

					CPages.handler.removeEvent(msg);
					if (CPages.paginator.isDeleteOnCancel()) {
						msg.delete().submit();
					}

				};

				{
					CPages.setTimeout(this.timeout, this.success, msg, time, unit);
				}

				public void accept(GenericMessageReactionEvent event) {
					event.retrieveUser().submit().thenAccept((u) -> {
						Message msgx = null;

						try {
							msgx = event.retrieveMessage().submit().get();
						} catch (ExecutionException | InterruptedException var7) {
						}

						if (!u.isBot() && msgx != null && event.getMessageId().equals(msgx.getId())) {
							MessageReaction.ReactionEmote reaction = event.getReactionEmote();
							Page                          pg;
							if (CPages.checkEmote(reaction, Emote.PREVIOUS)) {
								if (this.p > 0) {
									--this.p;
									pg = pgs.get(this.p);
									CPages.updatePage(msgx, pg);
								}
							} else if (CPages.checkEmote(reaction, Emote.NEXT)) {
								if (this.p < this.maxP) {
									++this.p;
									pg = pgs.get(this.p);
									CPages.updatePage(msgx, pg);
								}
							} else if (CPages.checkEmote(reaction, Emote.CANCEL)) {
								CPages.clearReactions(msgx, this.success);
							}

							if (event.isFromGuild() && (CPages.paginator == null || CPages.paginator.isRemoveOnReact())) {
								event.getReaction().removeReaction(u).submit();
							}

						}
					});
				}
			});
		}
	}

	public static void updatePage(@Nonnull Message msg, Page p) {
		if (p == null) {
			throw new NullPageException();
		} else {
			if (p.getContent() instanceof Message) {
				msg.editMessage((Message) p.getContent()).submit();
			} else if (p.getContent() instanceof MessageEmbed) {
				msg.editMessage((MessageEmbed) p.getContent()).submit();
			}

		}
	}

	private static void setTimeout(AtomicReference<ScheduledFuture<?>> timeout, Consumer<Void> success, Message msg, int time, TimeUnit unit) {
		if (timeout.get() != null) {
			timeout.get().cancel(true);
		}

		try {
			timeout.set(executor.schedule(() -> {
				msg.clearReactions().submit().thenAccept(success);
			}, time, unit));
		} catch (IllegalStateException | InsufficientPermissionException var6) {
			timeout.set(executor.schedule(() -> {
				msg.getChannel().retrieveMessageById(msg.getId()).submit().thenCompose((m) -> {
					CompletableFuture<?>[] removeReaction = new CompletableFuture[m.getReactions().size()];

					for (int i = 0; i < m.getReactions().size(); ++i) {
						MessageReaction r = m.getReactions().get(i);
						if (r.isSelf()) {
							removeReaction[i] = r.removeReaction().submit();
						}
					}

					return CompletableFuture.allOf(removeReaction).thenAccept(success);
				});
			}, time, unit));
		}

	}

	private static boolean checkEmote(MessageReaction.ReactionEmote reaction, Emote emote) {
		if (reaction.isEmoji() && reaction.getName().equals(paginator.getEmote(emote))) {
			return true;
		} else {
			return reaction.isEmote() && reaction.getId().equals(paginator.getEmote(emote));
		}
	}

	public static void clearReactions(Message msg) {
		try {
			if (msg.getChannel().getType().isGuild()) {
				msg.clearReactions().submit();
			} else {
				Iterator var1 = msg.getReactions().iterator();

				while (var1.hasNext()) {
					MessageReaction r = (MessageReaction) var1.next();
					r.removeReaction().submit();
				}
			}
		} catch (IllegalStateException | InsufficientPermissionException var4) {
			Iterator var2 = msg.getReactions().iterator();

			while (var2.hasNext()) {
				MessageReaction r = (MessageReaction) var2.next();
				r.removeReaction().submit();
			}
		}

	}

	public static void clearReactions(Message msg, Consumer<Void> callback) {
		try {
			if (msg.getChannel().getType().isGuild()) {
				msg.clearReactions().submit();
			} else {
				Iterator var2 = msg.getReactions().iterator();

				while (var2.hasNext()) {
					MessageReaction r = (MessageReaction) var2.next();
					r.removeReaction().submit();
				}
			}
		} catch (IllegalStateException | InsufficientPermissionException var5) {
			Iterator var3 = msg.getReactions().iterator();

			while (var3.hasNext()) {
				MessageReaction r = (MessageReaction) var3.next();
				r.removeReaction().submit();
			}
		}

		callback.accept(null);
	}
}
