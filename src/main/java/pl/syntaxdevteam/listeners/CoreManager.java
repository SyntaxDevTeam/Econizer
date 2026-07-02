package pl.syntaxdevteam.listeners;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
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
        }
    }

    @Override
    public void onGuildJoin(@NotNull GuildJoinEvent event) {
        WebAPIManager.reportGuildAction(event.getGuild().getId(), event.getGuild().getName(), "installed");
        registerCommands(event.getGuild());
    }

    @Override
    public void onGuildLeave(@NotNull GuildLeaveEvent event) {
        WebAPIManager.reportGuildAction(event.getGuild().getId(), event.getGuild().getName(), "removed");
    }

    private void registerCommands(Guild guild) {
        guild.updateCommands().addCommands(
                localized(Commands.slash("profil", "Check account status and level")
                        .addOption(OptionType.USER, "uzytkownik", "User to check", false),
                        "Sprawdź stan konta i poziom", "Kogo sprawdzić"),
                localized(Commands.slash("work", "Work to earn currency"),
                        "Podejmij pracę i zarób walutę", null),
                localized(Commands.slash("daily", "Claim your daily reward"),
                        "Odbierz codzienną nagrodę", null),
                localized(Commands.slash("zaplac", "Transfer currency to a player")
                        .addOptions(
                                new OptionData(OptionType.USER, "gracz", "Recipient", true),
                                new OptionData(OptionType.INTEGER, "kwota", "Amount", true)),
                        "Przelej walutę graczowi", null),
                localized(Commands.slash("dodajkase", "[ADMIN] Add coins")
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
                        .addOptions(
                                new OptionData(OptionType.USER, "gracz", "Recipient", true),
                                new OptionData(OptionType.INTEGER, "kwota", "Amount", true)),
                        "[ADMIN] Dodaj monety", null),
                localized(Commands.slash("bank", "Manage your bank account")
                        .addOptions(
                                new OptionData(OptionType.STRING, "operacja", "Operation", true)
                                        .addChoice("Deposit", "wplac").addChoice("Withdraw", "wyplac"),
                                new OptionData(OptionType.INTEGER, "kwota", "Amount", true)),
                        "Zarządzaj kontem bankowym", null),
                localized(Commands.slash("sklep", "Open the server shop"),
                        "Otwórz sklep serwerowy", null),
                localized(Commands.slash("statystyki", "Global bot statistics"),
                        "Globalne statystyki bota", null),
                localized(Commands.slash("coinflip", "Casino coinflip game")
                        .addOption(OptionType.INTEGER, "kwota", "Bet amount", true),
                        "Gra z kasynem", null),
                localized(Commands.slash("pets", "Interactive pet panel"),
                        "Panel interaktywny Twojego zwierzaka", null),
                localized(Commands.slash("napad", "Organize a heist on the Server Bank!"),
                        "Zorganizuj napad na Bank Serwera!", null),
                localized(Commands.slash("okradnij", "Try to rob a player (cash only)")
                        .addOption(OptionType.USER, "ofiara", "Victim", true),
                        "Spróbuj okraść gracza (tylko z gotówki)", "Kogo okradamy"),
                localized(Commands.slash("bounty", "Post a bounty on a wanted player")
                        .addOptions(
                                new OptionData(OptionType.USER, "gracz", "Wanted player", true),
                                new OptionData(OptionType.INTEGER, "kwota", "Reward", true)),
                        "Wystaw nagrodę za głowę poszukiwanego gracza", null),
                localized(Commands.slash("poluj", "Hunt a wanted player for bounty")
                        .addOption(OptionType.USER, "gracz", "Target", true),
                        "Spróbuj upolować poszukiwanego i zgarnąć bounty", "Na kogo polujesz"),
                localized(Commands.slash("firma", "Interactive labor office - manage companies"),
                        "Interaktywny Urząd Pracy - Zarządzaj firmami", null),
                localized(Commands.slash("dashboard", "Open your player dashboard on the website"),
                        "Otwórz swój główny panel gracza na stronie WWW", null),
                localized(Commands.slash("konfiguracja", "[ADMIN] Open server management panel on the website")
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR)),
                        "[ADMIN] Otwórz panel zarządzania serwerem na stronie WWW", null),
                localized(Commands.slash("reseteco", "[ADMIN] End season (FULL SERVER ECONOMY RESET)")
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
                        .addOptions(new OptionData(OptionType.BOOLEAN, "potwierdzenie", "Select True", true)),
                        "[ADMIN] Zakończ sezon (CAŁKOWITY RESET EKONOMII SERWERA)", "Wybierz True"),
                localized(Commands.slash("gielda", "Manage your stock portfolio")
                        .addOptions(
                                new OptionData(OptionType.STRING, "operacja", "Operation", true)
                                        .addChoice("📊 Check market", "sprawdz")
                                        .addChoice("📈 Buy shares", "kup")
                                        .addChoice("📉 Sell shares", "sprzedaj"),
                                new OptionData(OptionType.STRING, "akcja", "Stock symbol", false),
                                new OptionData(OptionType.INTEGER, "ilosc", "Quantity", false)),
                        "Zarządzaj swoim portfelem akcji", null)
        ).queue();
    }

    private static net.dv8tion.jda.api.interactions.commands.build.SlashCommandData localized(
            net.dv8tion.jda.api.interactions.commands.build.SlashCommandData cmd,
            String plDescription, String plOptionDesc) {
        cmd.setDescriptionLocalization(DiscordLocale.POLISH, plDescription);
        if (plOptionDesc != null && !cmd.getOptions().isEmpty()) {
            cmd.getOptions().get(cmd.getOptions().size() - 1)
                    .setDescriptionLocalization(DiscordLocale.POLISH, plOptionDesc);
        }
        return cmd;
    }
}
