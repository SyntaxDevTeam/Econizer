package pl.syntaxdevteam.listeners;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class CrimeManager extends ListenerAdapter {
    private final DatabaseManager db;
    private final Random random = new Random();

    // Pamięć RAM - Cooldowny na Napad oraz posiadany sprzęt
    private final Map<String, Long> heistCooldowns = new ConcurrentHashMap<>();
    private final Map<String, Boolean> hasC4 = new ConcurrentHashMap<>();

    private final int C4_COST = 2500;

    public CrimeManager(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        String userId = event.getUser().getId();
        GuildSettings settings = db.getGuildSettings(guildId);
        String cmd = event.getName();

        if (!settings.economyEnabled) return;

        // --- 🧨 NAPAD NA BANK SERWERA (Z HANDLARZEM) ---
        if (cmd.equals("napad")) {
            long currentTime = System.currentTimeMillis();
            String cdKey = guildId + "-" + userId;

            // Sprawdzanie 24-godzinnego cooldownu
            if (heistCooldowns.containsKey(cdKey) && currentTime < heistCooldowns.get(cdKey)) {
                long hoursLeft = ((heistCooldowns.get(cdKey) - currentTime) / 1000) / 3600;
                event.reply("⏳ <@" + userId + ">, policja monitoruje teren po ostatniej akcji! Następny wielki skok możesz zorganizować za **" + hoursLeft + "** godzin.").setEphemeral(true).queue();
                return;
            }

            boolean posiadaSprzet = hasC4.getOrDefault(cdKey, false);

            EmbedBuilder eb = new EmbedBuilder().setColor(Color.decode("#34495E"))
                    .setAuthor("Podziemie - Czarny Rynek", null, event.getUser().getEffectiveAvatarUrl())
                    .setDescription("Witaj w ukrytej kryjówce. Aby przeprowadzić udany skok na główny skarbiec serwera, potrzebujesz profesjonalnych ładunków wybuchowych (C4).\n\n" +
                            "📦 **Posiadany sprzęt:** " + (posiadaSprzet ? "✅ Ładunek C4" : "❌ Brak sprzętu"));

            if (!posiadaSprzet) {
                event.replyEmbeds(eb.build()).addActionRow(
                        Button.success("heist_buy_cash", "💵 Kup C4 od Handlarza (" + C4_COST + ")"),
                        Button.primary("heist_buy_card", "💳 Kup C4 dyskretnie Kartą (" + C4_COST + ")")
                ).setEphemeral(true).queue();
            } else {
                event.replyEmbeds(eb.build()).addActionRow(
                        Button.danger("heist_start", "🧨 Rozpocznij Skok na Skarbiec!")
                ).setEphemeral(true).queue();
            }
            return;
        }

        // --- 🥷 OKRADANIE (PvP) ---
        if (cmd.equals("okradnij")) {
            long currentRobTime = System.currentTimeMillis();
            long robCooldownTime = db.getCooldown(guildId, userId, "robbery");

            // 2 godziny cooldownu
            if (currentRobTime < robCooldownTime) {
                long minutesLeft = ((robCooldownTime - currentRobTime) / 1000) / 60;
                event.reply("⏳ <@" + userId + ">, za bardzo się rzucasz w oczy! Zniknij z radaru na kolejne **" + minutesLeft + "** minut.").setEphemeral(true).queue();
                return;
            }

            User ofiara = event.getOption("ofiara").getAsUser();
            if (ofiara.isBot() || ofiara.getId().equals(userId)) {
                event.reply("❌ Cel niedozwolony!").setEphemeral(true).queue(); return;
            }

            int ofiaraGotowka = db.getUserStats(guildId, ofiara.getId())[0];
            if (ofiaraGotowka < 100) {
                event.reply("❌ Ten użytkownik trzyma oszczędności w banku. Nie ma czego ukraść!").setEphemeral(true).queue(); return;
            }

            db.setCooldown(guildId, userId, "robbery", currentRobTime + 7200000); // 2 Godziny Cooldownu

            if (random.nextInt(100) < 40) {
                int ukradzione = (int)(ofiaraGotowka * (random.nextDouble() * 0.15 + 0.05));
                db.removeCoins(guildId, ofiara.getId(), ukradzione, "gotowka"); db.addCoins(guildId, userId, ukradzione, "gotowka");
                event.reply("🥷 **ZUCHWAŁY NAPAD!** <@" + userId + "> zręcznie wyciągnął z portfela <@" + ofiara.getId() + "> kwotę **" + ukradzione + " " + settings.currencyEmoji + "**!").queue();
            } else {
                int kara = 250;
                if (!db.removeCoins(guildId, userId, kara, "gotowka")) db.removeBankCoins(guildId, userId, kara);
                event.reply("🚓 **WPADKA!** <@" + userId + "> próbował okraść gracza <@" + ofiara.getId() + ">, lecz został złapany i zapłacił grzywnę **" + kara + " " + settings.currencyEmoji + "**!").queue();
            }
            return;
        }

        // --- 📜 LIST GOŃCZY ---
        if (cmd.equals("bounty")) {
            User poszukiwany = event.getOption("gracz").getAsUser(); int kwota = event.getOption("kwota").getAsInt();
            if (kwota <= 0) { event.reply("❌ Nagroda musi być wyższa od zera.").setEphemeral(true).queue(); return; }

            if (db.removeCoins(guildId, userId, kwota, "gotowka")) {
                db.addBounty(guildId, poszukiwany.getId(), kwota);
                EmbedBuilder eb = new EmbedBuilder().setColor(Color.decode("#C0392B"))
                        .setAuthor("Wystawiono List Gończy!", null, event.getUser().getEffectiveAvatarUrl())
                        .setThumbnail(poszukiwany.getEffectiveAvatarUrl())
                        .setDescription("<@" + userId + "> wyznaczył oficjalną nagrodę za schwytanie gracza <@" + poszukiwany.getId() + ">!\n\n🎯 **Łowcy głów, do dzieła! Do zgarnięcia:** `" + kwota + "` " + settings.currencyEmoji);
                event.replyEmbeds(eb.build()).queue();
            } else { event.reply("❌ Nie masz wystarczającej ilości gotówki!").setEphemeral(true).queue(); }
            return;
        }

        // --- 🎯 POLOWANIE ---
        if (cmd.equals("poluj")) {
            long currentHuntTime = System.currentTimeMillis();
            long huntCooldownTime = db.getCooldown(guildId, userId, "hunt");

            if (currentHuntTime < huntCooldownTime) {
                long minutesLeft = ((huntCooldownTime - currentHuntTime) / 1000) / 60;
                event.reply("⏳ <@" + userId + ">, musisz uzupełnić ekwipunek. Kolejne polowanie za **" + minutesLeft + "** minut.").setEphemeral(true).queue();
                return;
            }

            User poszukiwany = event.getOption("gracz").getAsUser(); int bounty = db.getBounty(guildId, poszukiwany.getId());
            if (bounty <= 0) { event.reply("❌ Za tego gracza nie wyznaczono żadnej nagrody.").setEphemeral(true).queue(); return; }

            db.setCooldown(guildId, userId, "hunt", currentHuntTime + 3600000); // 1 Godzina Cooldownu

            if (random.nextInt(100) < 30) {
                db.clearBounty(guildId, poszukiwany.getId()); db.addCoins(guildId, userId, bounty, "gotowka");
                EmbedBuilder eb = new EmbedBuilder().setColor(Color.decode("#27AE60")).setAuthor("Kontrakt zamknięty!", null, event.getUser().getEffectiveAvatarUrl()).setThumbnail(event.getUser().getEffectiveAvatarUrl())
                        .setDescription("🎯 <@" + userId + "> pomyślnie schwytał poszukiwanego listem gończym <@" + poszukiwany.getId() + "> i zgarnia nagrodę: **" + bounty + " " + settings.currencyEmoji + "**!");
                event.replyEmbeds(eb.build()).queue();
            } else { event.reply("💨 <@" + userId + "> próbował zapolować na <@" + poszukiwany.getId() + ">, lecz cel zdołał uciec!").queue(); }
        }
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        String[] parts = event.getComponentId().split(":");
        String action = parts[0];

        if (!action.startsWith("heist_")) return;

        String guildId = event.getGuild().getId();
        String userId = event.getUser().getId();
        GuildSettings settings = db.getGuildSettings(guildId);
        String cdKey = guildId + "-" + userId;

        if (action.equals("heist_buy_cash") || action.equals("heist_buy_card")) {
            boolean useCard = action.equals("heist_buy_card");
            boolean success = useCard ? db.removeBankCoins(guildId, userId, C4_COST) : db.removeCoins(guildId, userId, C4_COST, "gotowka");

            if (success) {
                hasC4.put(cdKey, true);
                event.editMessageEmbeds(new EmbedBuilder().setColor(Color.GREEN)
                                .setDescription("🕵️ **Handlarz mówi:** Dobry wybór. Masz sprzęt, zapłaciłeś " + (useCard ? "nie do namierzenia kartą" : "czystą gotówką") + ". Teraz bierz się do roboty, zanim policja wpadnie na Twój trop!").build())
                        .setComponents(ActionRow.of(Button.danger("heist_start", "🧨 Rozpocznij Skok na Skarbiec!"))).queue();
            } else {
                event.reply("❌ Handlarz wyśmiał Cię i wyrzucił za drzwi. Nie masz wystarczających środków (" + C4_COST + ") na wybranym koncie!").setEphemeral(true).queue();
            }
        }

        else if (action.equals("heist_start")) {
            if (!hasC4.getOrDefault(cdKey, false)) {
                event.reply("❌ Próbowałeś dostać się do sejfu, ale bez ładunków wybuchowych ani rusz! Wróć do Handlarza.").setEphemeral(true).queue();
                return;
            }

            // Zabierz C4 z ekwipunku i nałóż 24H cooldown
            hasC4.put(cdKey, false);
            heistCooldowns.put(cdKey, System.currentTimeMillis() + 86400000L);

            // 35% szans na potężny sukces
            if (random.nextInt(100) < 35) {
                int lup = random.nextInt(8000) + 5000; // Pula: 5000 - 12999
                db.addCoins(guildId, userId, lup, "gotowka");

                EmbedBuilder eb = new EmbedBuilder().setColor(Color.decode("#F1C40F"))
                        .setAuthor("Wielki Skok na Bank Zakończony Sukcesem!", null, event.getUser().getEffectiveAvatarUrl())
                        .setDescription("🧨 **BUM!** Drzwi skarbca wyleciały w powietrze! <@" + userId + "> zgarnia z półek wszystko, co się da, i ucieka helikopterem!\n\n" +
                                "💰 **Zdobyty Łup:** `+" + lup + "` " + settings.currencyEmoji);

                event.getChannel().sendMessageEmbeds(eb.build()).queue();
                event.editMessageEmbeds(new EmbedBuilder().setColor(Color.GRAY).setDescription("Akcja zakończona. Uciekasz z miejsca zdarzenia.").build()).setComponents().queue();
            } else {
                int kara = 2500;
                boolean paidFine = db.removeBankCoins(guildId, userId, kara) || db.removeCoins(guildId, userId, kara, "gotowka");

                EmbedBuilder eb = new EmbedBuilder().setColor(Color.RED)
                        .setAuthor("Napad Udaremniony przez SWAT!", null, event.getUser().getEffectiveAvatarUrl())
                        .setDescription("🚨 **ALARM!** Ładunek okazał się niewypałem, a w skarbcu czekały już oddziały specjalne! <@" + userId + "> wylądował za kratkami.\n\n" +
                                "💸 **Koszty sądowe i grzywna:** `" + (paidFine ? kara : "Konfiskata mienia") + "` " + settings.currencyEmoji);

                event.getChannel().sendMessageEmbeds(eb.build()).queue();
                event.editMessageEmbeds(new EmbedBuilder().setColor(Color.GRAY).setDescription("Zostałeś aresztowany. Twój sprzęt przepadł.").build()).setComponents().queue();
            }
        }
    }
}