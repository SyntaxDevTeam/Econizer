package pl.syntaxdevteam.listeners;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class CrimeManager extends ListenerAdapter {
    private final DatabaseManager db;
    private final Random random = new Random();

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

        if (!settings.economyEnabled) {
            event.reply(LanguageManager.t(settings, "eco_disabled")).setEphemeral(true).queue();
            return;
        }

        if (cmd.equals("napad")) {
            long currentTime = System.currentTimeMillis();
            String cdKey = guildId + "-" + userId;

            if (heistCooldowns.containsKey(cdKey) && currentTime < heistCooldowns.get(cdKey)) {
                long hoursLeft = ((heistCooldowns.get(cdKey) - currentTime) / 1000) / 3600;
                event.reply(LanguageManager.t(settings, "heist_cooldown", userId, hoursLeft)).setEphemeral(true).queue();
                return;
            }

            boolean posiadaSprzet = hasC4.getOrDefault(cdKey, false);
            String gearLine = posiadaSprzet
                    ? LanguageManager.t(settings, "heist_gear_owned")
                    : LanguageManager.t(settings, "heist_gear_missing");

            EmbedBuilder eb = new EmbedBuilder().setColor(Color.decode("#34495E"))
                    .setAuthor(LanguageManager.t(settings, "heist_author"), null, event.getUser().getEffectiveAvatarUrl())
                    .setDescription(LanguageManager.t(settings, "heist_intro") + gearLine);

            if (!posiadaSprzet) {
                event.replyEmbeds(eb.build()).addComponents(ActionRow.of(
                        Button.success("heist_buy_cash", LanguageManager.t(settings, "heist_btn_buy_cash", C4_COST)),
                        Button.primary("heist_buy_card", LanguageManager.t(settings, "heist_btn_buy_card", C4_COST))
                )).setEphemeral(true).queue();
            } else {
                event.replyEmbeds(eb.build()).addComponents(ActionRow.of(
                        Button.danger("heist_start", LanguageManager.t(settings, "heist_btn_start"))
                )).setEphemeral(true).queue();
            }
            return;
        }

        if (cmd.equals("okradnij")) {
            long currentRobTime = System.currentTimeMillis();
            long robCooldownTime = db.getCooldown(guildId, userId, "robbery");

            if (currentRobTime < robCooldownTime) {
                long minutesLeft = ((robCooldownTime - currentRobTime) / 1000) / 60;
                event.reply(LanguageManager.t(settings, "rob_cooldown", userId, minutesLeft)).setEphemeral(true).queue();
                return;
            }

            User ofiara = event.getOption("ofiara").getAsUser();
            if (ofiara.isBot() || ofiara.getId().equals(userId)) {
                event.reply(LanguageManager.t(settings, "rob_invalid")).setEphemeral(true).queue();
                return;
            }

            int ofiaraGotowka = db.getUserStats(guildId, ofiara.getId())[0];
            if (ofiaraGotowka < 100) {
                event.reply(LanguageManager.t(settings, "rob_no_cash")).setEphemeral(true).queue();
                return;
            }

            db.setCooldown(guildId, userId, "robbery", currentRobTime + 7200000);

            if (random.nextInt(100) < 40) {
                int ukradzione = (int) (ofiaraGotowka * (random.nextDouble() * 0.15 + 0.05));
                db.removeCoins(guildId, ofiara.getId(), ukradzione, "gotowka");
                db.addCoins(guildId, userId, ukradzione, "gotowka");
                event.reply(LanguageManager.t(settings, "rob_success", userId, ukradzione, settings.currencyEmoji, ofiara.getId())).queue();
            } else {
                int kara = 250;
                if (!db.removeCoins(guildId, userId, kara, "gotowka")) db.removeBankCoins(guildId, userId, kara);
                event.reply(LanguageManager.t(settings, "rob_fail", userId, ofiara.getId(), kara, settings.currencyEmoji)).queue();
            }
            return;
        }

        if (cmd.equals("bounty")) {
            User poszukiwany = event.getOption("gracz").getAsUser();
            int kwota = event.getOption("kwota").getAsInt();
            if (kwota <= 0) {
                event.reply(LanguageManager.t(settings, "bounty_err_amount")).setEphemeral(true).queue();
                return;
            }

            if (db.removeCoins(guildId, userId, kwota, "gotowka")) {
                db.addBounty(guildId, poszukiwany.getId(), kwota);
                EmbedBuilder eb = new EmbedBuilder().setColor(Color.decode("#C0392B"))
                        .setAuthor(LanguageManager.t(settings, "bounty_author"), null, event.getUser().getEffectiveAvatarUrl())
                        .setThumbnail(poszukiwany.getEffectiveAvatarUrl())
                        .setDescription(LanguageManager.t(settings, "bounty_desc", userId, poszukiwany.getId(), kwota, settings.currencyEmoji));
                event.replyEmbeds(eb.build()).queue();
            } else {
                event.reply(LanguageManager.t(settings, "bounty_no_funds")).setEphemeral(true).queue();
            }
            return;
        }

        if (cmd.equals("poluj")) {
            long currentHuntTime = System.currentTimeMillis();
            long huntCooldownTime = db.getCooldown(guildId, userId, "hunt");

            if (currentHuntTime < huntCooldownTime) {
                long minutesLeft = ((huntCooldownTime - currentHuntTime) / 1000) / 60;
                event.reply(LanguageManager.t(settings, "hunt_cooldown", userId, minutesLeft)).setEphemeral(true).queue();
                return;
            }

            User poszukiwany = event.getOption("gracz").getAsUser();
            int bounty = db.getBounty(guildId, poszukiwany.getId());
            if (bounty <= 0) {
                event.reply(LanguageManager.t(settings, "hunt_no_bounty")).setEphemeral(true).queue();
                return;
            }

            db.setCooldown(guildId, userId, "hunt", currentHuntTime + 3600000);

            if (random.nextInt(100) < 30) {
                db.clearBounty(guildId, poszukiwany.getId());
                db.addCoins(guildId, userId, bounty, "gotowka");
                EmbedBuilder eb = new EmbedBuilder().setColor(Color.decode("#27AE60"))
                        .setAuthor(LanguageManager.t(settings, "hunt_success_author"), null, event.getUser().getEffectiveAvatarUrl())
                        .setThumbnail(event.getUser().getEffectiveAvatarUrl())
                        .setDescription(LanguageManager.t(settings, "hunt_success_desc", userId, poszukiwany.getId(), bounty, settings.currencyEmoji));
                event.replyEmbeds(eb.build()).queue();
            } else {
                event.reply(LanguageManager.t(settings, "hunt_fail", userId, poszukiwany.getId())).queue();
            }
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
                String payMethod = useCard
                        ? LanguageManager.t(settings, "heist_dealer_pay_card")
                        : LanguageManager.t(settings, "heist_dealer_pay_cash");
                event.editMessageEmbeds(new EmbedBuilder().setColor(Color.GREEN)
                                .setDescription(LanguageManager.t(settings, "heist_dealer_ok", payMethod)).build())
                        .setComponents(ActionRow.of(Button.danger("heist_start", LanguageManager.t(settings, "heist_btn_start")))).queue();
            } else {
                event.reply(LanguageManager.t(settings, "heist_buy_fail", C4_COST)).setEphemeral(true).queue();
            }
        } else if (action.equals("heist_start")) {
            if (!hasC4.getOrDefault(cdKey, false)) {
                event.reply(LanguageManager.t(settings, "heist_no_c4")).setEphemeral(true).queue();
                return;
            }

            hasC4.put(cdKey, false);
            heistCooldowns.put(cdKey, System.currentTimeMillis() + 86400000L);

            if (random.nextInt(100) < 35) {
                int lup = random.nextInt(8000) + 5000;
                db.addCoins(guildId, userId, lup, "gotowka");

                EmbedBuilder eb = new EmbedBuilder().setColor(Color.decode("#F1C40F"))
                        .setAuthor(LanguageManager.t(settings, "heist_win_author"), null, event.getUser().getEffectiveAvatarUrl())
                        .setDescription(LanguageManager.t(settings, "heist_win_desc", userId, lup, settings.currencyEmoji));

                event.getChannel().sendMessageEmbeds(eb.build()).queue();
                event.editMessageEmbeds(new EmbedBuilder().setColor(Color.GRAY)
                        .setDescription(LanguageManager.t(settings, "heist_done")).build()).setComponents().queue();
            } else {
                int kara = 2500;
                boolean paidFine = db.removeBankCoins(guildId, userId, kara) || db.removeCoins(guildId, userId, kara, "gotowka");
                String fineDisplay = paidFine ? String.valueOf(kara) : LanguageManager.t(settings, "heist_fine_confiscated");

                EmbedBuilder eb = new EmbedBuilder().setColor(Color.RED)
                        .setAuthor(LanguageManager.t(settings, "heist_fail_author"), null, event.getUser().getEffectiveAvatarUrl())
                        .setDescription(LanguageManager.t(settings, "heist_fail_desc", userId, fineDisplay, settings.currencyEmoji));

                event.getChannel().sendMessageEmbeds(eb.build()).queue();
                event.editMessageEmbeds(new EmbedBuilder().setColor(Color.GRAY)
                        .setDescription(LanguageManager.t(settings, "heist_arrested")).build()).setComponents().queue();
            }
        }
    }
}
