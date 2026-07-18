package pl.syntaxdevteam.listeners;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class LevelingManager extends ListenerAdapter {
    private final DatabaseManager db;
    private final Random random = new Random();
    private final Map<String, Long> chatCooldown = new ConcurrentHashMap<>();

    public LevelingManager(DatabaseManager db) { this.db = db; }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || !event.isFromGuild()) return;
        String guildId = event.getGuild().getId();
        String userId = event.getAuthor().getId();
        GuildSettings settings = db.getGuildSettings(guildId);
        if (!settings.economyEnabled) return;

        long cur = System.currentTimeMillis();
        String key = guildId + "-" + userId;
        if (chatCooldown.size() > 5000) chatCooldown.clear();

        if (!chatCooldown.containsKey(key) || cur >= chatCooldown.get(key)) {
            double[] mults = db.getActiveMultipliers(guildId, userId);
            int exp = (int) ((random.nextInt(16) + 10) * mults[1]);
            int newLvl = db.addExpAndCheckLevel(guildId, userId, exp);

            if (newLvl > 0) {
                int reward = newLvl * 50;
                EmbedBuilder eb = new EmbedBuilder().setColor(Color.decode("#E67E22"))
                        .setTitle(LanguageManager.t(settings, "levelup_chat_title"))
                        .setDescription(LanguageManager.t(settings, "levelup_chat_desc", userId, newLvl, reward, settings.currencyEmoji))
                        .setThumbnail(event.getAuthor().getEffectiveAvatarUrl());
                TextChannel ch = (settings.levelUpChannelId != null)
                        ? event.getGuild().getTextChannelById(settings.levelUpChannelId)
                        : event.getChannel().asTextChannel();
                if (ch != null && ch.canTalk()) ch.sendMessageEmbeds(eb.build()).queue();
            }
            chatCooldown.put(key, cur + 60000);
        }
    }
}