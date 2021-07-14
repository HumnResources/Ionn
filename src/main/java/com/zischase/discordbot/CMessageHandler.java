//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zischase.discordbot;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.react.GenericMessageReactionEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.Consumer;

/*
 *
 * 	This is a compatible version of MessageHandler.class provided
 *	by https://github.com/ygimenez/Pagination-Utils for use with CPages.java.
 *
 *	Added flag to not delete existing reactions on message for pagination.
 *
 * */

public class CMessageHandler extends ListenerAdapter {
	private final Map<String, Consumer<GenericMessageReactionEvent>> events = new HashMap();
	private final Set<String> locks = new HashSet();

	public CMessageHandler() {
	}

	public void addEvent(String id, Consumer<GenericMessageReactionEvent> act) {
		this.events.put(id, act);
	}

	public void removeEvent(Message msg) {
		Map var10000;
		String var10001;
		switch(msg.getChannelType()) {
			case TEXT:
				var10000 = this.events;
				var10001 = msg.getGuild().getId();
				var10000.remove(var10001 + msg.getId());
				break;
			case PRIVATE:
				var10000 = this.events;
				var10001 = msg.getPrivateChannel().getId();
				var10000.remove(var10001 + msg.getId());
		}

	}

	public Map<String, Consumer<GenericMessageReactionEvent>> getEventMap() {
		return Collections.unmodifiableMap(this.events);
	}

	public void clear() {
		this.events.clear();
	}

	private void lock(GenericMessageReactionEvent evt) {
		Set var10000 = this.locks;
		String var10001 = evt.getGuild().getId();
		var10000.add(var10001 + evt.getMessageId());
	}

	private void unlock(GenericMessageReactionEvent evt) {
		Set var10000 = this.locks;
		String var10001 = evt.getGuild().getId();
		var10000.remove(var10001 + evt.getMessageId());
	}

	private boolean isLocked(GenericMessageReactionEvent evt) {
		Set var10000;
		String var10001;
		switch(evt.getChannelType()) {
			case TEXT:
				var10000 = this.locks;
				var10001 = evt.getGuild().getId();
				return var10000.contains(var10001 + evt.getMessageId());
			case PRIVATE:
				var10000 = this.locks;
				var10001 = evt.getPrivateChannel().getId();
				return var10000.contains(var10001 + evt.getMessageId());
			default:
				return true;
		}
	}

	public void onMessageReactionAdd(@Nonnull MessageReactionAddEvent evt) {
		evt.retrieveUser().submit().thenAccept((u) -> {
			if (!u.isBot() && !this.isLocked(evt)) {
				try {
					if (CPages.getPaginator().isEventLocked()) {
						this.lock(evt);
					}

					Map var10000;
					String var10001;
					switch(evt.getChannelType()) {
						case TEXT:
							var10000 = this.events;
							var10001 = evt.getGuild().getId();
							if (var10000.containsKey(var10001 + evt.getMessageId())) {
								var10001 = evt.getGuild().getId();
								((Consumer)var10000.get(var10001 + evt.getMessageId())).accept(evt);
							}
							break;
						case PRIVATE:
							var10000 = this.events;
							var10001 = evt.getPrivateChannel().getId();
							if (var10000.containsKey(var10001 + evt.getMessageId())) {
								var10001 = evt.getPrivateChannel().getId();
								((Consumer)var10000.get(var10001 + evt.getMessageId())).accept(evt);
							}
					}

					if (CPages.getPaginator().isEventLocked()) {
						this.unlock(evt);
					}

				} catch (Exception var4) {
					if (CPages.getPaginator().isEventLocked()) {
						this.unlock(evt);
					}

					throw var4;
				}
			}
		});
	}

	public void onMessageDelete(@Nonnull MessageDeleteEvent evt) {
		Map var10000;
		String var10001;
		switch(evt.getChannelType()) {
			case TEXT:
				var10000 = this.events;
				var10001 = evt.getGuild().getId();
				var10000.remove(var10001 + evt.getMessageId());
				break;
			case PRIVATE:
				var10000 = this.events;
				var10001 = evt.getPrivateChannel().getId();
				var10000.remove(var10001 + evt.getMessageId());
		}

	}

	public void onMessageReactionRemove(@Nonnull MessageReactionRemoveEvent evt) {
		evt.retrieveUser().submit().thenAccept((u) -> {
			if (!u.isBot()) {
				boolean removeOnReact = CPages.isActivated() && CPages.getPaginator() == null || CPages.isActivated() && CPages.getPaginator().isRemoveOnReact();
				if (evt.isFromGuild() && removeOnReact) {
					evt.getPrivateChannel().retrieveMessageById(evt.getMessageId()).submit().thenAccept((msg) -> {
						Map var10000;
						String var10001;
						switch(evt.getChannelType()) {
							case TEXT:
								var10000 = this.events;
								var10001 = evt.getGuild().getId();
								if (var10000.containsKey(var10001 + msg.getId()) && !msg.getReactions().contains(evt.getReaction())) {
									if (evt.getReactionEmote().isEmoji()) {
										msg.addReaction(evt.getReactionEmote().getName()).submit();
									} else {
										msg.addReaction(evt.getReactionEmote().getEmote()).submit();
									}
								}
								break;
							case PRIVATE:
								var10000 = this.events;
								var10001 = evt.getPrivateChannel().getId();
								if (var10000.containsKey(var10001 + msg.getId()) && !msg.getReactions().contains(evt.getReaction())) {
									if (evt.getReactionEmote().isEmoji()) {
										msg.addReaction(evt.getReactionEmote().getName()).submit();
									} else {
										msg.addReaction(evt.getReactionEmote().getEmote()).submit();
									}
								}
						}

					});
				} else if (!this.isLocked(evt)) {
					try {
						if (CPages.getPaginator().isEventLocked()) {
							this.lock(evt);
						}

						Map var10000;
						String var10001;
						switch(evt.getChannelType()) {
							case TEXT:
								var10000 = this.events;
								var10001 = evt.getGuild().getId();
								if (var10000.containsKey(var10001 + evt.getMessageId())) {
									var10001 = evt.getGuild().getId();
									((Consumer)var10000.get(var10001 + evt.getMessageId())).accept(evt);
								}
								break;
							case PRIVATE:
								var10000 = this.events;
								var10001 = evt.getPrivateChannel().getId();
								if (var10000.containsKey(var10001 + evt.getMessageId())) {
									var10001 = evt.getPrivateChannel().getId();
									((Consumer)var10000.get(var10001 + evt.getMessageId())).accept(evt);
								}
						}

						if (CPages.getPaginator().isEventLocked()) {
							this.unlock(evt);
						}
					} catch (Exception var5) {
						if (CPages.getPaginator().isEventLocked()) {
							this.unlock(evt);
						}

						throw var5;
					}
				}

			}
		});
	}
}
