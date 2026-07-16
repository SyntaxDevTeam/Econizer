package pl.syntaxdevteam.listeners;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jetbrains.annotations.NotNull;

public class CoreManager extends ListenerAdapter {

    private final DatabaseManager db;

    public CoreManager(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        System.out.println("[System] Bot w pełni połączony! Uruchamiam procesy w tle...");

        ShopSyncTask shopSync = new ShopSyncTask(event.getJDA(), db);
        shopSync.startPolling();

        for (Guild guild : event.getJDA().getGuilds()) {
            WebAPIManager.reportGuildAction(guild.getId(), guild.getName(), "installed");
            registerCommands(guild);
            setupAnchorRole(guild);
        }
    }

    @Override
    public void onGuildJoin(@NotNull GuildJoinEvent event) {
        WebAPIManager.reportGuildAction(event.getGuild().getId(), event.getGuild().getName(), "installed");
        registerCommands(event.getGuild());
        setupAnchorRole(event.getGuild());
    }

    @Override
    public void onGuildLeave(@NotNull GuildLeaveEvent event) {
        WebAPIManager.reportGuildAction(event.getGuild().getId(), event.getGuild().getName(), "removed");
    }

    private void setupAnchorRole(Guild guild) {
        boolean hasAnchor = guild.getRoles().stream().anyMatch(r -> r.getName().contains("ECONIZER_ANCHOR"));
        if (!hasAnchor) {
            try {
                guild.createRole()
                        .setName("ECONIZER_ANCHOR [Move Above Users]")
                        .queue();
            } catch (Exception ignored) {}
        }
    }

    // ==========================================
    // SYSTEM POWIADOMIEŃ O NOWYM POZIOMIE (DLA KOMEND)
    // ==========================================
    public static void sendLevelUpNotification(Guild guild, MessageChannel fallbackChannel, String userId, int newLevel, GuildSettings settings) {
        MessageChannel targetChannel = fallbackChannel;

        try {
            if (settings.levelUpChannelId != null && !settings.levelUpChannelId.isEmpty()) {
                TextChannel configuredChannel = guild.getTextChannelById(settings.levelUpChannelId);
                if (configuredChannel != null) {
                    targetChannel = configuredChannel;
                }
            }
            targetChannel.sendMessage("🎉 Gratulacje <@" + userId + ">! Właśnie awansowałeś na **" + newLevel + "** poziom!").queue();
        } catch (Exception e) {
            try {
                fallbackChannel.sendMessage("🎉 Gratulacje <@" + userId + ">! Właśnie awansowałeś na **" + newLevel + "** poziom!").queue();
            } catch (Exception ignored) {}
        }
    }

    private void registerCommands(Guild guild) {
        String guildId = guild.getId();
        GuildSettings settings = db.getGuildSettings(guildId);

        guild.updateCommands().addCommands(
                localized(settings, Commands.slash("profile", "Check account status and level")
                                .addOption(OptionType.USER, "user", "User to check", false),
                        "Sprawdź stan konta i poziom", "Kogo sprawdzić"),

                localized(settings, Commands.slash("work", "Work to earn currency"),
                        "Podejmij pracę i zarób walutę", null),

                localized(settings, Commands.slash("daily", "Claim your daily reward"),
                        "Odbierz codzienną nagrodę", null),

                localized(settings, Commands.slash("pay", "Transfer currency to a player")
                                .addOptions(
                                        new OptionData(OptionType.USER, "player", "Recipient", true),
                                        new OptionData(OptionType.INTEGER, "amount", "Amount to transfer", true)),
                        "Przelej walutę graczowi", null),

                localized(settings, Commands.slash("addmoney", "[ADMIN] Add coins to a player")
                                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
                                .addOptions(
                                        new OptionData(OptionType.USER, "player", "Recipient", true),
                                        new OptionData(OptionType.INTEGER, "amount", "Amount to add", true)),
                        "[ADMIN] Dodaj monety", null),

                localized(settings, Commands.slash("bank", "Manage your bank account")
                                .addOptions(
                                        new OptionData(OptionType.STRING, "operation", "Operation type", true)
                                                .addChoice("Deposit", "deposit")
                                                .addChoice("Withdraw", "withdraw"),
                                        new OptionData(OptionType.INTEGER, "amount", "Amount", true)),
                        "Zarządzaj kontem bankowym", null),

                localized(settings, Commands.slash("shop", "Open the server shop"),
                        "Otwórz sklep serwerowy", null),

                localized(settings, Commands.slash("stats", "Global bot statistics"),
                        "Globalne statystyki bota", null),

                localized(settings, Commands.slash("coinflip", "Casino coinflip game")
                                .addOption(OptionType.INTEGER, "amount", "Bet amount", true),
                        "Gra losowa z kasynem", null),

                localized(settings, Commands.slash("pets", "Interactive pet panel"),
                        "Panel interaktywny Twojego zwierzaka", null),

                localized(settings, Commands.slash("heist", "Organize a team heist on the Server Bank!")
                                .addOption(OptionType.INTEGER, "team", "Team size (min 2, max 5)", false),
                        "Zorganizuj napad na Bank Serwera!", "Rozmiar ekipy (2-5, domyślnie 2)"),

                localized(settings, Commands.slash("rob", "Try to rob a player (cash only)")
                                .addOption(OptionType.USER, "victim", "Victim to rob", true),
                        "Spróbuj okraść gracza (tylko z gotówki)", "Kogo okradamy"),

                localized(settings, Commands.slash("bounty", "Post a bounty on a wanted player")
                                .addOptions(
                                        new OptionData(OptionType.USER, "player", "Wanted player", true),
                                        new OptionData(OptionType.INTEGER, "amount", "Reward amount", true)),
                        "Wystaw nagrodę za głowę poszukiwanego gracza", null),

                localized(settings, Commands.slash("hunt", "Hunt a wanted player for bounty")
                                .addOption(OptionType.USER, "player", "Target player", true),
                        "Spróbuj upolować poszukiwanego i zgarnąć bounty", "Na kogo polujesz"),

                localized(settings, Commands.slash("company", "Interactive labor office - manage companies"),
                        "Interaktywny Urząd Pracy - Zarządzaj firmami", null),

                localized(settings, Commands.slash("dashboard", "Open your player dashboard on the website"),
                        "Otwórz swój główny panel gracza na stronie WWW", null),

                localized(settings, Commands.slash("config", "[ADMIN] Open server management panel on the website")
                                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR)),
                        "[ADMIN] Otwórz panel zarządzania serwerem na stronie WWW", null),

                localized(settings, Commands.slash("reseteco", "[ADMIN] End season (FULL SERVER ECONOMY RESET)")
                                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
                                .addOptions(new OptionData(OptionType.BOOLEAN, "confirm", "Select True", true)),
                        "[ADMIN] Zakończ sezon (CAŁKOWITY RESET EKONOMII SERWERA)", "Wybierz True"),

                localized(settings, Commands.slash("market", "Manage your stock portfolio")
                                .addOptions(
                                        new OptionData(OptionType.STRING, "operation", "Operation type", true)
                                                .addChoice("📊 Check market", "check")
                                                .addChoice("📈 Buy shares", "buy")
                                                .addChoice("📉 Sell shares", "sell"),
                                        new OptionData(OptionType.STRING, "symbol", "Stock symbol", false),
                                        new OptionData(OptionType.INTEGER, "quantity", "Quantity", false)),
                        "Zarządzaj swoim portfelem akcji", null)
        ).queue();
    }

    private static SlashCommandData localized(GuildSettings settings, SlashCommandData cmd, String plDescription, String plOptionDesc) {
        if (settings.language != null && settings.language.equalsIgnoreCase("pl")) {
            cmd.setDescription(plDescription);
            if (plOptionDesc != null && !cmd.getOptions().isEmpty()) {
                cmd.getOptions().get(cmd.getOptions().size() - 1).setDescription(plOptionDesc);
            }
        }
        return cmd;
    }
}