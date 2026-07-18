package pl.syntaxdevteam.listeners;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class CoreManager extends ListenerAdapter {

    private final DatabaseManager db;

    public CoreManager(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public void onGuildReady(@NotNull GuildReadyEvent event) {
        registerSlashCommands(event.getGuild());
    }

    private void registerSlashCommands(Guild guild) {
        List<CommandData> cmds = new ArrayList<>();

        // ==========================================
        // 1. EKONOMIA I PODSTAWY (EconomyManager)
        // ==========================================
        cmds.add(Commands.slash("work", "Idź do pracy i zarób trochę gotówki"));
        cmds.add(Commands.slash("daily", "Odbierz codzienną nagrodę ze strzeżonego sejfu"));
        cmds.add(Commands.slash("shop", "Otwórz oficjalny sklep serwerowy z przedmiotami i zwierzakami"));
        cmds.add(Commands.slash("dashboard", "Zarządzaj swoim profilem przez panel WWW"));
        cmds.add(Commands.slash("config", "Skonfiguruj ustawienia bota (Tylko Administracja)"));

        cmds.add(Commands.slash("reseteco", "Resetuje całą ekonomię serwera (Tylko Administracja)")
                .addOptions(new OptionData(OptionType.BOOLEAN, "confirm", "Potwierdź nieodwracalne usunięcie danych", true)));

        cmds.add(Commands.slash("bank", "Otwórz interaktywny panel Banku Centralnego (Lokaty i Kredyty)"));

        cmds.add(Commands.slash("profile", "Sprawdź profil gracza (poziom, kasa, firma, pet)")
                .addOptions(new OptionData(OptionType.USER, "user", "Opcjonalnie: gracz do sprawdzenia", false)));

        cmds.add(Commands.slash("pay", "Przelej gotówkę innemu graczowi z podatkiem")
                .addOptions(
                        new OptionData(OptionType.USER, "player", "Odbiorca przelewu", true),
                        new OptionData(OptionType.INTEGER, "amount", "Kwota", true)
                ));

        // ZMIANA: Usunięto wzmianki o hazardzie/kasynie
        cmds.add(Commands.slash("coinflip", "Rzuć monetą i zagraj o stawkę (Minigra)")
                .addOptions(new OptionData(OptionType.INTEGER, "amount", "Stawka gotówki lub z karty", true)));

        cmds.add(Commands.slash("addmoney", "Dodaj pieniądze graczowi (Tylko Administracja)")
                .addOptions(
                        new OptionData(OptionType.USER, "player", "Odbiorca środków", true),
                        new OptionData(OptionType.INTEGER, "amount", "Kwota", true)
                ));

        cmds.add(Commands.slash("removemoney", "Odejmij pieniądze graczowi (Tylko Administracja)")
                .addOptions(
                        new OptionData(OptionType.USER, "player", "Cel konfiskaty środków", true),
                        new OptionData(OptionType.INTEGER, "amount", "Kwota do odjęcia", true)
                ));

        // ==========================================
        // 2. KRYMINALNE I BOUNTY (CrimeManager)
        // ==========================================
        cmds.add(Commands.slash("bounty", "Wystaw list gończy na uciekiniera (max 30% jego majątku)")
                .addOptions(
                        new OptionData(OptionType.USER, "player", "Cel, na którego wyznaczasz nagrodę", true),
                        new OptionData(OptionType.INTEGER, "amount", "Kwota zlecenia z Twojej kieszeni", true)
                ));

        cmds.add(Commands.slash("wanted", "Otwórz Terminal Czarnego Rynku - Lista Listów Gończych"));

        cmds.add(Commands.slash("hunt", "Spróbuj wytropić uciekiniera za 10% puli nagrody (Wpisowe)")
                .addOptions(new OptionData(OptionType.USER, "player", "Poszukiwany gracz z listy /wanted", true)));

        cmds.add(Commands.slash("heist", "Zorganizuj zbrojny napad na skarbiec banku.")
                .addOptions(
                        new OptionData(OptionType.INTEGER, "investment", "Wpisowe zrzutka na sprzęt dla każdego gracza (min. 1000)", true),
                        new OptionData(OptionType.INTEGER, "team", "Wielkość ekipy (od 2 do 5 osób)", false)
                ));

        cmds.add(Commands.slash("rob", "Spróbuj po cichu okraść innego gracza na ulicy")
                .addOptions(new OptionData(OptionType.USER, "victim", "Gracz do okradzenia", true)));

        // ==========================================
        // 3. INNE MODUŁY (CompanyManager, Pets, Market)
        // ==========================================
        cmds.add(Commands.slash("company", "Interaktywny Urząd Pracy - Zarządzaj firmami"));
        cmds.add(Commands.slash("pets", "Panel interaktywny Twojego zwierzaka"));

        cmds.add(Commands.slash("market", "Otwórz Główny Terminal Giełdowy i zarządzaj kryptowalutami/akcjami"));

        guild.updateCommands().addCommands(cmds).queue();
    }

    public static void sendLevelUpNotification(Guild guild, TextChannel interactionChannel, String userId, int newLvl, GuildSettings settings) {
        TextChannel targetChannel = interactionChannel;
        if (settings.levelUpChannelId != null && !settings.levelUpChannelId.isEmpty()) {
            TextChannel configuredChannel = guild.getTextChannelById(settings.levelUpChannelId);
            if (configuredChannel != null) {
                targetChannel = configuredChannel;
            }
        }
        if (targetChannel != null) {
            // WEB PANEL READY: Dynamiczne zaciąganie tekstów z plików językowych na żywo
            String desc = LanguageManager.t(settings, "lvl_up_desc", userId, newLvl, (newLvl * 50), settings.currencyEmoji);

            EmbedBuilder eb = new EmbedBuilder()
                    .setColor(Color.decode("#F1C40F"))
                    .setTitle(LanguageManager.t(settings, "lvl_up_title"))
                    .setDescription(desc)
                    .setFooter("Econizer.gg");
            targetChannel.sendMessageEmbeds(eb.build()).queue();
        }
    }
}