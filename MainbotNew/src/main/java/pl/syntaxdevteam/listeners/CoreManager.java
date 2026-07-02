package pl.syntaxdevteam.listeners;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
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
                Commands.slash("profil", "Sprawdź stan konta i poziom").addOption(OptionType.USER, "uzytkownik", "Kogo sprawdzić", false),
                Commands.slash("work", "Podejmij pracę i zarób walutę"),
                Commands.slash("daily", "Odbierz codzienną nagrodę"),
                Commands.slash("zaplac", "Przelej walutę graczowi").addOptions(new OptionData(OptionType.USER, "gracz", "Odbiorca", true), new OptionData(OptionType.INTEGER, "kwota", "Ilość", true)),
                Commands.slash("dodajkase", "[ADMIN] Dodaj monety").setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR)).addOptions(new OptionData(OptionType.USER, "gracz", "Odbiorca", true), new OptionData(OptionType.INTEGER, "kwota", "Kwota", true)),
                Commands.slash("bank", "Zarządzaj kontem bankowym").addOptions(new OptionData(OptionType.STRING, "operacja", "Wybierz", true).addChoice("Wpłać", "wplac").addChoice("Wypłać", "wyplac"), new OptionData(OptionType.INTEGER, "kwota", "Kwota", true)),
                Commands.slash("sklep", "Otwórz sklep serwerowy"),
                Commands.slash("statystyki", "Globalne statystyki bota"),
                Commands.slash("coinflip", "Gra z kasynem").addOption(OptionType.INTEGER, "kwota", "Stawka", true),
                Commands.slash("pets", "Panel interaktywny Twojego zwierzaka"),
                Commands.slash("napad", "Zorganizuj napad na Bank Serwera!"),
                Commands.slash("okradnij", "Spróbuj okraść gracza (tylko z gotówki)").addOption(OptionType.USER, "ofiara", "Kogo okradamy", true),
                Commands.slash("bounty", "Wystaw nagrodę za głowę poszukiwanego gracza").addOptions(new OptionData(OptionType.USER, "gracz", "Poszukiwany", true), new OptionData(OptionType.INTEGER, "kwota", "Nagroda", true)),
                Commands.slash("poluj", "Spróbuj upolować poszukiwanego i zgarnąć bounty").addOption(OptionType.USER, "gracz", "Na kogo polujesz", true),
                Commands.slash("firma", "Interaktywny Urząd Pracy - Zarządzaj firmami"),
                Commands.slash("dashboard", "Otwórz swój główny panel gracza na stronie WWW"),
                Commands.slash("konfiguracja", "[ADMIN] Otwórz panel zarządzania serwerem na stronie WWW").setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR)),
                Commands.slash("reseteco", "[ADMIN] Zakończ sezon (CAŁKOWITY RESET EKONOMII SERWERA)").setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR)).addOptions(new OptionData(OptionType.BOOLEAN, "potwierdzenie", "Wybierz True", true)),
                Commands.slash("gielda", "Zarządzaj swoim portfelem akcji")
                        .addOptions(
                                new OptionData(OptionType.STRING, "operacja", "Wybierz operację", true).addChoice("📊 Sprawdź rynek", "sprawdz").addChoice("📈 Kup akcje", "kup").addChoice("📉 Sprzedaj akcje", "sprzedaj"),
                                new OptionData(OptionType.STRING, "akcja", "Symbol spółki", false),
                                new OptionData(OptionType.INTEGER, "ilosc", "Ilość", false)
                        )
        ).queue();
    }
}