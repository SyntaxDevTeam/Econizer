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
import java.util.concurrent.TimeUnit;

public class ModerationManager extends ListenerAdapter {

    private final DatabaseManager db;

    public ModerationManager(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        // Ignorujemy wiadomości od botów i prywatne
        if (event.getAuthor().isBot() || !event.isFromGuild()) return;

        String guildId = event.getGuild().getId();
        GuildSettings settings = db.getGuildSettings(guildId);

        // WEB PANEL READY: Sprawdza flagę automodEnabled na żywo
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
                event.getMessage().delete().queue();

                // Ponieważ to MessageReceivedEvent, nie możemy użyć setEphemeral.
                // Wysyłamy i usuwamy po 5s, aby nie śmiecić.
                event.getChannel().sendMessage(LanguageManager.t(settings, "automod_warn", event.getAuthor().getId()))
                        .queue(m -> m.delete().queueAfter(5, TimeUnit.SECONDS));

                try {
                    // Mute na 5 minut (wymaga uprawnień Moderate Members u bota)
                    event.getGuild().timeoutFor(event.getMember(), Duration.ofMinutes(5)).queue(null, err -> {});
                } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        String guildId = event.getGuild().getId();
        GuildSettings settings = db.getGuildSettings(guildId);

        // Nadawanie autoroli (zabezpieczone przed nullami z bazy z panelu WWW)
        if (settings.autoroleId != null && !settings.autoroleId.isEmpty()) {
            Role role = event.getGuild().getRoleById(settings.autoroleId);
            if (role != null) {
                event.getGuild().addRoleToMember(event.getMember(), role).queue(null, err -> {});
            }
        }

        // Wiadomość powitalna
        if (settings.welcomeChannelId != null && !settings.welcomeChannelId.isEmpty()) {
            TextChannel channel = event.getGuild().getTextChannelById(settings.welcomeChannelId);
            if (channel != null && channel.canTalk()) {

                // Zabezpieczenie: jeśli admin wyczyści wiadomość na WWW, wstawiamy bezpieczny fallback, aby zapobiec crashom bota
                String rawMsg = (settings.welcomeMessage != null && !settings.welcomeMessage.trim().isEmpty())
                        ? settings.welcomeMessage
                        : "Welcome {user} to our server!";

                String msg = rawMsg.replace("{user}", event.getMember().getAsMention());

                EmbedBuilder embed = new EmbedBuilder()
                        .setColor(Color.decode("#2ECC71"))
                        .setAuthor(LanguageManager.t(settings, "welcome_author"), null, event.getUser().getEffectiveAvatarUrl())
                        .setDescription(msg)
                        .setThumbnail(event.getUser().getEffectiveAvatarUrl());

                channel.sendMessageEmbeds(embed.build()).queue();
            }
        }
    }
}