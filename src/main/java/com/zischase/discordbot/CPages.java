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
import java.util.List;
import java.util.concurrent.ExecutionException;
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

	public static void paginate(@Nonnull final Message msg, @Nonnull List<Page> pages, boolean refreshEmotes, boolean fastForward) throws ErrorResponseException, InsufficientPermissionException {
		if (!isActivated()) {
			throw new InvalidStateException();
		} else {
			final List<Page> pgs = Collections.unmodifiableList(pages);

			if (refreshEmotes) {
				clearReactions(msg);

				if (fastForward) {
					msg.addReaction(paginator.getEmotes().get(Emote.GOTO_FIRST)).submit();
				}
				msg.addReaction(paginator.getEmotes().get(Emote.PREVIOUS)).submit();
				msg.addReaction(paginator.getEmotes().get(Emote.NEXT)).submit();
				if (fastForward) {
					msg.addReaction(paginator.getEmotes().get(Emote.GOTO_LAST)).submit();
				}
			}

			handler.addEvent((msg.getChannelType().isGuild() ? msg.getGuild().getId() : msg.getPrivateChannel().getId()) + msg.getId(), new Consumer<>() {
				private final int maxP = pgs.size() - 1;
				private int p = 0;

				public void accept(GenericMessageReactionEvent event) {
					event.retrieveUser().submit().thenAccept((u) -> {
						Message msgx = null;

						try {
							msgx = event.retrieveMessage().submit().get();
						} catch (ExecutionException | InterruptedException ignored) {
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
							}else if (CPages.checkEmote(reaction, Emote.GOTO_FIRST)) {
								pg = pgs.get(0);
								CPages.updatePage(msgx, pg);
							}else if (CPages.checkEmote(reaction, Emote.GOTO_LAST)) {
								pg = pgs.get(maxP);
								CPages.updatePage(msgx, pg);
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
				msg.editMessage((Message) p.getContent()).queue();
			} else if (p.getContent() instanceof MessageEmbed) {
				msg.editMessage((MessageEmbed) p.getContent()).queue();
			}

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

				for (MessageReaction r : msg.getReactions()) {
					r.removeReaction().submit();
				}
			}
		} catch (IllegalStateException | InsufficientPermissionException var4) {

			for (MessageReaction r : msg.getReactions()) {
				r.removeReaction().submit();
			}
		}

	}

	public static void clearReactions(Message msg, Consumer<Void> callback) {
		try {
			if (msg.getChannel().getType().isGuild()) {
				msg.clearReactions().submit();
			} else {

				for (MessageReaction r : msg.getReactions()) {
					r.removeReaction().submit();
				}
			}
		} catch (IllegalStateException | InsufficientPermissionException var5) {

			for (MessageReaction r : msg.getReactions()) {
				r.removeReaction().submit();
			}
		}

		callback.accept(null);
	}
}
