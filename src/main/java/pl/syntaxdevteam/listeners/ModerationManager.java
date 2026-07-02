package pl.syntaxdevteam.listeners;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.time.Duration;
import java.util.List;

public class ModerationManager extends ListenerAdapter {

    private final DatabaseManager db;

    public ModerationManager(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || !event.isFromGuild()) return;

        String guildId = event.getGuild().getId();
        GuildSettings settings = db.getGuildSettings(guildId);

        // --- AUTOMOD: Wykrywanie i karanie za zakazane słowa / linki ---
        // Admini są ignorowani przez AutoModa
        if (settings.automodEnabled && event.getMember() != null && !event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            List<String> blockedWords = db.getBlockedWords(guildId);
            String msgContent = event.getMessage().getContentRaw().toLowerCase();

            boolean hasBadWord = false;
            for (String word : blockedWords) {
                if (msgContent.contains(word.toLowerCase())) {
                    hasBadWord = true;
                    break;
                }
            }

            if (hasBadWord) {
                // 1. Kasujemy wiadomość
                event.getMessage().delete().queue();

                // 2. Ostrzeżenie na czacie (znika po 5 sekundach)
                event.getChannel().sendMessage("<@" + event.getAuthor().getId() + ">, ta wiadomość zawierała zablokowane słowo lub link i została usunięta!")
                        .queue(m -> m.delete().queueAfter(5, java.util.concurrent.TimeUnit.SECONDS));

                // 3. Kara: Timeout na 5 minut (Działa tylko, jeśli bot ma uprawnienie Mute/Timeout)
                try {
                    event.getGuild().timeoutFor(event.getMember(), Duration.ofMinutes(5)).queue(null, err -> {});
                } catch (Exception ignored) {
                    // Ciche zignorowanie błędu, jeśli bot nie ma roli wyższej od gracza
                }
            }
        }
    }

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        String guildId = event.getGuild().getId();
        GuildSettings settings = db.getGuildSettings(guildId);

        // --- AUTOROLE: Nadawanie domyślnej rangi nowemu graczowi ---
        if (settings.autoroleId != null && !settings.autoroleId.isEmpty()) {
            Role role = event.getGuild().getRoleById(settings.autoroleId);
            if (role != null) {
                event.getGuild().addRoleToMember(event.getMember(), role).queue(null, err -> {});
            }
        }

        // --- POWITANIA: Wysyłanie wiadomości powitalnej na konkretny kanał ---
        if (settings.welcomeChannelId != null && !settings.welcomeChannelId.isEmpty()) {
            TextChannel channel = event.getGuild().getTextChannelById(settings.welcomeChannelId);
            if (channel != null && channel.canTalk()) {

                // Zmieniamy {user} na faktyczny ping gracza z bazy danych
                String msg = settings.welcomeMessage.replace("{user}", event.getMember().getAsMention());

                EmbedBuilder embed = new EmbedBuilder()
                        .setColor(Color.decode("#2ECC71"))
                        .setAuthor("Witamy na serwerze!", null, event.getUser().getEffectiveAvatarUrl())
                        .setDescription(msg)
                        .setThumbnail(event.getUser().getEffectiveAvatarUrl());

                channel.sendMessageEmbeds(embed.build()).queue();
            }
        }
    }
}