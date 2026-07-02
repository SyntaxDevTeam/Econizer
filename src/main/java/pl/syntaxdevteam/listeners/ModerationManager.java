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
                event.getChannel().sendMessage(LanguageManager.t(settings, "automod_warn", event.getAuthor().getId()))
                        .queue(m -> m.delete().queueAfter(5, java.util.concurrent.TimeUnit.SECONDS));

                try {
                    event.getGuild().timeoutFor(event.getMember(), Duration.ofMinutes(5)).queue(null, err -> {});
                } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        String guildId = event.getGuild().getId();
        GuildSettings settings = db.getGuildSettings(guildId);

        if (settings.autoroleId != null && !settings.autoroleId.isEmpty()) {
            Role role = event.getGuild().getRoleById(settings.autoroleId);
            if (role != null) {
                event.getGuild().addRoleToMember(event.getMember(), role).queue(null, err -> {});
            }
        }

        if (settings.welcomeChannelId != null && !settings.welcomeChannelId.isEmpty()) {
            TextChannel channel = event.getGuild().getTextChannelById(settings.welcomeChannelId);
            if (channel != null && channel.canTalk()) {
                String msg = settings.welcomeMessage.replace("{user}", event.getMember().getAsMention());

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
