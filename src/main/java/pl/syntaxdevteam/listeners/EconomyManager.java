package pl.syntaxdevteam.listeners;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Random;

public class EconomyManager extends ListenerAdapter {
    private final DatabaseManager db;
    private final Random random = new Random();

    public EconomyManager(DatabaseManager db) { this.db = db; }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        String userId = event.getUser().getId();
        GuildSettings settings = db.getGuildSettings(guildId);
        String cmd = event.getName();

        if (!settings.economyEnabled) return;
        db.logCommandUsage(cmd, guildId);

        if (cmd.equals("sklep")) {
            String shopUrl = settings.shopBaseUrl + guildId;
            event.replyEmbeds(new EmbedBuilder().setColor(Color.decode("#9B59B6")).setAuthor("Econizer - Sklep Serwerowy", null, event.getJDA().getSelfUser().getEffectiveAvatarUrl()).setDescription("Wymień zgromadzone środki na nagrody na naszej stronie internetowej!\n\n🛒 **[Otwórz sklep serwera](" + shopUrl + ")**").build()).queue();
            return;
        }

        if (cmd.equals("dashboard")) {
            String dashUrl = "https://econizer.syntaxdevteam.pl/dashboard";
            event.replyEmbeds(new EmbedBuilder().setColor(Color.decode("#3498DB")).setAuthor("Panel Gracza Econizer", null, event.getUser().getEffectiveAvatarUrl()).setDescription("Zarządzaj swoimi finansami, firmą i ustawieniami w jednym miejscu!\n\n🌍 **[Otwórz swój Dashboard](" + dashUrl + ")**").build()).setEphemeral(true).queue();
            return;
        }

        if (cmd.equals("konfiguracja")) {
            if (event.getMember() != null && !event.getMember().hasPermission(Permission.ADMINISTRATOR)) { event.reply("❌ Brak uprawnień do panelu zarządzania!").setEphemeral(true).queue(); return; }
            String adminUrl = "https://econizer.syntaxdevteam.pl/admin/guild/" + guildId;
            event.replyEmbeds(new EmbedBuilder().setColor(Color.decode("#E74C3C")).setAuthor("Panel Zarządzania Serwerem", null, event.getJDA().getSelfUser().getEffectiveAvatarUrl()).setDescription("Skonfiguruj podatki, nagrody, limity firm oraz system sezonowy.\n\n⚙️ **[Otwórz konfigurację serwera](" + adminUrl + ")**").build()).setEphemeral(true).queue();
            return;
        }

        if (cmd.equals("reseteco")) {
            if (event.getMember() != null && !event.getMember().hasPermission(Permission.ADMINISTRATOR)) { event.reply("❌ Brak uprawnień!").setEphemeral(true).queue(); return; }
            if (!event.getOption("potwierdzenie").getAsBoolean()) { event.reply("❌ Przerwano. Aby potwierdzić usunięcie wszystkich danych ekonomii, wybierz `True`.").setEphemeral(true).queue(); return; }
            db.resetServerEconomy(guildId);
            WebAPIManager.reportGuildAction(guildId, event.getGuild().getName(), "economy_wipe");
            event.replyEmbeds(new EmbedBuilder().setColor(Color.RED).setTitle("⚠️ ZAKOŃCZENIE SEZONU: WIPE EKONOMII ⚠️").setThumbnail(event.getGuild().getIconUrl()).setDescription("Administrator <@" + userId + "> użył resetu!\n\nWszystkie konta zostały wyzerowane. Firmy zamknięte, gotówka spłonęła. Rozpoczynamy nowy sezon!").build()).queue();
            return;
        }

        if (cmd.equals("bank")) {
            String op = event.getOption("operacja").getAsString(); int kwota = event.getOption("kwota").getAsInt();
            if (kwota <= 0) { event.reply("❌ Podano nieprawidłową kwotę!").setEphemeral(true).queue(); return; }
            if (op.equals("wplac")) {
                if (db.removeCoins(guildId, userId, kwota, "gotowka")) {
                    int netto = (int)(kwota * 0.95); db.addBankCoins(guildId, userId, netto);
                    event.reply("🏦 <@" + userId + ">, wpłaciłeś **" + netto + " " + settings.currencyEmoji + "** do banku (Prowizja: 5%). Twoje oszczędności są tam bezpieczne!").queue();
                } else { event.reply("❌ Nie masz tyle gotówki przy sobie!").setEphemeral(true).queue(); }
            } else {
                if (db.removeBankCoins(guildId, userId, kwota)) {
                    db.addCoins(guildId, userId, kwota, "gotowka");
                    event.reply("🏦 <@" + userId + ">, wypłaciłeś **" + kwota + " " + settings.currencyEmoji + "** z konta bankowego!").queue();
                } else { event.reply("❌ Nie posiadasz tylu środków w banku!").setEphemeral(true).queue(); }
            }
            return;
        }

        if (cmd.equals("profil")) {
            OptionMapping uOpt = event.getOption("uzytkownik"); User target = uOpt != null ? uOpt.getAsUser() : event.getUser();
            if (target.isBot()) { event.reply("❌ Boty nie mają kont!").setEphemeral(true).queue(); return; }
            int[] stats = db.getUserStats(guildId, target.getId()); int petId = db.getUserPetId(guildId, target.getId());
            String petName = "Brak"; if (petId > 0) { Object[] c = db.getPetConfig(guildId, petId, settings.isPremium); if (c != null) petName = (String) c[0]; }
            EmbedBuilder eb = new EmbedBuilder().setColor(Color.decode("#F1C40F")).setAuthor("Konto: " + target.getName(), null, target.getEffectiveAvatarUrl()).setThumbnail(target.getEffectiveAvatarUrl())
                    .addField("💵 Gotówka", "`" + stats[0] + "` " + settings.currencyEmoji, true).addField("🏦 Bank", "`" + stats[3] + "` " + settings.currencyEmoji, true)
                    .addField("🏆 Poziom", "Lvl `" + stats[1] + "`", true).addField("✨ Exp", "`" + stats[2] + " / " + (stats[1] * 100) + "` XP", false).addField("🐾 Towarzysz", "**" + petName + "**", false);
            event.replyEmbeds(eb.build()).queue(); return;
        }

        if (cmd.equals("work")) {
            long cur = System.currentTimeMillis(); long cd = db.getCooldown(guildId, userId, "work");
            if (cur < cd) { event.reply("⏳ <@" + userId + ">, jesteś zmęczony! Kolejna praca za **" + ((cd - cur) / 1000) + "** sekund.").setEphemeral(true).queue(); return; }
            double[] mults = db.getActiveMultipliers(guildId, userId);
            int earned = (int) ((random.nextInt((settings.maxWork - settings.minWork) + 1) + settings.minWork) * mults[0]);
            int exp = (int) ((random.nextInt(21) + 20) * mults[1]);
            db.addCoins(guildId, userId, earned, "WORK"); db.setCooldown(guildId, userId, "work", cur + 600000);
            int newLvl = db.addExpAndCheckLevel(guildId, userId, exp);

            try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/econizer", "root", "twoje_haslo")) { CompanyManager.handleEmployeeWork(conn, guildId, userId, earned); } catch (Exception ignored) {}

            EmbedBuilder eb = new EmbedBuilder().setColor(Color.decode("#2ECC71")).setAuthor(event.getUser().getName() + " wraca z pracy!", null, event.getUser().getEffectiveAvatarUrl()).setThumbnail(event.getUser().getEffectiveAvatarUrl())
                    .setDescription("<@" + userId + "> ukończył zadania!\n\n💰 **Zarobek:** `+" + earned + "` " + settings.currencyEmoji + "\n✨ **Doświadczenie:** `+" + exp + "` XP");
            if (mults[0] > 1.0) eb.appendDescription("\n🐾 *(Aktywny bonus od najedzonego towarzysza!)*");
            if (newLvl > 0) eb.appendDescription("\n\n🎉 **AWANS!** Awansujesz na poziom **" + newLvl + "**!");
            event.replyEmbeds(eb.build()).queue(); return;
        }

        if (cmd.equals("daily")) {
            long cur = System.currentTimeMillis(); long cd = db.getCooldown(guildId, userId, "daily");
            if (cur < cd) { event.reply("⏳ <@" + userId + ">, odebrałeś dzisiejszą paczkę! Spróbuj za **" + (((cd - cur) / 1000) / 3600) + "** godzin.").setEphemeral(true).queue(); return; }
            double[] mults = db.getActiveMultipliers(guildId, userId);
            int earned = (int) (settings.dailyAmount * mults[0]); int exp = (int) ((random.nextInt(101) + 150) * mults[1]);
            db.addCoins(guildId, userId, earned, "DAILY"); db.setCooldown(guildId, userId, "daily", cur + 86400000);
            int newLvl = db.addExpAndCheckLevel(guildId, userId, exp);

            EmbedBuilder eb = new EmbedBuilder().setColor(Color.decode("#3498DB")).setAuthor("Darmowa Nagroda Codzienna!", null, event.getUser().getEffectiveAvatarUrl()).setThumbnail(event.getUser().getEffectiveAvatarUrl())
                    .setDescription("<@" + userId + "> odebrał darmowy daily-box!\n\n🎁 **Zawartość:** `+" + earned + "` " + settings.currencyEmoji + "\n✨ **Bonus:** `+" + exp + "` XP");
            if (mults[0] > 1.0) eb.appendDescription("\n🐾 *(Twój pupil zwiększył zasoby paczki!)*");
            if (newLvl > 0) eb.appendDescription("\n\n🎉 **AWANS!** Awansujesz na poziom **" + newLvl + "**!");
            event.replyEmbeds(eb.build()).queue(); return;
        }

        if (cmd.equals("zaplac")) {
            User target = event.getOption("gracz").getAsUser(); int amt = event.getOption("kwota").getAsInt();
            if (target.isBot() || target.getId().equals(userId) || amt <= 0) { event.reply("❌ Błędne parametry transakcji!").setEphemeral(true).queue(); return; }
            int tax = (int) Math.round(amt * settings.transferTax); int net = amt - tax;
            if (db.removeCoins(guildId, userId, amt, "TRANSFER_SEND")) {
                db.addCoins(guildId, target.getId(), net, "TRANSFER_RECEIVE");
                EmbedBuilder eb = new EmbedBuilder().setColor(Color.decode("#1ABC9C")).setAuthor("Pomyślny Przelew Środków!", null, event.getUser().getEffectiveAvatarUrl()).setThumbnail(target.getEffectiveAvatarUrl())
                        .setDescription("<@" + userId + "> przelał gotówkę użytkownikowi <@" + target.getId() + ">!\n\n💸 **Kwota netto:** `+" + net + "` " + settings.currencyEmoji + "\n🧾 **Podatek bota:** `" + tax + "` " + settings.currencyEmoji);
                event.replyEmbeds(eb.build()).queue();
            } else { event.reply("❌ Nie masz tyle gotówki w portfelu!").setEphemeral(true).queue(); }
            return;
        }

        if (cmd.equals("coinflip")) {
            int amt = event.getOption("kwota").getAsInt(); if (amt <= 0) { event.reply("❌ Błędna stawka!").setEphemeral(true).queue(); return; }
            EmbedBuilder eb = new EmbedBuilder().setColor(Color.decode("#E67E22")).setAuthor("System Kasyna", null, event.getUser().getEffectiveAvatarUrl()).setDescription("<@" + userId + ">, deklarujesz stawkę **" + amt + " " + settings.currencyEmoji + "**. Wybierz rachunek płatniczy:");
            event.replyEmbeds(eb.build()).addComponents(ActionRow.of(Button.success("cf_pay_cash:" + amt, "💵 Portfel (Gotówka)"), Button.primary("cf_pay_card:" + amt, "💳 Karta (Konto Bankowe)"))).setEphemeral(true).queue();
            return;
        }

        if (cmd.equals("dodajkase")) {
            if (event.getMember() != null && !event.getMember().hasPermission(Permission.ADMINISTRATOR)) { event.reply("❌ Brak uprawnień!").setEphemeral(true).queue(); return; }
            User target = event.getOption("gracz").getAsUser(); int amt = event.getOption("kwota").getAsInt();
            if (amt <= 0) { event.reply("❌ Błędna kwota.").setEphemeral(true).queue(); return; } db.addCoins(guildId, target.getId(), amt, "ADMIN_ADD");
            EmbedBuilder eb = new EmbedBuilder().setColor(Color.decode("#D35400")).setAuthor("Korekta Administracyjna", null, event.getUser().getEffectiveAvatarUrl()).setThumbnail(target.getEffectiveAvatarUrl())
                    .setDescription("Administrator <@" + userId + "> skorygował stan portfela gracza <@" + target.getId() + ">!\n\n🔧 **Dodano:** `+" + amt + "` " + settings.currencyEmoji + ".");
            event.replyEmbeds(eb.build()).queue();
        }
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        String[] parts = event.getComponentId().split(":"); String action = parts[0]; if (!action.startsWith("cf_pay_")) return;
        String guildId = event.getGuild().getId(); String userId = event.getUser().getId(); GuildSettings settings = db.getGuildSettings(guildId);
        int amt = Integer.parseInt(parts[1]); int currentLvl = db.getUserStats(guildId, userId)[1];

        boolean success = action.equals("cf_pay_card") ? db.removeBankCoins(guildId, userId, amt) : db.removeCoins(guildId, userId, amt, "CASINO_BET");
        if (!success) { event.reply("❌ Odmowa transakcji: brak środków na koncie.").setEphemeral(true).queue(); return; }

        boolean win = random.nextBoolean(); EmbedBuilder eb = new EmbedBuilder().setThumbnail(event.getUser().getEffectiveAvatarUrl());
        if (win) {
            int winAmt = amt * 2; if (action.equals("cf_pay_card")) db.addBankCoins(guildId, userId, winAmt); else db.addCoins(guildId, userId, winAmt, "CASINO_WIN");
            eb.setColor(Color.GREEN).setAuthor("Wygrana w Kasynie!", null, event.getUser().getEffectiveAvatarUrl()).setDescription("🪙 **ORZEŁ!** Szczęśliwy rzut!\n\n<@" + userId + "> postawił " + (action.equals("cf_pay_card") ? "kartą" : "gotówką") + " i wygrywa **" + winAmt + "** " + settings.currencyEmoji + "!");
        } else {
            eb.setColor(Color.RED).setAuthor("Przegrana w Kasynie...", null, event.getUser().getEffectiveAvatarUrl()).setDescription("📉 **RESZKA!** Dom zawsze wygrywa.\n\n<@" + userId + "> traci postawione **" + amt + " " + settings.currencyEmoji + "**.");
        }
        event.editMessageEmbeds(eb.build()).setComponents().queue();
    }
}