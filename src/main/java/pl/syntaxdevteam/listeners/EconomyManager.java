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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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

        if (!settings.economyEnabled) {
            event.reply(LanguageManager.t(settings, "eco_disabled")).setEphemeral(true).queue();
            return;
        }
        db.logCommandUsage(cmd, guildId);

        if (cmd.equals("shop")) {
            String shopUrl = settings.shopBaseUrl + guildId;
            event.replyEmbeds(new EmbedBuilder().setColor(Color.decode("#9B59B6"))
                    .setAuthor(LanguageManager.t(settings, "shop_author"), null, event.getJDA().getSelfUser().getEffectiveAvatarUrl())
                    .setDescription(LanguageManager.t(settings, "shop_desc", shopUrl)).build()).queue();
            return;
        }

        if (cmd.equals("dashboard")) {
            String dashUrl = "https://econizer.syntaxdevteam.pl/dashboard";
            event.replyEmbeds(new EmbedBuilder().setColor(Color.decode("#3498DB"))
                    .setAuthor(LanguageManager.t(settings, "dashboard_author"), null, event.getUser().getEffectiveAvatarUrl())
                    .setDescription(LanguageManager.t(settings, "dashboard_desc", dashUrl)).build()).setEphemeral(true).queue();
            return;
        }

        if (cmd.equals("config")) {
            if (event.getMember() != null && !event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
                event.reply(LanguageManager.t(settings, "config_no_perm")).setEphemeral(true).queue();
                return;
            }
            String adminUrl = "https://econizer.syntaxdevteam.pl/admin/guild/" + guildId;
            event.replyEmbeds(new EmbedBuilder().setColor(Color.decode("#E74C3C"))
                    .setAuthor(LanguageManager.t(settings, "config_author"), null, event.getJDA().getSelfUser().getEffectiveAvatarUrl())
                    .setDescription(LanguageManager.t(settings, "config_desc", adminUrl)).build()).setEphemeral(true).queue();
            return;
        }

        if (cmd.equals("reseteco")) {
            if (event.getMember() != null && !event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
                event.reply(LanguageManager.t(settings, "admin_err_perm")).setEphemeral(true).queue();
                return;
            }
            if (!event.getOption("confirm").getAsBoolean()) {
                event.reply(LanguageManager.t(settings, "reset_confirm_fail")).setEphemeral(true).queue();
                return;
            }
            db.resetServerEconomy(guildId);
            WebAPIManager.reportGuildAction(guildId, event.getGuild().getName(), "economy_wipe");
            event.replyEmbeds(new EmbedBuilder().setColor(Color.RED)
                    .setTitle(LanguageManager.t(settings, "reset_title"))
                    .setThumbnail(event.getGuild().getIconUrl())
                    .setDescription(LanguageManager.t(settings, "reset_desc", userId)).build()).queue();
            return;
        }

        if (cmd.equals("stats")) {
            long[] stats = db.getBotStatistics();
            int serverCount = event.getJDA().getGuilds().size();
            EmbedBuilder eb = new EmbedBuilder().setColor(Color.decode("#9B59B6"))
                    .setTitle(LanguageManager.t(settings, "stats_title"))
                    .setDescription(LanguageManager.t(settings, "stats_desc"))
                    .addField(LanguageManager.t(settings, "stats_servers"), "`" + serverCount + "`", true)
                    .addField(LanguageManager.t(settings, "stats_cmds_24h"), "`" + stats[1] + "`", true)
                    .addField(LanguageManager.t(settings, "stats_cmds_total"), "`" + stats[0] + "`", true);
            event.replyEmbeds(eb.build()).queue();
            return;
        }

        if (cmd.equals("bank")) {
            String op = event.getOption("operation").getAsString();
            int kwota = event.getOption("amount").getAsInt();
            if (kwota <= 0) {
                event.reply(LanguageManager.t(settings, "err_amount")).setEphemeral(true).queue();
                return;
            }
            if (op.equals("deposit") || op.equals("wplac")) {
                if (db.removeCoins(guildId, userId, kwota, "gotowka")) {
                    int netto = (int) (kwota * 0.95);
                    db.addBankCoins(guildId, userId, netto);
                    event.reply(LanguageManager.t(settings, "bank_deposit_ok", userId, netto, settings.currencyEmoji)).queue();
                } else {
                    event.reply(LanguageManager.t(settings, "bank_no_cash")).setEphemeral(true).queue();
                }
            } else {
                if (db.removeBankCoins(guildId, userId, kwota)) {
                    db.addCoins(guildId, userId, kwota, "gotowka");
                    event.reply(LanguageManager.t(settings, "bank_withdraw_ok", userId, kwota, settings.currencyEmoji)).queue();
                } else {
                    event.reply(LanguageManager.t(settings, "bank_no_bank_funds")).setEphemeral(true).queue();
                }
            }
            return;
        }

        if (cmd.equals("profile")) {
            OptionMapping uOpt = event.getOption("user");
            User target = uOpt != null ? uOpt.getAsUser() : event.getUser();
            if (target.isBot()) {
                event.reply(LanguageManager.t(settings, "bot_profile")).setEphemeral(true).queue();
                return;
            }
            int[] userStats = db.getUserStats(guildId, target.getId());

            int petId = db.getUserPetId(guildId, target.getId());
            String petName = LanguageManager.t(settings, "profile_pet_none");
            if (petId > 0) {
                Object[] c = db.getPetConfig(guildId, petId, settings.isPremium);
                if (c != null) petName = (String) c[0];
            }

            String companyDisplay = "Brak";
            try (PreparedStatement pstmt = db.getConnection().prepareStatement(
                    "SELECT c.name, c.owner_id FROM bot_employees e JOIN bot_companies c ON e.company_id = c.id WHERE e.guild_id = ? AND e.user_id = ?")) {
                pstmt.setString(1, guildId);
                pstmt.setString(2, target.getId());
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    String compName = rs.getString("name");
                    String ownerId = rs.getString("owner_id");
                    if (target.getId().equals(ownerId)) {
                        companyDisplay = "**" + compName + "**\n(👑 Właściciel)";
                    } else {
                        companyDisplay = "**" + compName + "**\n(👷 Pracownik)";
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            EmbedBuilder eb = new EmbedBuilder().setColor(Color.decode("#F1C40F"))
                    .setAuthor(LanguageManager.t(settings, "profile_author", target.getName()), null, target.getEffectiveAvatarUrl())
                    .setThumbnail(target.getEffectiveAvatarUrl())
                    .addField(LanguageManager.t(settings, "profile_field_cash"), "`" + userStats[0] + "` " + settings.currencyEmoji, true)
                    .addField(LanguageManager.t(settings, "profile_field_bank"), "`" + userStats[3] + "` " + settings.currencyEmoji, true)
                    .addField(LanguageManager.t(settings, "profile_field_level"), "Lvl `" + userStats[1] + "`", true)
                    .addField(LanguageManager.t(settings, "profile_field_exp"), "`" + userStats[2] + " / " + (userStats[1] * 100) + "` XP", false)
                    .addField(LanguageManager.t(settings, "profile_field_pet"), "**" + petName + "**", true)
                    .addField("🏢 Firma", companyDisplay, true);

            event.replyEmbeds(eb.build()).queue();
            return;
        }

        if (cmd.equals("work")) {
            long cur = System.currentTimeMillis();
            long cd = db.getCooldown(guildId, userId, "work");
            if (cur < cd) {
                event.reply(LanguageManager.t(settings, "cooldown_work", userId, (cd - cur) / 1000)).setEphemeral(true).queue();
                return;
            }
            double[] mults = db.getActiveMultipliers(guildId, userId);

            double compBonus = CompanyManager.getEmployeeBonusMultiplier(db, guildId, userId);

            int baseEarned = (int) ((random.nextInt((settings.maxWork - settings.minWork) + 1) + settings.minWork) * mults[0]);
            int finalEarned = (int) (baseEarned * compBonus);
            int exp = (int) ((random.nextInt(21) + 20) * mults[1]);

            db.addCoins(guildId, userId, finalEarned, "WORK");
            db.setCooldown(guildId, userId, "work", cur + 600000);

            int newLvl = db.addExpAndCheckLevel(guildId, userId, exp);
            if (newLvl > 0) {
                // Wywołanie zewnętrznego powiadomienia o awansie
                CoreManager.sendLevelUpNotification(event.getGuild(), event.getChannel(), userId, newLvl, settings);
            }

            CompanyManager.addCompanyRevenue(db, guildId, userId, finalEarned);

            EmbedBuilder eb = new EmbedBuilder().setColor(Color.decode("#2ECC71"))
                    .setAuthor(LanguageManager.t(settings, "work_author", event.getUser().getName()), null, event.getUser().getEffectiveAvatarUrl())
                    .setThumbnail(event.getUser().getEffectiveAvatarUrl())
                    .setDescription(LanguageManager.t(settings, "work_desc", userId, finalEarned, settings.currencyEmoji, exp));

            if (mults[0] > 1.0) eb.appendDescription(LanguageManager.t(settings, "pet_bonus"));
            if (compBonus > 1.0) eb.appendDescription("\n🏢 Otrzymujesz **+" + (int)Math.round((compBonus - 1.0) * 100) + "%** premii za pracę w firmie!");

            event.replyEmbeds(eb.build()).queue();
            return;
        }

        if (cmd.equals("daily")) {
            long cur = System.currentTimeMillis();
            long cd = db.getCooldown(guildId, userId, "daily");
            if (cur < cd) {
                event.reply(LanguageManager.t(settings, "cooldown_daily", userId, ((cd - cur) / 1000) / 3600)).setEphemeral(true).queue();
                return;
            }
            double[] mults = db.getActiveMultipliers(guildId, userId);

            double compBonus = CompanyManager.getEmployeeBonusMultiplier(db, guildId, userId);

            int baseEarned = (int) (settings.dailyAmount * mults[0]);
            int finalEarned = (int) (baseEarned * compBonus);
            int exp = (int) ((random.nextInt(101) + 150) * mults[1]);

            db.addCoins(guildId, userId, finalEarned, "DAILY");
            db.setCooldown(guildId, userId, "daily", cur + 86400000);

            int newLvl = db.addExpAndCheckLevel(guildId, userId, exp);
            if (newLvl > 0) {
                // Wywołanie zewnętrznego powiadomienia o awansie
                CoreManager.sendLevelUpNotification(event.getGuild(), event.getChannel(), userId, newLvl, settings);
            }

            CompanyManager.addCompanyRevenue(db, guildId, userId, finalEarned);

            EmbedBuilder eb = new EmbedBuilder().setColor(Color.decode("#3498DB"))
                    .setAuthor(LanguageManager.t(settings, "daily_author"), null, event.getUser().getEffectiveAvatarUrl())
                    .setThumbnail(event.getUser().getEffectiveAvatarUrl())
                    .setDescription(LanguageManager.t(settings, "daily_desc", userId, finalEarned, settings.currencyEmoji, exp));

            if (mults[0] > 1.0) eb.appendDescription(LanguageManager.t(settings, "daily_pet_bonus"));
            if (compBonus > 1.0) eb.appendDescription("\n🏢 Otrzymujesz **+" + (int)Math.round((compBonus - 1.0) * 100) + "%** premii za pracę w firmie!");

            event.replyEmbeds(eb.build()).queue();
            return;
        }

        if (cmd.equals("pay")) {
            User target = event.getOption("player").getAsUser();
            int amt = event.getOption("amount").getAsInt();
            if (target.isBot() || target.getId().equals(userId) || amt <= 0) {
                event.reply(LanguageManager.t(settings, "pay_err_params")).setEphemeral(true).queue();
                return;
            }
            int tax = (int) Math.round(amt * settings.transferTax);
            int net = amt - tax;
            if (db.removeCoins(guildId, userId, amt, "TRANSFER_SEND")) {
                db.addCoins(guildId, target.getId(), net, "TRANSFER_RECEIVE");
                EmbedBuilder eb = new EmbedBuilder().setColor(Color.decode("#1ABC9C"))
                        .setAuthor(LanguageManager.t(settings, "pay_author"), null, event.getUser().getEffectiveAvatarUrl())
                        .setThumbnail(target.getEffectiveAvatarUrl())
                        .setDescription(LanguageManager.t(settings, "pay_desc", userId, target.getId(), net, settings.currencyEmoji, tax));
                event.replyEmbeds(eb.build()).queue();
            } else {
                event.reply(LanguageManager.t(settings, "pay_no_funds")).setEphemeral(true).queue();
            }
            return;
        }

        if (cmd.equals("coinflip")) {
            int amt = event.getOption("amount").getAsInt();
            if (amt <= 0) {
                event.reply(LanguageManager.t(settings, "cf_err_stake")).setEphemeral(true).queue();
                return;
            }
            EmbedBuilder eb = new EmbedBuilder().setColor(Color.decode("#E67E22"))
                    .setAuthor(LanguageManager.t(settings, "cf_author"), null, event.getUser().getEffectiveAvatarUrl())
                    .setDescription(LanguageManager.t(settings, "cf_desc", userId, amt, settings.currencyEmoji));
            event.replyEmbeds(eb.build()).addComponents(ActionRow.of(
                    Button.success("cf_pay_cash:" + amt, LanguageManager.t(settings, "btn_cash")),
                    Button.primary("cf_pay_card:" + amt, LanguageManager.t(settings, "btn_card"))
            )).setEphemeral(true).queue();
            return;
        }

        if (cmd.equals("addmoney")) {
            if (event.getMember() != null && !event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
                event.reply(LanguageManager.t(settings, "admin_err_perm")).setEphemeral(true).queue();
                return;
            }
            User target = event.getOption("player").getAsUser();
            int amt = event.getOption("amount").getAsInt();
            if (amt <= 0) {
                event.reply(LanguageManager.t(settings, "err_amount")).setEphemeral(true).queue();
                return;
            }
            db.addCoins(guildId, target.getId(), amt, "ADMIN_ADD");
            EmbedBuilder eb = new EmbedBuilder().setColor(Color.decode("#D35400"))
                    .setAuthor(LanguageManager.t(settings, "admin_add_author"), null, event.getUser().getEffectiveAvatarUrl())
                    .setThumbnail(target.getEffectiveAvatarUrl())
                    .setDescription(LanguageManager.t(settings, "admin_add_desc", userId, target.getId(), amt, settings.currencyEmoji));
            event.replyEmbeds(eb.build()).queue();
        }
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        String[] parts = event.getComponentId().split(":");
        String action = parts[0];
        if (!action.startsWith("cf_pay_")) return;
        String guildId = event.getGuild().getId();
        String userId = event.getUser().getId();
        GuildSettings settings = db.getGuildSettings(guildId);
        int amt = Integer.parseInt(parts[1]);

        boolean success = action.equals("cf_pay_card") ? db.removeBankCoins(guildId, userId, amt) : db.removeCoins(guildId, userId, amt, "CASINO_BET");
        if (!success) {
            event.reply(LanguageManager.t(settings, "cf_no_funds")).setEphemeral(true).queue();
            return;
        }

        boolean win = random.nextBoolean();
        EmbedBuilder eb = new EmbedBuilder().setThumbnail(event.getUser().getEffectiveAvatarUrl());
        String payMethod = action.equals("cf_pay_card") ? LanguageManager.t(settings, "cf_pay_card") : LanguageManager.t(settings, "cf_pay_cash");
        if (win) {
            int winAmt = amt * 2;
            if (action.equals("cf_pay_card")) db.addBankCoins(guildId, userId, winAmt);
            else db.addCoins(guildId, userId, winAmt, "CASINO_WIN");
            eb.setColor(Color.GREEN).setAuthor(LanguageManager.t(settings, "cf_win_author"), null, event.getUser().getEffectiveAvatarUrl())
                    .setDescription(LanguageManager.t(settings, "cf_win_desc", userId, payMethod, winAmt, settings.currencyEmoji));
        } else {
            eb.setColor(Color.RED).setAuthor(LanguageManager.t(settings, "cf_lose_author"), null, event.getUser().getEffectiveAvatarUrl())
                    .setDescription(LanguageManager.t(settings, "cf_lose_desc", userId, amt, settings.currencyEmoji));
        }
        event.editMessageEmbeds(eb.build()).setComponents().queue();
    }
}