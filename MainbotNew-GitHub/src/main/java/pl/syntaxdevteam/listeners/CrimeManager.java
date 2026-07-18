package pl.syntaxdevteam.listeners;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

public class CrimeManager extends ListenerAdapter {
    private final DatabaseManager db;
    private final Map<String, List<String>> activeHeists = new HashMap<>();
    private final Map<String, Long> crimeCooldowns = new HashMap<>();
    private final Random random = new Random();
    private final long HEIST_COOLDOWN = 7200000L; // 2 godziny

    public CrimeManager(DatabaseManager db) {
        this.db = db;
        setupV2Bounties();
    }

    private void setupV2Bounties() {
        try (Connection conn = db.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS bot_bounties_v2 (" +
                    "guild_id VARCHAR(64), user_id VARCHAR(64), sponsor_id VARCHAR(64), amount INT, attempts INT DEFAULT 1, " +
                    "PRIMARY KEY (guild_id, user_id))");
        } catch (Exception ignored) {}
    }

    private void setCrimeCooldown(String guildId, String userId, String action, long durationMs) {
        crimeCooldowns.put(guildId + "_" + userId + "_" + action, System.currentTimeMillis() + durationMs);
    }

    private long getCrimeCooldown(String guildId, String userId, String action) {
        return crimeCooldowns.getOrDefault(guildId + "_" + userId + "_" + action, 0L);
    }

    private String getLang(GuildSettings settings, String key, String fallback, Object... args) {
        String result = LanguageManager.t(settings, key, args);
        if (result.startsWith("TEXT_ERR") || result.equals("LANG_ERR")) {
            for (int i = 0; i < args.length; i++) {
                fallback = fallback.replace("{" + i + "}", String.valueOf(args[i]));
            }
            return fallback.replace("\\n", "\n");
        }
        return result;
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        String commandName = event.getName();
        if (!Arrays.asList("bounty", "wanted", "hunt", "heist", "rob").contains(commandName)) return;

        String guildId = event.getGuild().getId();
        GuildSettings settings = db.getGuildSettings(guildId);

        if (!settings.economyEnabled) {
            EmbedBuilder errorEmbed = new EmbedBuilder().setColor(Color.RED).setTitle(getLang(settings, "err_sys_locked", "🛑 SYSTEM ZABLOKOWANY"))
                    .setDescription(getLang(settings, "eco_disabled", "Ekonomia na tym serwerze jest wyłączona.")).setFooter("Econizer.gg");
            event.replyEmbeds(errorEmbed.build()).setEphemeral(true).queue();
            return;
        }

        db.logCommandUsage(commandName, guildId);

        switch (commandName) {
            case "bounty": handleBounty(event, guildId, settings); break;
            case "wanted": handleWanted(event, guildId, settings); break;
            case "hunt": handleHunt(event, guildId, settings); break;
            case "heist": handleHeist(event, guildId, settings); break;
            case "rob": handleRob(event, guildId, settings); break;
        }
    }

    private void handleBounty(SlashCommandInteractionEvent event, String guildId, GuildSettings settings) {
        User target = event.getOption("player").getAsUser();
        int amount = event.getOption("amount").getAsInt();
        String userId = event.getUser().getId();

        if (target.isBot() || target.getId().equals(userId)) {
            event.replyEmbeds(new EmbedBuilder().setColor(Color.RED).setTitle(getLang(settings, "crime_bounty_err_title", "❌ BŁĄD REJESTRACJI ZLECENIA"))
                    .setDescription(getLang(settings, "crime_bounty_invalid", "Nie możesz wystawić listu gończego na siebie ani na bota!")).setFooter("Econizer.gg").build()).setEphemeral(true).queue(); return;
        }

        if (amount < 1000) {
            event.replyEmbeds(new EmbedBuilder().setColor(Color.RED).setTitle(getLang(settings, "crime_bounty_min_title", "❌ NISKA STAWKA"))
                    .setDescription(getLang(settings, "crime_bounty_min", "Minimalna kwota zlecenia to **1000** {0}.", settings.currencyEmoji)).setFooter("Econizer.gg").build()).setEphemeral(true).queue(); return;
        }

        if (!db.removeCoins(guildId, userId, amount, "gotowka")) {
            event.replyEmbeds(new EmbedBuilder().setColor(Color.RED).setTitle(getLang(settings, "crime_err_funds_title", "❌ BRAK FUNDUSZY"))
                    .setDescription(getLang(settings, "crime_no_funds", "Nie masz wystarczająco gotówki w portfelu, aby opłacić kontrakt!")).setFooter("Econizer.gg").build()).setEphemeral(true).queue(); return;
        }

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO bot_bounties_v2 (guild_id, user_id, sponsor_id, amount, attempts) VALUES (?, ?, ?, ?, 1) ON DUPLICATE KEY UPDATE amount = amount + ?, attempts = 1, sponsor_id = ?")) {
            ps.setString(1, guildId); ps.setString(2, target.getId()); ps.setString(3, userId); ps.setInt(4, amount); ps.setInt(5, amount); ps.setString(6, userId);
            ps.executeUpdate();

            EmbedBuilder eb = new EmbedBuilder().setColor(Color.decode("#C0392B")).setTitle(getLang(settings, "crime_bounty_ok_title", "📜 CZARNA LISTA SYNDYKATU - NOWY KONTRAKT"))
                    .setDescription(getLang(settings, "crime_bounty_desc", "Sponsor <@{0}> wyznaczył nagrodę za głowę uciekiniera!\n\n🎯 **Cel:** <@{1}>\n💰 **Nagroda:** **{2}** {3}\n\n*Łowcy mogą teraz użyć komendy `/hunt`, aby namierzyć cel radarami operacyjnymi.*", userId, target.getId(), amount, settings.currencyEmoji))
                    .setThumbnail(target.getEffectiveAvatarUrl()).setFooter("Econizer.gg");
            event.replyEmbeds(eb.build()).setEphemeral(settings.hideEconomyReplies).queue();
        } catch (SQLException e) { event.replyEmbeds(new EmbedBuilder().setColor(Color.RED).setTitle("❌ BŁĄD").setDescription(getLang(settings, "err_db", "Błąd Bazy Danych!")).setFooter("Econizer.gg").build()).setEphemeral(true).queue(); }
    }

    private void handleWanted(SlashCommandInteractionEvent event, String guildId, GuildSettings settings) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT user_id, amount, attempts FROM bot_bounties_v2 WHERE guild_id = ? ORDER BY amount DESC LIMIT 10")) {
            ps.setString(1, guildId); ResultSet rs = ps.executeQuery();
            EmbedBuilder eb = new EmbedBuilder().setColor(Color.decode("#2C3E50")).setTitle(getLang(settings, "crime_wanted_title", "💻 TERMINAL CZARNEGO RYNKU - AKTYWNE ZLECENIA")).setFooter("Econizer.gg");
            StringBuilder sb = new StringBuilder(); int count = 1;
            while (rs.next()) {
                int att = rs.getInt("attempts");
                sb.append(getLang(settings, "crime_wanted_item", "**{0}.** 🎯 <@{1}> ➔ **{2}** {3} *(Namiary: Próba {4}/10 | Szansa wykrycia: {5}%)*\n", count, rs.getString("user_id"), rs.getInt("amount"), settings.currencyEmoji, att, (att * 10)));
                count++;
            }
            if (sb.length() == 0) sb.append(getLang(settings, "crime_wanted_empty", "📡 *Brak aktywnych celów w bazie danych. Miasto śpi spokojnie...*"));
            eb.setDescription(sb.toString()); event.replyEmbeds(eb.build()).setEphemeral(settings.hideEconomyReplies).queue();
        } catch (SQLException e) { event.replyEmbeds(new EmbedBuilder().setColor(Color.RED).setTitle("❌ BŁĄD").setDescription(getLang(settings, "err_db", "Błąd Bazy Danych!")).setFooter("Econizer.gg").build()).setEphemeral(true).queue(); }
    }

    private void handleHunt(SlashCommandInteractionEvent event, String guildId, GuildSettings settings) {
        User target = event.getOption("player").getAsUser(); String userId = event.getUser().getId();
        try (Connection conn = db.getConnection();
             PreparedStatement check = conn.prepareStatement("SELECT sponsor_id, amount, attempts FROM bot_bounties_v2 WHERE guild_id = ? AND user_id = ?")) {
            check.setString(1, guildId); check.setString(2, target.getId()); ResultSet rs = check.executeQuery();
            if (!rs.next()) {
                event.replyEmbeds(new EmbedBuilder().setColor(Color.RED).setTitle(getLang(settings, "crime_hunt_lost_title", "❌ SYGNAŁ UTRACONY")).setDescription(getLang(settings, "crime_hunt_lost_desc", "Ten cel nie jest poszukiwany lub jego kontrakt został już zrealizowany.")).setFooter("Econizer.gg").build()).setEphemeral(true).queue(); return;
            }

            String sponsorId = rs.getString("sponsor_id"); int amount = rs.getInt("amount"); int attempt = rs.getInt("attempts"); int fee = (int) (amount * 0.10);
            if (!db.removeCoins(guildId, userId, fee, "gotowka")) {
                event.replyEmbeds(new EmbedBuilder().setColor(Color.RED).setTitle(getLang(settings, "crime_hunt_radar_err_title", "❌ ODNOWIENIE RADARU ODRZUCONE")).setDescription(getLang(settings, "crime_hunt_radar_err_desc", "Potrzebujesz **{0}** {1} w gotówce (10% puli), aby wykupić wojskowy szyfr dostępu do radaru namierzającego syndykatu.", fee, settings.currencyEmoji)).setFooter("Econizer.gg").build()).setEphemeral(true).queue(); return;
            }

            db.addCoins(guildId, sponsorId, fee, "gotowka");
            boolean hit = random.nextInt(100) < (attempt * 10);
            EmbedBuilder resultEmbed = new EmbedBuilder().setFooter("Econizer.gg");

            if (hit) {
                int hunterReward = (int) (amount * (1.0 - ((attempt - 1) * 0.10)));
                int[] targetStats = db.getUserStats(guildId, target.getId());
                int takenFromCash = Math.min(targetStats[0], amount); db.removeCoins(guildId, target.getId(), takenFromCash, "BOUNTY_LOST");
                int takenFromBank = Math.min(targetStats[3], amount - takenFromCash); if (takenFromBank > 0) db.removeBankCoins(guildId, target.getId(), takenFromBank);
                int totalConfiscated = takenFromCash + takenFromBank;

                int deducted = db.addCoins(guildId, userId, hunterReward, "gotowka"); db.addCoins(guildId, sponsorId, totalConfiscated, "gotowka");
                try (PreparedStatement del = conn.prepareStatement("DELETE FROM bot_bounties_v2 WHERE guild_id = ? AND user_id = ?")) { del.setString(1, guildId); del.setString(2, target.getId()); del.executeUpdate(); }

                resultEmbed.setColor(Color.GREEN).setTitle(getLang(settings, "crime_hunt_ok_title", "🎯 KONTRAKT ZREALIZOWANY - LIKWIDACJA CELU")).setThumbnail(event.getUser().getEffectiveAvatarUrl())
                        .setDescription(getLang(settings, "crime_hunt_ok_desc", "📡 **Radar operacyjny idealnie namierzył kryjówkę!**\n<@{0}> wbił na dziuplę z drzwiami i zneutralizował <@{1}> przy **{2}** próbie sieciowej!\n\n💰 **Nagroda łowcy:** **{3}** {4} *(Potrącono koszty tylu prób)*\n⚖️ **Konfiskata majątku:** Z kont zbiega ściągnięto łącznie **{5}** {4} i przekazano Sponsorowi.", userId, target.getId(), attempt, hunterReward, settings.currencyEmoji, totalConfiscated));
                if (deducted > 0) resultEmbed.appendDescription("\n\n⚠️ **Zajęcie komornicze:** *" + getLang(settings, "debt_confiscation", "Komornik zabezpieczył **{0}** {1} na poczet zadłużenia!", deducted, settings.currencyEmoji) + "*");
            } else {
                if (attempt >= 10) {
                    int escapeReward = (int) (amount * 0.50); db.addCoins(guildId, target.getId(), escapeReward, "gotowka");
                    try (PreparedStatement del = conn.prepareStatement("DELETE FROM bot_bounties_v2 WHERE guild_id = ? AND user_id = ?")) { del.setString(1, guildId); del.setString(2, target.getId()); del.executeUpdate(); }
                    resultEmbed.setColor(Color.decode("#7F8C8D")).setTitle(getLang(settings, "crime_hunt_escaped_title", "💨 KONTRAKT ANULOWANY - UCIECZKA CELU")).setThumbnail(target.getEffectiveAvatarUrl())
                            .setDescription(getLang(settings, "crime_hunt_escaped_desc", "To była **10. próba** łowców – limit operacji wyczerpany.\nRadar całkowicie stracił sygnał, a <@{0}> zdołał zmylić pościg, upłynnić skradzione dane i zgarnąć **{1}** {2} z puli!", target.getId(), escapeReward, settings.currencyEmoji));
                } else {
                    try (PreparedStatement upd = conn.prepareStatement("UPDATE bot_bounties_v2 SET attempts = attempts + 1 WHERE guild_id = ? AND user_id = ?")) { upd.setString(1, guildId); upd.setString(2, target.getId()); upd.executeUpdate(); }
                    resultEmbed.setColor(Color.ORANGE).setTitle(getLang(settings, "crime_hunt_miss_title", "❌ ŚLEPY TROP - SKANOWANIE"))
                            .setDescription(getLang(settings, "crime_hunt_miss_desc", "Radar namierzył fałszywy sygnał i uderzyłeś w pusty magazyn!\n\n💸 **Koszty operacyjne:** Straciłeś wpisowe **{0}** {1}.\n📊 **Raport:** To była **{2}/10** próba. Następny łowca ruszy z szansą wynoszącą **{3}%**!", fee, settings.currencyEmoji, attempt, ((attempt + 1) * 10)));
                }
            }
            event.replyEmbeds(resultEmbed.build()).setEphemeral(settings.hideEconomyReplies).queue();
        } catch (SQLException e) { event.replyEmbeds(new EmbedBuilder().setColor(Color.RED).setTitle("❌ BŁĄD").setDescription(getLang(settings, "err_db", "Błąd Bazy Danych!")).setFooter("Econizer.gg").build()).setEphemeral(true).queue(); }
    }

    private void handleRob(SlashCommandInteractionEvent event, String guildId, GuildSettings settings) {
        User target = event.getOption("victim").getAsUser();
        String userId = event.getUser().getId();

        if (target.isBot() || target.getId().equals(userId)) {
            event.replyEmbeds(new EmbedBuilder().setColor(Color.RED).setTitle(getLang(settings, "crime_rob_logic_title", "❌ BŁĄD LOGICZNY")).setDescription(getLang(settings, "crime_rob_logic_desc", "Nie możesz okraść bota, systemu ani samego siebie!")).setFooter("Econizer.gg").build()).setEphemeral(true).queue(); return;
        }

        long curTime = System.currentTimeMillis();
        long cd = getCrimeCooldown(guildId, userId, "rob");
        if (curTime < cd) {
            long left = cd - curTime;
            long mins = (left / 1000) / 60;
            long secs = (left / 1000) % 60;
            event.replyEmbeds(new EmbedBuilder().setColor(Color.RED).setTitle(getLang(settings, "crime_rob_cd_title", "🚓 ZBYT GORĄCO!"))
                    .setDescription(getLang(settings, "crime_rob_cd_desc", "Psy wciąż węszą po Twojej ostatniej akcji. Przeczekaj w ukryciu jeszcze **{0}m {1}s** zanim wyjdziesz na ulice!", mins, secs))
                    .setFooter("Econizer.gg").build()).setEphemeral(true).queue();
            return;
        }

        int[] targetStats = db.getUserStats(guildId, target.getId());
        if (targetStats[0] < 500) {
            event.replyEmbeds(new EmbedBuilder().setColor(Color.RED).setTitle(getLang(settings, "crime_rob_empty_title", "❌ PUSTE KIESZENIE")).setDescription(getLang(settings, "crime_rob_empty_desc", "Obserwowałeś cel, ale okazał się spłukany. {0} nie ma przy sobie nawet 500 w gotówce. Szkoda ryzykować dla groszy.", target.getEffectiveName())).setFooter("Econizer.gg").build()).setEphemeral(true).queue(); return;
        }

        setCrimeCooldown(guildId, userId, "rob", 900000L);

        EmbedBuilder eb = new EmbedBuilder().setColor(Color.decode("#8E44AD")).setTitle(getLang(settings, "crime_rob_target_title", "🕵️ OBSERWACJA CELU: ") + target.getEffectiveName().toUpperCase())
                .setDescription(getLang(settings, "crime_rob_target_desc", "Zauważyłeś ofiarę na ciemnej ulicy. **Musisz podjąć decyzję, jak podejdziesz do sprawy.** Im brutalniej, tym wyższy zysk, ale większa grzywna w razie wpadki."))
                .setFooter("Econizer.gg • " + getLang(settings, "crime_rob_footer", "Masz 3 opcje podejścia"));

        event.replyEmbeds(eb.build()).addComponents(ActionRow.of(
                Button.secondary("rob_stealth:" + target.getId() + ":" + userId, getLang(settings, "crime_rob_btn_stealth", "🥷 Kradzież z kieszeni")),
                Button.primary("rob_mug:" + target.getId() + ":" + userId, getLang(settings, "crime_rob_btn_mug", "🔪 Zastraszenie")),
                Button.danger("rob_brutal:" + target.getId() + ":" + userId, getLang(settings, "crime_rob_btn_brutal", "🏏 Brutalny Napad"))
        )).setEphemeral(settings.hideEconomyReplies).queue();
    }

    private void handleHeist(SlashCommandInteractionEvent event, String guildId, GuildSettings settings) {
        String userId = event.getUser().getId();
        int investment = event.getOption("investment").getAsInt();
        int teamSize = event.getOption("team") != null ? event.getOption("team").getAsInt() : 3;

        if (investment < 1000) {
            event.replyEmbeds(new EmbedBuilder().setColor(Color.RED).setTitle(getLang(settings, "crime_heist_min_title", "❌ ZA MAŁE RYZYKO")).setDescription(getLang(settings, "crime_heist_min_desc", "Minimalny wkład własny w organizację napadu to **1000** {0}.", settings.currencyEmoji)).setFooter("Econizer.gg").build()).setEphemeral(true).queue(); return;
        }
        if (teamSize < 2 || teamSize > 5) {
            event.replyEmbeds(new EmbedBuilder().setColor(Color.RED).setTitle(getLang(settings, "crime_heist_size_title", "❌ BŁĄD LOGISTYKI")).setDescription(getLang(settings, "crime_heist_size_desc", "Ekipa do zorganizowanego napadu musi liczyć od 2 do 5 osób!")).setFooter("Econizer.gg").build()).setEphemeral(true).queue(); return;
        }

        long curTime = System.currentTimeMillis();

        long cd = getCrimeCooldown(guildId, userId, "heist");
        if (curTime < cd) {
            long left = cd - curTime;
            event.replyEmbeds(new EmbedBuilder().setColor(Color.RED).setTitle(getLang(settings, "crime_heist_cd_title", "🚨 PROFILOWANY PRZEZ POLICJĘ"))
                    .setDescription(getLang(settings, "crime_heist_cd_desc", "Jesteś zbyt rozpoznawalny po ostatnim skoku! Musisz przeczekać w bezpiecznej dziupli jeszcze **{0}h {1}m**.", (left / 3600000L), ((left % 3600000L) / 60000L))).setFooter("Econizer.gg").build()).setEphemeral(true).queue();
            return;
        }

        long lobbyCd = getCrimeCooldown(guildId, userId, "heist_lobby");
        if (curTime < lobbyCd) {
            event.replyEmbeds(new EmbedBuilder().setColor(Color.RED).setTitle(getLang(settings, "crime_heist_lobby_title", "❌ JEDEN NAPAD NA RAZ"))
                    .setDescription(getLang(settings, "crime_heist_lobby_desc", "Zorganizowałeś już jeden napad lub niedawno go anulowano! Odczekaj chwilę zanim spalisz kolejny plan.")).setFooter("Econizer.gg").build()).setEphemeral(true).queue();
            return;
        }

        for (List<String> team : activeHeists.values()) {
            if (team.contains(userId)) {
                event.replyEmbeds(new EmbedBuilder().setColor(Color.RED).setTitle(getLang(settings, "crime_heist_inteam_title", "❌ JESTEŚ JUŻ W EKPIE"))
                        .setDescription(getLang(settings, "crime_heist_inteam_desc", "Siedzisz już w innym vanie. Nie możesz organizować dwóch napadów na raz!")).setFooter("Econizer.gg").build()).setEphemeral(true).queue();
                return;
            }
        }

        if (!db.removeCoins(guildId, userId, investment, "gotowka")) {
            event.replyEmbeds(new EmbedBuilder().setColor(Color.RED).setTitle(getLang(settings, "crime_err_funds_title", "❌ BRAK FUNDUSZY"))
                    .setDescription(getLang(settings, "crime_heist_nofunds_desc", "Masz puste kieszenie. Jako organizator musisz wyłożyć na stół swoje wpisowe w wysokości **{0}** {1}!", investment, settings.currencyEmoji)).setFooter("Econizer.gg").build()).setEphemeral(true).queue();
            return;
        }

        setCrimeCooldown(guildId, userId, "heist_lobby", 120000L);

        String heistId = "heist_" + System.currentTimeMillis();
        activeHeists.put(heistId, new ArrayList<>(Collections.singletonList(userId)));

        int bustChance = teamSize == 2 ? 50 : (teamSize == 3 ? 35 : (teamSize == 4 ? 25 : 15));
        int stealthChance = teamSize == 2 ? 10 : (teamSize == 3 ? 15 : (teamSize == 4 ? 20 : 30));

        String desc = getLang(settings, "crime_heist_plan_desc", "Przywódca <@{0}> zbiera zaufanych ludzi na skok! Im większa ekipa, tym mniejsze ryzyko i większa profeska.\n\n💵 **Zrzutka z góry:** **{1}** {2} *(Każdy uczestnik musi wyłożyć tyle samo!)*\n📈 **Analiza wywiadu (Skład {3}-osobowy):**\n» Ryzyko zdemaskowania i wpadki: **{4}%**\n» Szansa na czystą i bezszelestną robotę: **{5}%**\n\n👥 **Status ekipy:** **{6}/{3}** zrekrutowanych.\n*Ekipa ma 2 minuty na zebranie się, inaczej wkład wraca na konta!*", userId, investment, settings.currencyEmoji, teamSize, bustChance, stealthChance, 1);

        EmbedBuilder eb = new EmbedBuilder().setColor(Color.decode("#F39C12")).setTitle(getLang(settings, "crime_heist_plan_title", "🧨 PLANOWANIE ZORGANIZOWANEGO NAPADU")).setThumbnail(event.getUser().getEffectiveAvatarUrl())
                .setDescription(desc).setFooter("Econizer.gg • " + getLang(settings, "crime_heist_plan_footer", "Nasłuch policyjny aktywny"));

        Button joinBtn = Button.primary(heistId + ":join:" + teamSize + ":" + investment, getLang(settings, "crime_heist_btn_join", "🔫 Wchodzę (Płacę: {0})", investment));

        // TO MUSI BYĆ PUBLICZNE, ABY INNI MOGLI KLIKNĄĆ!
        event.replyEmbeds(eb.build()).addComponents(ActionRow.of(joinBtn)).setEphemeral(false).queue(msg -> {
            new java.util.Timer().schedule(new java.util.TimerTask() {
                @Override
                public void run() {
                    List<String> t = activeHeists.remove(heistId);
                    if (t != null) {
                        for (String mem : t) db.addCoins(guildId, mem, investment, "gotowka");
                        try {
                            msg.editOriginalEmbeds(new EmbedBuilder().setColor(Color.RED).setTitle(getLang(settings, "crime_heist_cancel_title", "❌ NAPAD ODWOŁANY"))
                                    .setDescription(getLang(settings, "crime_heist_cancel_desc", "Załoga się nie zebrała w czasie. Zwrócono wszystkim ich pulę zrzutki (**{0}** {1}).", investment, settings.currencyEmoji))
                                    .setFooter("Econizer.gg").build()).setComponents().queue();
                        } catch (Exception ignored) {}
                    }
                }
            }, 120000);
        });
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        String[] parts = event.getComponentId().split(":");
        String actionPrefix = parts[0];
        String userId = event.getUser().getId();
        String guildId = event.getGuild().getId();
        GuildSettings settings = db.getGuildSettings(guildId);
        long curTime = System.currentTimeMillis();

        if (actionPrefix.startsWith("rob_")) {
            String victimId = parts[1];
            String ownerId = parts[2];

            if (!userId.equals(ownerId)) {
                event.reply(getLang(settings, "err_not_yours", "❌ Odsuń się, to nie Twoja robota!")).setEphemeral(true).queue(); return;
            }

            int[] targetStats = db.getUserStats(guildId, victimId);
            if (targetStats[0] < 500) {
                event.editMessageEmbeds(new EmbedBuilder().setColor(Color.RED).setTitle(getLang(settings, "crime_rob_changed_title", "❌ SYTUACJA ZMIENIONA")).setDescription(getLang(settings, "crime_rob_changed_desc", "Zbyt długo czekałeś! Cel zdążył już wydać pieniądze. Zwijamy się stąd!")).setFooter("Econizer.gg").build()).setComponents().queue(); return;
            }

            boolean success = false; double minPct = 0, maxPct = 0; int fine = 0; String typeTitle = "", successText = "", failText = "";

            switch (actionPrefix) {
                case "rob_stealth":
                    success = random.nextInt(100) < 50; minPct = 0.05; maxPct = 0.10; fine = 150;
                    typeTitle = getLang(settings, "crime_rob_type_stealth", "🥷 KRADZIEŻ KIESZONKOWA");
                    successText = getLang(settings, "crime_rob_stealth_ok", "Zadziałałeś jak duch! Wyciągnąłeś gotówkę z kurtki <@{0}>.", victimId);
                    failText = getLang(settings, "crime_rob_stealth_fail", "Miałeś pecha! <@{0}> nagle się odwrócił. Z nerwów upuściłeś po drodze **{1}** {2}.", victimId, fine, settings.currencyEmoji);
                    break;
                case "rob_mug":
                    success = random.nextInt(100) < 35; minPct = 0.10; maxPct = 0.25; fine = 350;
                    typeTitle = getLang(settings, "crime_rob_type_mug", "🔪 ZASTRASZENIE W ZAUŁKU");
                    successText = getLang(settings, "crime_rob_mug_ok", "Przyszpiliłeś <@{0}> do ściany i pokazałeś ostrze. Ofiara bez słowa oddała gotówkę.", victimId);
                    failText = getLang(settings, "crime_rob_mug_fail", "Ofiara stawiła opór! Dostałeś gazem po oczach. Uciekając, gubisz **{0}** {1} grzywny!", fine, settings.currencyEmoji);
                    break;
                case "rob_brutal":
                    success = random.nextInt(100) < 20; minPct = 0.25; maxPct = 0.45; fine = 750;
                    typeTitle = getLang(settings, "crime_rob_type_brutal", "🏏 BRUTALNY NAPAD");
                    successText = getLang(settings, "crime_rob_brutal_ok", "Wjechałeś z pełnym impetem! <@{0}> ląduje na asfalcie, a Ty czyścisz mu kieszenie!", victimId);
                    failText = getLang(settings, "crime_rob_brutal_fail", "Ktoś wezwał patrol! Rzucając się do ucieczki przez płoty, gubisz aż **{0}** {1} ze strachu!", fine, settings.currencyEmoji);
                    break;
            }

            EmbedBuilder resultEmbed = new EmbedBuilder().setFooter("Econizer.gg");
            if (success) {
                int stolen = (int) (targetStats[0] * (minPct + (random.nextDouble() * (maxPct - minPct))));
                db.removeCoins(guildId, victimId, stolen, "gotowka"); int deducted = db.addCoins(guildId, userId, stolen, "gotowka");
                resultEmbed.setColor(Color.GREEN).setTitle(typeTitle + getLang(settings, "crime_rob_success_suffix", " - SUKCES!")).setDescription(successText + "\n\n" + getLang(settings, "crime_rob_loot", "💰 **Zabrany łup:** **{0}** {1}", stolen, settings.currencyEmoji));
                if (deducted > 0) resultEmbed.appendDescription("\n⚖️ *" + getLang(settings, "debt_confiscation", "Komornik zabezpieczył **{0}** {1} na poczet zadłużenia!", deducted, settings.currencyEmoji) + "*");
            } else {
                db.removeCoins(guildId, userId, fine, "gotowka");
                resultEmbed.setColor(Color.RED).setTitle(typeTitle + getLang(settings, "crime_rob_fail_suffix", " - WPADKA!")).setDescription(failText);
            }
            event.editMessageEmbeds(resultEmbed.build()).setComponents().queue(); return;
        }

        if (actionPrefix.startsWith("heist_")) {
            String heistId = parts[0];
            String heistAction = parts[1];
            int requiredSize = Integer.parseInt(parts[2]);
            int investment = Integer.parseInt(parts[3]);

            if (heistAction.equals("join")) {
                List<String> team = activeHeists.get(heistId);
                if (team == null) {
                    event.replyEmbeds(new EmbedBuilder().setColor(Color.RED).setTitle(getLang(settings, "crime_heist_timeout_title", "❌ KONIEC CZASU")).setDescription(getLang(settings, "crime_heist_timeout_desc", "Ta rekrutacja wygasła lub furgonetka już odjechała!")).setFooter("Econizer.gg").build()).setEphemeral(true).queue(); return;
                }

                if (curTime < getCrimeCooldown(guildId, userId, "heist")) {
                    event.replyEmbeds(new EmbedBuilder().setColor(Color.RED).setTitle(getLang(settings, "crime_heist_busted_title", "🚨 SPALONY"))
                            .setDescription(getLang(settings, "crime_heist_busted_desc", "Psy wciąż na Ciebie polują po ostatnim skoku. Zostań w dziupli i nie psuj akcji innym!")).setFooter("Econizer.gg").build()).setEphemeral(true).queue();
                    return;
                }

                for (List<String> t : activeHeists.values()) {
                    if (t.contains(userId)) {
                        event.replyEmbeds(new EmbedBuilder().setColor(Color.RED).setTitle(getLang(settings, "crime_heist_inteam_title", "❌ JUŻ JESTEŚ W EKPIE")).setDescription(getLang(settings, "crime_heist_already_desc", "Zrekrutowałeś się już do napadu!")).setFooter("Econizer.gg").build()).setEphemeral(true).queue(); return;
                    }
                }

                if (!db.removeCoins(guildId, userId, investment, "gotowka")) {
                    event.replyEmbeds(new EmbedBuilder().setColor(Color.RED).setTitle(getLang(settings, "crime_err_funds_title", "❌ BRAK FUNDUSZY"))
                            .setDescription(getLang(settings, "crime_heist_join_nofunds", "Masz puste kieszenie. Wymagany kapitał wejścia to **{0}** {1}!", investment, settings.currencyEmoji)).setFooter("Econizer.gg").build()).setEphemeral(true).queue();
                    return;
                }

                team.add(userId);

                if (team.size() >= requiredSize) {
                    activeHeists.remove(heistId);
                    event.getMessage().delete().queue();

                    int bustChance = requiredSize == 2 ? 50 : (requiredSize == 3 ? 35 : (requiredSize == 4 ? 25 : 15));
                    int stealthChance = requiredSize == 2 ? 10 : (requiredSize == 3 ? 15 : (requiredSize == 4 ? 20 : 30));

                    int roll = random.nextInt(100);
                    EmbedBuilder resultEb = new EmbedBuilder().setFooter("Econizer.gg");
                    StringBuilder teamMentions = new StringBuilder();

                    for (String memberId : team) {
                        setCrimeCooldown(guildId, memberId, "heist", HEIST_COOLDOWN);
                    }

                    if (roll < stealthChance) {
                        for (String memberId : team) {
                            int payout = (int) (investment * (2.5 + random.nextDouble() * 1.5));
                            int deducted = db.addCoins(guildId, memberId, payout, "HEIST_PERFECT");
                            String line = getLang(settings, "crime_heist_line_win", "• <@{0}> ➔ Zgarnął **{1}** {2}", memberId, payout, settings.currencyEmoji);
                            if (deducted > 0) line += getLang(settings, "crime_heist_line_deduct", " *(Komornik: -{0})*", deducted);
                            teamMentions.append(line).append("\n");
                        }
                        resultEb.setColor(Color.decode("#9B59B6")).setTitle(getLang(settings, "crime_heist_perfect_title", "🥷 SKOK STULECIA - PERFEKCYJNA ROBOTA"))
                                .setDescription(getLang(settings, "crime_heist_perfect_desc", "Zrobiliście to bezszelestnie! Wyłączyliście kamery, a hajs wynieśliście w sportowych torbach tylnym wyjściem. Mieliście {0}% szans na taki przebieg i idealnie Wam to wyszło!\n\n**Czysty podział zysków:**\n", stealthChance) + teamMentions.toString());

                    } else if (roll < (100 - bustChance)) {
                        for (String memberId : team) {
                            int payout = (int) (investment * (1.2 + random.nextDouble() * 1.0));
                            int deducted = db.addCoins(guildId, memberId, payout, "HEIST_LOUD");
                            String line = getLang(settings, "crime_heist_line_win", "• <@{0}> ➔ Zgarnął **{1}** {2}", memberId, payout, settings.currencyEmoji);
                            if (deducted > 0) line += getLang(settings, "crime_heist_line_deduct", " *(Komornik: -{0})*", deducted);
                            teamMentions.append(line).append("\n");
                        }
                        resultEb.setColor(Color.ORANGE).setTitle(getLang(settings, "crime_heist_loud_title", "💥 KRWAWA STRZELANINA - MAMY ŁUP!"))
                                .setDescription(getLang(settings, "crime_heist_loud_desc", "Plan poszedł w łeb, wywiązała się ostra strzelanina w holu głównym. Wasz kierowca staranował policyjne blokady opancerzonym vanem. Przeżyliście i macie hajs, ale fura jest sitem od kul!\n\n**Podział uratowanej floty:**\n") + teamMentions.toString());

                    } else {
                        for (String memberId : team) {
                            int extraFine = (int) (investment * (0.5 + random.nextDouble()));
                            int[] stats = db.getUserStats(guildId, memberId);
                            int cashToTake = Math.min(stats[0], extraFine);
                            if (cashToTake > 0) db.removeCoins(guildId, memberId, cashToTake, "HEIST_BUSTED");
                            teamMentions.append(getLang(settings, "crime_heist_line_fail", "• <@{0}> ➔ Utrata wkładu + Kaucja **{1}** {2}", memberId, cashToTake, settings.currencyEmoji)).append("\n");
                        }
                        resultEb.setColor(Color.RED).setTitle(getLang(settings, "crime_heist_fail_title", "🚨 NALOT SWAT - ZGARNIĘCI!"))
                                .setDescription(getLang(settings, "crime_heist_fail_desc", "Zostaliście sprzedani przez informatora! (Ryzyko wpadki wynosiło {0}%). Przez świetliki wpadły granaty hukowe. *\"GLEBA! RĘCE NA WIDOKU!\"*\n\nCała ekipa wylądowała na dołku. Straciliście całe pieniądze włożone w organizację i opłaciliście dodatkowe kaucje z własnych portfeli.\n\n**Rejestr strat (Grzywny):**\n", bustChance) + teamMentions.toString());
                    }

                    resultEb.appendDescription("\n" + getLang(settings, "crime_heist_cooldown_msg", "⏳ *Cała Wasza ekipa dostała teraz 2-godzinny zakaz zbliżania się do placówek bankowych!*"));

                    // Skoro to wynik publicznego poszukiwania ekipy, wysyłamy wynik też na kanale
                    event.replyEmbeds(resultEb.build()).queue();

                } else {
                    String leaderId = team.get(0);
                    int bustChance = requiredSize == 2 ? 50 : (requiredSize == 3 ? 35 : (requiredSize == 4 ? 25 : 15));
                    int stealthChance = requiredSize == 2 ? 10 : (requiredSize == 3 ? 15 : (requiredSize == 4 ? 20 : 30));

                    String updatedDesc = getLang(settings, "crime_heist_plan_desc", "Przywódca <@{0}> zbiera zaufanych ludzi na skok! Im większa ekipa, tym mniejsze ryzyko i większa profeska.\n\n💵 **Zrzutka z góry:** **{1}** {2} *(Każdy uczestnik musi wyłożyć tyle samo!)*\n📈 **Analiza wywiadu (Skład {3}-osobowy):**\n» Ryzyko zdemaskowania i wpadki: **{4}%**\n» Szansa na czystą i bezszelestną robotę: **{5}%**\n\n👥 **Status ekipy:** **{6}/{3}** zrekrutowanych.\n*Ekipa ma 2 minuty na zebranie się, inaczej wkład wraca na konta!*", leaderId, investment, settings.currencyEmoji, requiredSize, bustChance, stealthChance, team.size());

                    EmbedBuilder eb = new EmbedBuilder(event.getMessage().getEmbeds().get(0)).setDescription(updatedDesc);
                    event.editMessageEmbeds(eb.build()).queue();
                }
            }
            return;
        }
    }
}