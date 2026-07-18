package pl.syntaxdevteam.listeners;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

public class EconomyManager extends ListenerAdapter {
    private final DatabaseManager db;
    private final Random random = new Random();

    public EconomyManager(DatabaseManager db) { this.db = db; }

    private static class JobDef {
        String id; String emoji;
        JobDef(String id, String emoji) {
            this.id = id; this.emoji = emoji;
        }
    }

    private final JobDef[] JOBS = {
            new JobDef("miner", "⛏️"),
            new JobDef("doctor", "👨‍⚕️"),
            new JobDef("hacker", "💻"),
            new JobDef("chef", "🧑‍🍳")
    };

    private String getLang(GuildSettings settings, String key, Object... args) {
        String result = LanguageManager.t(settings, key, args);
        return (result != null && !result.startsWith("TEXT_ERR")) ? result : key;
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        String userId = event.getUser().getId();
        GuildSettings settings = db.getGuildSettings(guildId);
        String cmd = event.getName();

        if (!settings.economyEnabled && !cmd.equals("config")) {
            event.reply(getLang(settings, "eco_disabled")).setEphemeral(true).queue();
            return;
        }
        db.logCommandUsage(cmd, guildId);

        if (cmd.equals("work")) {
            long cur = System.currentTimeMillis();
            long cd = db.getCooldown(guildId, userId, "work");
            if (cur < cd) {
                long sec = (cd - cur) / 1000;
                event.reply(getLang(settings, "work_cd", event.getUser().getAsMention(), sec)).setEphemeral(true).queue();
                return;
            }

            JobDef targetJob = JOBS[random.nextInt(JOBS.length)];
            int targetAmount = random.nextInt(5) + 1;

            String jobName = getLang(settings, "job_" + targetJob.id + "_name");
            String taskPrefix = getLang(settings, "job_" + targetJob.id + "_prefix");
            String unitName = getLang(settings, "job_" + targetJob.id + "_unit");

            EmbedBuilder eb = new EmbedBuilder()
                    .setColor(Color.ORANGE)
                    .setTitle(getLang(settings, "work_title"))
                    .setDescription(getLang(settings, "work_desc", targetJob.emoji, jobName, taskPrefix, targetAmount, unitName))
                    .setFooter("Econizer.gg");

            List<Button> jobButtons = new ArrayList<>();
            for (JobDef job : JOBS) {
                String btnName = getLang(settings, "job_" + job.id + "_name");
                jobButtons.add(Button.primary("w1:" + job.id + ":" + targetJob.id + ":" + targetAmount + ":" + userId, job.emoji + " " + btnName));
            }
            Collections.shuffle(jobButtons);

            event.replyEmbeds(eb.build()).addComponents(ActionRow.of(jobButtons)).setEphemeral(settings.hideEconomyReplies).queue();
            return;
        }

        if (cmd.equals("daily")) {
            long cur = System.currentTimeMillis();
            long cd = db.getCooldown(guildId, userId, "daily");
            if (cur < cd) {
                long hours = ((cd - cur) / 1000) / 3600;
                if (hours <= 0) hours = 1;
                event.reply(getLang(settings, "daily_cd", event.getUser().getAsMention(), hours)).setEphemeral(true).queue();
                return;
            }

            List<Integer> rewards = new ArrayList<>();
            rewards.add(settings.dailyAmount);
            rewards.add(settings.dailyAmount2);
            rewards.add(settings.dailyAmount3);
            Collections.shuffle(rewards);

            EmbedBuilder eb = new EmbedBuilder()
                    .setColor(Color.decode("#2ECC71"))
                    .setTitle(getLang(settings, "daily_title"))
                    .setDescription(getLang(settings, "daily_desc", settings.dailyAmount3, settings.currencyEmoji))
                    .setFooter("Econizer.gg");

            String payload = rewards.get(0) + "_" + rewards.get(1) + "_" + rewards.get(2);
            List<Button> safeButtons = new ArrayList<>();
            safeButtons.add(Button.success("d1:0:" + payload + ":" + userId, getLang(settings, "daily_btn_safe", 1)));
            safeButtons.add(Button.success("d1:1:" + payload + ":" + userId, getLang(settings, "daily_btn_safe", 2)));
            safeButtons.add(Button.success("d1:2:" + payload + ":" + userId, getLang(settings, "daily_btn_safe", 3)));

            event.replyEmbeds(eb.build()).addComponents(ActionRow.of(safeButtons)).setEphemeral(settings.hideEconomyReplies).queue();
            return;
        }

        if (cmd.equals("bank")) {
            db.checkAndApplyLoanInterest(guildId, userId);
            sendMainBankPanel(event, guildId, userId, settings, false);
            return;
        }

        if (cmd.equals("shop")) {
            String shopUrl = settings.shopBaseUrl + guildId;
            EmbedBuilder eb = new EmbedBuilder()
                    .setColor(Color.decode("#9B59B6"))
                    .setTitle(getLang(settings, "shop_title"))
                    .setThumbnail(event.getJDA().getSelfUser().getEffectiveAvatarUrl())
                    .setDescription(getLang(settings, "shop_desc_full"))
                    .setFooter("Econizer.gg");

            Button openShopBtn = Button.link(shopUrl, getLang(settings, "shop_btn"));
            event.replyEmbeds(eb.build()).addComponents(ActionRow.of(openShopBtn)).setEphemeral(true).queue();
            return;
        }

        if (cmd.equals("dashboard")) {
            String dashUrl = "https://econizer.syntaxdevteam.pl/dashboard";
            EmbedBuilder eb = new EmbedBuilder()
                    .setColor(Color.decode("#3498DB"))
                    .setTitle(getLang(settings, "dash_title"))
                    .setThumbnail(event.getUser().getEffectiveAvatarUrl())
                    .setDescription(getLang(settings, "dash_desc"))
                    .setFooter("Econizer.gg");

            Button openDashBtn = Button.link(dashUrl, getLang(settings, "dash_btn"));
            event.replyEmbeds(eb.build()).addComponents(ActionRow.of(openDashBtn)).setEphemeral(true).queue();
            return;
        }

        if (cmd.equals("config")) {
            if (event.getMember() != null && !event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
                event.reply(getLang(settings, "config_no_perm")).setEphemeral(true).queue();
                return;
            }
            String adminUrl = "https://econizer.syntaxdevteam.pl/admin/guild/" + guildId;
            EmbedBuilder eb = new EmbedBuilder()
                    .setColor(Color.decode("#E74C3C"))
                    .setTitle(getLang(settings, "config_title"))
                    .setThumbnail(event.getGuild().getIconUrl())
                    .setDescription(getLang(settings, "config_desc"))
                    .setFooter("Econizer.gg");

            Button openConfigBtn = Button.link(adminUrl, getLang(settings, "config_btn"));
            event.replyEmbeds(eb.build()).addComponents(ActionRow.of(openConfigBtn)).setEphemeral(true).queue();
            return;
        }

        if (cmd.equals("profile")) {
            OptionMapping uOpt = event.getOption("user");
            User target = uOpt != null ? uOpt.getAsUser() : event.getUser();
            if (target.isBot()) {
                event.reply(getLang(settings, "bot_profile")).setEphemeral(true).queue();
                return;
            }
            int[] userStats = db.getUserStats(guildId, target.getId());
            int petId = db.getUserPetId(guildId, target.getId());
            String petName = getLang(settings, "profile_pet_none");
            if (petId > 0) {
                Object[] c = db.getPetConfig(guildId, petId, settings.isPremium);
                if (c != null) petName = (String) c[0];
            }

            String companyDisplay = getLang(settings, "profile_comp_none");
            try (PreparedStatement pstmt = db.getConnection().prepareStatement(
                    "SELECT c.name, c.owner_id FROM bot_employees e JOIN bot_companies c ON e.company_id = c.id WHERE e.guild_id = ? AND e.user_id = ?")) {
                pstmt.setString(1, guildId); pstmt.setString(2, target.getId()); ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    String compName = rs.getString("name"); String ownerId = rs.getString("owner_id");
                    companyDisplay = target.getId().equals(ownerId) ? getLang(settings, "profile_comp_owner", compName) : getLang(settings, "profile_comp_emp", compName);
                }
            } catch (Exception e) { e.printStackTrace(); }

            EmbedBuilder eb = new EmbedBuilder().setColor(Color.decode("#F1C40F"))
                    .setAuthor(getLang(settings, "profile_author", target.getName()), null, target.getEffectiveAvatarUrl())
                    .setThumbnail(target.getEffectiveAvatarUrl())
                    .addField(getLang(settings, "profile_field_cash"), "`" + userStats[0] + "` " + settings.currencyEmoji, true)
                    .addField(getLang(settings, "profile_field_bank"), "`" + userStats[3] + "` " + settings.currencyEmoji, true)
                    .addField(getLang(settings, "profile_field_level"), "Lvl `" + userStats[1] + "`", true)
                    .addField(getLang(settings, "profile_field_exp"), "`" + userStats[2] + " / " + (userStats[1] * 100) + "` XP", false)
                    .addField(getLang(settings, "profile_field_pet"), "**" + petName + "**", true)
                    .addField(getLang(settings, "profile_field_comp"), companyDisplay, true)
                    .setFooter("Econizer.gg");

            event.replyEmbeds(eb.build()).setEphemeral(settings.hideEconomyReplies).queue();
            return;
        }

        if (cmd.equals("pay")) {
            User target = event.getOption("player").getAsUser();
            int amt = event.getOption("amount").getAsInt();
            if (target.isBot() || target.getId().equals(userId) || amt <= 0) {
                event.reply(getLang(settings, "pay_err_params")).setEphemeral(true).queue(); return;
            }
            int tax = (int) Math.round(amt * settings.transferTax); int net = amt - tax;
            if (db.removeCoins(guildId, userId, amt, "TRANSFER_SEND")) {
                db.addCoins(guildId, target.getId(), net, "TRANSFER_RECEIVE");
                EmbedBuilder eb = new EmbedBuilder().setColor(Color.decode("#1ABC9C"))
                        .setAuthor(getLang(settings, "pay_author"), null, event.getUser().getEffectiveAvatarUrl())
                        .setThumbnail(target.getEffectiveAvatarUrl())
                        .setDescription(getLang(settings, "pay_desc", userId, target.getId(), net, settings.currencyEmoji, tax))
                        .setFooter("Econizer.gg");
                event.replyEmbeds(eb.build()).setEphemeral(settings.hideEconomyReplies).queue();
            } else { event.reply(getLang(settings, "pay_no_funds")).setEphemeral(true).queue(); }
            return;
        }

        if (cmd.equals("coinflip")) {
            int amt = event.getOption("amount").getAsInt();
            if (amt <= 0) { event.reply(getLang(settings, "minigame_err_stake")).setEphemeral(true).queue(); return; }
            EmbedBuilder eb = new EmbedBuilder().setColor(Color.decode("#E67E22"))
                    .setAuthor(getLang(settings, "minigame_author"), null, event.getUser().getEffectiveAvatarUrl())
                    .setDescription(getLang(settings, "minigame_desc", userId, amt, settings.currencyEmoji))
                    .setFooter("Econizer.gg");
            event.replyEmbeds(eb.build()).addComponents(
                    ActionRow.of(Button.success("cf_pay_cash:" + amt, getLang(settings, "btn_cash")),
                            Button.primary("cf_pay_card:" + amt, getLang(settings, "btn_card")))
            ).setEphemeral(true).queue();
            return;
        }

        if (cmd.equals("addmoney")) {
            if (event.getMember() != null && !event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
                event.reply(getLang(settings, "admin_err_perm")).setEphemeral(true).queue(); return;
            }
            User target = event.getOption("player").getAsUser(); int amt = event.getOption("amount").getAsInt();
            if (amt <= 0) { event.reply(getLang(settings, "err_amount")).setEphemeral(true).queue(); return; }
            db.addCoins(guildId, target.getId(), amt, "ADMIN_ADD");
            EmbedBuilder eb = new EmbedBuilder().setColor(Color.decode("#D35400"))
                    .setAuthor(getLang(settings, "addmoney_author"), null, event.getUser().getEffectiveAvatarUrl())
                    .setThumbnail(target.getEffectiveAvatarUrl())
                    .setDescription(getLang(settings, "addmoney_desc", target.getId(), amt, settings.currencyEmoji))
                    .setFooter("Econizer.gg");
            event.replyEmbeds(eb.build()).queue();
            return;
        }

        if (cmd.equals("removemoney")) {
            if (event.getMember() != null && !event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
                event.reply(getLang(settings, "admin_err_perm")).setEphemeral(true).queue(); return;
            }
            User target = event.getOption("player").getAsUser(); int amt = event.getOption("amount").getAsInt();
            if (amt <= 0) { event.reply(getLang(settings, "err_amount")).setEphemeral(true).queue(); return; }

            db.removeCoins(guildId, target.getId(), amt, "ADMIN_REMOVE");
            EmbedBuilder eb = new EmbedBuilder().setColor(Color.decode("#7F8C8D"))
                    .setAuthor(getLang(settings, "removemoney_author"), null, event.getUser().getEffectiveAvatarUrl())
                    .setThumbnail(target.getEffectiveAvatarUrl())
                    .setDescription(getLang(settings, "removemoney_desc", target.getId(), amt, settings.currencyEmoji))
                    .setFooter("Econizer.gg");
            event.replyEmbeds(eb.build()).queue();
            return;
        }

        if (cmd.equals("market")) {
            sendMarketPanel(event, guildId, userId, settings);
            return;
        }

        if (cmd.equals("reseteco")) {
            if (event.getMember() != null && !event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
                event.reply(getLang(settings, "admin_err_perm")).setEphemeral(true).queue(); return;
            }
            if (!event.getOption("confirm").getAsBoolean()) {
                event.reply(getLang(settings, "reset_confirm_fail")).setEphemeral(true).queue(); return;
            }
            db.resetServerEconomy(guildId);
            WebAPIManager.reportGuildAction(guildId, event.getGuild().getName(), "economy_wipe");
            event.replyEmbeds(new EmbedBuilder().setColor(Color.RED).setTitle(getLang(settings, "reset_title")).setThumbnail(event.getGuild().getIconUrl()).setDescription(getLang(settings, "reset_desc", userId)).setFooter("Econizer.gg").build()).queue();
        }
    }

    private void sendMarketPanel(SlashCommandInteractionEvent event, String guildId, String userId, GuildSettings settings) {
        Map<String, Integer> stocks = db.getAndRefreshStocks(guildId);

        EmbedBuilder eb = new EmbedBuilder()
                .setColor(Color.decode("#00FFA3"))
                .setTitle(getLang(settings, "market_terminal_title"))
                .setDescription(getLang(settings, "market_terminal_desc"));

        if (stocks.isEmpty()) {
            eb.addField(getLang(settings, "market_offline_field"), getLang(settings, "market_closed_desc"), false);
            eb.setFooter("Econizer.gg");
            event.replyEmbeds(eb.build()).setEphemeral(false).queue();
            return;
        }

        long timeWindow = System.currentTimeMillis() / 300000L;

        for (Map.Entry<String, Integer> entry : stocks.entrySet()) {
            String ticker = entry.getKey();
            int currentPrice = entry.getValue();
            int userOwned = db.getUserStockAmount(guildId, userId, ticker);

            Random tr = new Random(ticker.hashCode() ^ timeWindow);
            double changePct = (tr.nextDouble() * 24.0) - 12.0;

            String trendEmoji = changePct >= 0 ? "🟢 📈" : "🔴 📉";
            String sign = changePct >= 0 ? "+" : "";
            String trendStr = String.format(java.util.Locale.US, "%s `%s%.2f%%` %s", trendEmoji, sign, changePct, getLang(settings, "market_trend_time"));

            String portfolioStr = userOwned > 0
                    ? getLang(settings, "market_port_yes", userOwned, (userOwned * currentPrice), settings.currencyEmoji)
                    : getLang(settings, "market_port_no");

            eb.addField("🪙 " + ticker.toUpperCase(),
                    getLang(settings, "market_curr_price", currentPrice, settings.currencyEmoji) + "\n" +
                            getLang(settings, "market_trend_label") + " " + trendStr + "\n" +
                            portfolioStr, false);
        }

        eb.setFooter("Econizer.gg • " + getLang(settings, "market_footer"));

        Button buyBtn = Button.success("market_buy_p:" + userId, getLang(settings, "market_btn_buy"));
        Button sellBtn = Button.danger("market_sell_p:" + userId, getLang(settings, "market_btn_sell"));
        Button webBtn = Button.link("https://econizer.syntaxdevteam.pl/market", getLang(settings, "market_btn_web"));

        event.replyEmbeds(eb.build()).addComponents(ActionRow.of(buyBtn, sellBtn, webBtn)).setEphemeral(false).queue();
    }

    private void sendMainBankPanel(net.dv8tion.jda.api.interactions.callbacks.IReplyCallback event, String guildId, String userId, GuildSettings settings, boolean isEdit) {
        long[] bankData = db.getBankData(guildId, userId); long now = System.currentTimeMillis(); long left = 86400000L - (now - bankData[2]);
        String lokataStatus = (left <= 0) ? getLang(settings, "bank_dep_ready") : getLang(settings, "bank_dep_wait", (left / 3600000L), ((left % 3600000L) / 60000L));

        EmbedBuilder eb = new EmbedBuilder().setColor(Color.decode("#3498DB")).setTitle(getLang(settings, "bank_title")).setDescription(getLang(settings, "bank_desc", bankData[0], settings.currencyEmoji, bankData[5], settings.currencyEmoji, lokataStatus)).setFooter("Econizer.gg");
        List<Button> buttons = new ArrayList<>(); buttons.add(Button.success("bank_dep_p:" + userId, getLang(settings, "bank_btn_dep"))); buttons.add(Button.danger("bank_wit_p:" + userId, getLang(settings, "bank_btn_wit"))); buttons.add(Button.primary("bank_clm:" + userId, getLang(settings, "bank_btn_clm"))); buttons.add(Button.secondary("bank_cred:" + userId, getLang(settings, "bank_btn_cred")));

        if (isEdit && event instanceof ButtonInteractionEvent) ((ButtonInteractionEvent) event).editMessageEmbeds(eb.build()).setComponents(ActionRow.of(buttons)).queue();
        else {
            if (event instanceof SlashCommandInteractionEvent) {
                ((SlashCommandInteractionEvent) event).replyEmbeds(eb.build()).addComponents(ActionRow.of(buttons)).setEphemeral(settings.hideEconomyReplies).queue();
            } else {
                event.replyEmbeds(eb.build()).addComponents(ActionRow.of(buttons)).setEphemeral(true).queue();
            }
        }
    }

    private void sendCreditPanel(net.dv8tion.jda.api.interactions.callbacks.IReplyCallback event, String guildId, String userId, GuildSettings settings) {
        long[] bankData = db.getBankData(guildId, userId); long maxCredit = bankData[4] * 5000;
        EmbedBuilder eb = new EmbedBuilder().setColor(Color.decode("#E74C3C")).setTitle(getLang(settings, "cred_title")).setDescription(getLang(settings, "cred_desc", maxCredit, settings.currencyEmoji, bankData[1], settings.currencyEmoji)).setFooter("Econizer.gg");
        List<Button> buttons = new ArrayList<>(); buttons.add(Button.success("cred_take_p:" + userId, getLang(settings, "cred_btn_take"))); buttons.add(Button.primary("cred_pay_p:" + userId, getLang(settings, "cred_btn_pay"))); buttons.add(Button.secondary("bank_main:" + userId, getLang(settings, "cred_btn_back")));
        if (event instanceof ButtonInteractionEvent) ((ButtonInteractionEvent) event).editMessageEmbeds(eb.build()).setComponents(ActionRow.of(buttons)).queue();
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        String[] parts = event.getComponentId().split(":");
        String action = parts[0];
        String guildId = event.getGuild().getId();
        String userId = event.getUser().getId();
        GuildSettings settings = db.getGuildSettings(guildId);

        if (action.startsWith("bank_") || action.startsWith("cred_") || action.startsWith("market_")) {
            if (!parts[parts.length - 1].equals(userId)) {
                event.reply(getLang(settings, "err_not_yours")).setEphemeral(true).queue(); return;
            }
        }

        if (action.equals("bank_main")) { sendMainBankPanel(event, guildId, userId, settings, true); }
        else if (action.equals("bank_cred")) { sendCreditPanel(event, guildId, userId, settings); }
        else if (action.equals("market_buy_p")) {
            TextInput tickerInput = TextInput.create("ticker", getLang(settings, "market_mod_ticker"), TextInputStyle.SHORT).setRequired(true).build();
            TextInput amountInput = TextInput.create("amount", getLang(settings, "market_mod_amount_b"), TextInputStyle.SHORT).setRequired(true).build();
            Modal modal = Modal.create("mod_market_buy", getLang(settings, "market_mod_buy_title")).addComponents(ActionRow.of(tickerInput), ActionRow.of(amountInput)).build();
            event.replyModal(modal).queue();
        }
        else if (action.equals("market_sell_p")) {
            TextInput tickerInput = TextInput.create("ticker", getLang(settings, "market_mod_ticker"), TextInputStyle.SHORT).setRequired(true).build();
            TextInput amountInput = TextInput.create("amount", getLang(settings, "market_mod_amount_s"), TextInputStyle.SHORT).setRequired(true).build();
            Modal modal = Modal.create("mod_market_sell", getLang(settings, "market_mod_sell_title")).addComponents(ActionRow.of(tickerInput), ActionRow.of(amountInput)).build();
            event.replyModal(modal).queue();
        }
        else if (action.equals("bank_dep_p")) {
            TextInput amountInput = TextInput.create("amount", getLang(settings, "bank_mod_amt"), TextInputStyle.SHORT).setRequired(true).build();
            Modal modal = Modal.create("mod_bank_dep", getLang(settings, "bank_mod_dep_title")).addComponents(ActionRow.of(amountInput)).build();
            event.replyModal(modal).queue();
        } else if (action.equals("bank_wit_p")) {
            TextInput amountInput = TextInput.create("amount", getLang(settings, "bank_mod_amt"), TextInputStyle.SHORT).setRequired(true).build();
            Modal modal = Modal.create("mod_bank_wit", getLang(settings, "bank_mod_wit_title")).addComponents(ActionRow.of(amountInput)).build();
            event.replyModal(modal).queue();
        } else if (action.equals("cred_take_p")) {
            TextInput amountInput = TextInput.create("amount", getLang(settings, "cred_mod_amt_t"), TextInputStyle.SHORT).setRequired(true).build();
            Modal modal = Modal.create("mod_cred_take", getLang(settings, "cred_mod_take_title")).addComponents(ActionRow.of(amountInput)).build();
            event.replyModal(modal).queue();
        } else if (action.equals("cred_pay_p")) {
            TextInput amountInput = TextInput.create("amount", getLang(settings, "cred_mod_amt_p"), TextInputStyle.SHORT).setRequired(true).build();
            Modal modal = Modal.create("mod_cred_pay", getLang(settings, "cred_mod_pay_title")).addComponents(ActionRow.of(amountInput)).build();
            event.replyModal(modal).queue();
        } else if (action.equals("bank_clm")) {
            long[] bankData = db.getBankData(guildId, userId); long lastClaim = bankData[2]; long now = System.currentTimeMillis();
            if (now - lastClaim < 86400000L) { long left = 86400000L - (now - lastClaim); event.reply(getLang(settings, "bank_clm_wait", (left / 3600000L), ((left % 3600000L) / 60000L))).setEphemeral(true).queue(); return; }
            long bankBalance = bankData[0]; if (bankBalance < 100) { event.reply(getLang(settings, "bank_clm_min", settings.currencyEmoji)).setEphemeral(true).queue(); return; }
            int interest = (int) (bankBalance * 0.02); if (interest < 1) interest = 1;
            db.addBankCoins(guildId, userId, interest); db.updateDepositClaim(guildId, userId);
            event.reply(getLang(settings, "bank_clm_ok", interest, settings.currencyEmoji)).setEphemeral(true).queue();
            sendMainBankPanel(event, guildId, userId, settings, true);
        }

        if (action.equals("w1")) {
            String clickedJob = parts[1]; String targetJob = parts[2]; String targetAmount = parts[3]; String ownerId = parts[4];
            if (!userId.equals(ownerId)) { event.reply(getLang(settings, "err_not_yours")).setEphemeral(true).queue(); return; }
            if (!clickedJob.equals(targetJob)) { db.setCooldown(guildId, userId, "work", System.currentTimeMillis() + 600000); event.editMessageEmbeds(new EmbedBuilder().setColor(Color.RED).setTitle(getLang(settings, "work_err_title")).setDescription(getLang(settings, "work_err_desc")).setFooter("Econizer.gg").build()).setComponents().queue(); return; }
            JobDef currentJobInfo = null; for (JobDef j : JOBS) { if (j.id.equals(targetJob)) currentJobInfo = j; }

            // POPRAWKA: Usunięto ".id" ze Stringa
            String jobName = getLang(settings, "job_" + targetJob + "_name");
            String taskPrefix = getLang(settings, "job_" + targetJob + "_prefix");
            String unitName = getLang(settings, "job_" + targetJob + "_unit");

            EmbedBuilder eb = new EmbedBuilder().setColor(Color.YELLOW).setTitle(getLang(settings, "work_ver_title", currentJobInfo.emoji, jobName)).setDescription(getLang(settings, "work_ver_desc", taskPrefix, targetAmount, unitName)).setFooter("Econizer.gg");
            List<Button> amountButtons = new ArrayList<>(); for (int i = 1; i <= 5; i++) amountButtons.add(Button.secondary("w2:" + i + ":" + targetAmount + ":" + ownerId, String.valueOf(i)));
            event.editMessageEmbeds(eb.build()).setComponents(ActionRow.of(amountButtons)).queue(); return;
        }

        if (action.equals("w2")) {
            String clickedAmount = parts[1]; String targetAmount = parts[2]; String ownerId = parts[3];
            if (!userId.equals(ownerId)) { event.reply(getLang(settings, "err_not_yours")).setEphemeral(true).queue(); return; }
            db.setCooldown(guildId, userId, "work", System.currentTimeMillis() + 600000);
            if (!clickedAmount.equals(targetAmount)) { event.editMessageEmbeds(new EmbedBuilder().setColor(Color.RED).setTitle(getLang(settings, "work_err2_title")).setDescription(getLang(settings, "work_err2_desc", clickedAmount, targetAmount)).setFooter("Econizer.gg").build()).setComponents().queue(); return; }
            double[] mults = db.getActiveMultipliers(guildId, userId); double compBonus = CompanyManager.getEmployeeBonusMultiplier(db, guildId, userId);
            int baseEarned = (int) ((random.nextInt((settings.maxWork - settings.minWork) + 1) + settings.minWork) * mults[0]); int finalEarned = (int) (baseEarned * compBonus); int exp = (int) ((random.nextInt(21) + 20) * mults[1]);
            int deducted = db.addCoins(guildId, userId, finalEarned, "WORK"); int newLvl = db.addExpAndCheckLevel(guildId, userId, exp);
            if (newLvl > 0 && event.getChannel() != null) CoreManager.sendLevelUpNotification(event.getGuild(), event.getChannel().asTextChannel(), userId, newLvl, settings);
            CompanyManager.addCompanyRevenue(db, guildId, userId, finalEarned);
            EmbedBuilder eb = new EmbedBuilder().setColor(Color.GREEN).setTitle(getLang(settings, "work_ok_title")).setDescription(getLang(settings, "work_ok_desc", finalEarned, settings.currencyEmoji, exp)).setFooter("Econizer.gg");
            if (mults[0] > 1.0) eb.appendDescription("\n🐾 *" + getLang(settings, "work_bonus_pet") + "*"); if (compBonus > 1.0) eb.appendDescription("\n🏢 " + getLang(settings, "work_bonus_comp", (int)Math.round((compBonus - 1.0) * 100))); if (deducted > 0) eb.appendDescription("\n⚖️ **Ważne:** *" + getLang(settings, "debt_confiscation", deducted, settings.currencyEmoji) + "*");
            event.editMessageEmbeds(eb.build()).setComponents().queue(); return;
        }

        if (action.equals("d1")) {
            int selectedIndex = Integer.parseInt(parts[1]); String[] rewardsStr = parts[2].split("_"); String ownerId = parts[3];
            if (!userId.equals(ownerId)) { event.reply(getLang(settings, "err_not_yours")).setEphemeral(true).queue(); return; }
            db.setCooldown(guildId, userId, "daily", System.currentTimeMillis() + 86400000); int wonAmountBase = Integer.parseInt(rewardsStr[selectedIndex]);
            double[] mults = db.getActiveMultipliers(guildId, userId); double compBonus = CompanyManager.getEmployeeBonusMultiplier(db, guildId, userId);
            int baseEarned = (int) (wonAmountBase * mults[0]); int finalEarned = (int) (baseEarned * compBonus); int exp = (int) ((random.nextInt(101) + 150) * mults[1]);
            int deducted = db.addCoins(guildId, userId, finalEarned, "DAILY"); int newLvl = db.addExpAndCheckLevel(guildId, userId, exp);
            if (newLvl > 0 && event.getChannel() != null) CoreManager.sendLevelUpNotification(event.getGuild(), event.getChannel().asTextChannel(), userId, newLvl, settings);
            CompanyManager.addCompanyRevenue(db, guildId, userId, finalEarned);
            StringBuilder summary = new StringBuilder().append(getLang(settings, "daily_ok_main", (selectedIndex + 1), wonAmountBase, settings.currencyEmoji)).append("\n\n");
            if (compBonus > 1.0 || mults[0] > 1.0) summary.append(getLang(settings, "daily_ok_bonus", finalEarned, settings.currencyEmoji)).append("\n\n");
            summary.append(getLang(settings, "daily_others")).append("\n");
            for (int i = 0; i < 3; i++) { if (i == selectedIndex) summary.append("> 🟢 **Sejf ").append(i + 1).append(":** ").append(getLang(settings, "daily_yours")).append(" **").append(rewardsStr[i]).append(" **").append(settings.currencyEmoji).append("\n"); else { int lostAmount = Integer.parseInt(rewardsStr[i]); summary.append("> ").append(lostAmount == settings.dailyAmount3 ? "💎" : "🔒").append(" **Sejf ").append(i + 1).append(":** **").append(lostAmount).append("** ").append(settings.currencyEmoji).append("\n"); } }
            EmbedBuilder eb = new EmbedBuilder().setColor(Color.decode("#F1C40F")).setTitle(getLang(settings, "daily_ok_title")).setThumbnail(event.getUser().getEffectiveAvatarUrl()).setDescription(summary.toString()).setFooter("Econizer.gg");
            if (mults[0] > 1.0) eb.appendDescription("\n🐾 *" + getLang(settings, "daily_pet") + "*"); if (compBonus > 1.0) eb.appendDescription("\n🏢 " + getLang(settings, "work_bonus_comp", (int)Math.round((compBonus - 1.0) * 100))); if (deducted > 0) eb.appendDescription("\n⚖️ **Ważne:** *" + getLang(settings, "debt_confiscation", deducted, settings.currencyEmoji) + "*");
            event.editMessageEmbeds(eb.build()).setComponents().queue(); return;
        }

        if (action.startsWith("cf_pay_")) {
            int amt = Integer.parseInt(parts[1]); boolean success = action.equals("cf_pay_card") ? db.removeBankCoins(guildId, userId, amt) : db.removeCoins(guildId, userId, amt, "MINIGAME_BET");
            if (!success) { event.reply(getLang(settings, "minigame_no_funds")).setEphemeral(true).queue(); return; }
            boolean win = random.nextBoolean(); EmbedBuilder eb = new EmbedBuilder().setThumbnail(event.getUser().getEffectiveAvatarUrl()).setFooter("Econizer.gg"); String payMethod = action.equals("cf_pay_card") ? getLang(settings, "btn_card") : getLang(settings, "btn_cash");
            if (win) {
                int winAmt = amt * 2; int deducted = action.equals("cf_pay_card") ? 0 : db.addCoins(guildId, userId, winAmt, "MINIGAME_WIN"); if (action.equals("cf_pay_card")) db.addBankCoins(guildId, userId, winAmt);
                eb.setColor(Color.GREEN).setAuthor(getLang(settings, "minigame_win_author"), null, event.getUser().getEffectiveAvatarUrl()).setDescription(getLang(settings, "minigame_win_desc", userId, payMethod, winAmt, settings.currencyEmoji)); if (deducted > 0) eb.appendDescription("\n⚖️ *" + getLang(settings, "debt_confiscation", deducted, settings.currencyEmoji) + "*");
            } else { eb.setColor(Color.RED).setAuthor(getLang(settings, "minigame_lose_author"), null, event.getUser().getEffectiveAvatarUrl()).setDescription(getLang(settings, "minigame_lose_desc", userId, amt, settings.currencyEmoji)); }
            event.editMessageEmbeds(eb.build()).setComponents().queue();
        }
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        String modalId = event.getModalId();
        String guildId = event.getGuild().getId();
        String userId = event.getUser().getId();
        GuildSettings settings = db.getGuildSettings(guildId);

        if (modalId.equals("mod_market_buy") || modalId.equals("mod_market_sell")) {
            String ticker = event.getValue("ticker").getAsString().toUpperCase();
            String amountStr = event.getValue("amount").getAsString();

            try {
                int ilosc = Integer.parseInt(amountStr);
                if (ilosc <= 0) { event.reply(getLang(settings, "err_amount")).setEphemeral(true).queue(); return; }

                Map<String, Integer> stocks = db.getAndRefreshStocks(guildId);
                if (!stocks.containsKey(ticker)) { event.reply(getLang(settings, "market_mod_err_not_found")).setEphemeral(true).queue(); return; }

                int pricePerUnit = stocks.get(ticker);
                int totalCost = pricePerUnit * ilosc;

                if (modalId.equals("mod_market_buy")) {
                    if (db.removeCoins(guildId, userId, totalCost, "MARKET_BUY")) {
                        db.updateUserStock(guildId, userId, ticker, ilosc, pricePerUnit);
                        event.reply(getLang(settings, "market_buy_success", ilosc, ticker, totalCost, settings.currencyEmoji)).setEphemeral(true).queue();
                    } else {
                        event.reply(getLang(settings, "market_buy_err", totalCost, settings.currencyEmoji)).setEphemeral(true).queue();
                    }
                } else {
                    int currentOwned = db.getUserStockAmount(guildId, userId, ticker);
                    if (currentOwned < ilosc) { event.reply(getLang(settings, "market_sell_err_qty", ticker, currentOwned)).setEphemeral(true).queue(); return; }

                    db.updateUserStock(guildId, userId, ticker, -ilosc, pricePerUnit);
                    int deducted = db.addCoins(guildId, userId, totalCost, "MARKET_SELL");

                    String msg = getLang(settings, "market_sell_success", ilosc, ticker, totalCost, settings.currencyEmoji);
                    if (deducted > 0) msg += "\n⚖️ **Zajęcie komornicze:** *" + getLang(settings, "debt_confiscation", deducted, settings.currencyEmoji) + "*";

                    event.reply(msg).setEphemeral(true).queue();
                }
            } catch (NumberFormatException e) {
                event.reply(getLang(settings, "mod_err_number")).setEphemeral(true).queue();
            }
            return;
        }

        try {
            int amount = Integer.parseInt(event.getValue("amount").getAsString());
            if (amount <= 0) { event.reply(getLang(settings, "err_amount")).setEphemeral(true).queue(); return; }

            if (modalId.equals("mod_bank_dep")) {
                if (db.removeCoins(guildId, userId, amount, "BANK_TRANSFER")) { db.addBankCoins(guildId, userId, amount); event.reply(getLang(settings, "bank_dep_success", amount, settings.currencyEmoji)).setEphemeral(true).queue(); }
                else { event.reply(getLang(settings, "pay_no_funds")).setEphemeral(true).queue(); }
            }
            else if (modalId.equals("mod_bank_wit")) {
                if (db.removeBankCoins(guildId, userId, amount)) { db.addCoins(guildId, userId, amount, "BANK_TRANSFER"); event.reply(getLang(settings, "bank_wit_success", amount, settings.currencyEmoji)).setEphemeral(true).queue(); }
                else { event.reply(getLang(settings, "bank_err_funds")).setEphemeral(true).queue(); }
            }
            else if (modalId.equals("mod_cred_take")) {
                long[] bankData = db.getBankData(guildId, userId); if (bankData[1] > 0) { event.reply(getLang(settings, "cred_err_active")).setEphemeral(true).queue(); return; }
                int maxCredit = (int) bankData[4] * 5000; if (amount > maxCredit) { event.reply(getLang(settings, "cred_err_max", maxCredit, settings.currencyEmoji)).setEphemeral(true).queue(); return; }
                int toPay = (int) (amount * 1.15); db.takeLoan(guildId, userId, toPay); db.addBankCoins(guildId, userId, amount);
                event.reply(getLang(settings, "cred_success", amount, settings.currencyEmoji, toPay)).setEphemeral(true).queue();
            }
            else if (modalId.equals("mod_cred_pay")) {
                long[] bankData = db.getBankData(guildId, userId); int currentLoan = (int) bankData[1]; if (currentLoan == 0) { event.reply(getLang(settings, "cred_err_none")).setEphemeral(true).queue(); return; }
                if (amount > currentLoan) amount = currentLoan;
                if (db.removeCoins(guildId, userId, amount, "LOAN_PAY")) { db.payLoan(guildId, userId, amount); event.reply(getLang(settings, "cred_pay_success_cash", amount, settings.currencyEmoji)).setEphemeral(true).queue(); }
                else if (db.removeBankCoins(guildId, userId, amount)) { db.payLoan(guildId, userId, amount); event.reply(getLang(settings, "cred_pay_success_bank", amount, settings.currencyEmoji)).setEphemeral(true).queue(); }
                else { event.reply(getLang(settings, "cred_pay_err")).setEphemeral(true).queue(); }
            }
        } catch (NumberFormatException e) { event.reply(getLang(settings, "mod_err_number")).setEphemeral(true).queue(); }
    }
}