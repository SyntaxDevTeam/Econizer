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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class CrimeManager extends ListenerAdapter {
    private final DatabaseManager db;
    private final Random random = new Random();

    private final Map<String, Long> heistCooldowns = new ConcurrentHashMap<>();
    private final Map<String, Boolean> hasC4 = new ConcurrentHashMap<>();
    private final Map<String, HeistLobby> lobbies = new ConcurrentHashMap<>();

    private final int C4_COST = 2500;

    static class HeistLobby {
        String guildId;
        String leaderId;
        int required;
        Set<String> players = ConcurrentHashMap.newKeySet();

        public HeistLobby(String guildId, String leaderId, int required) {
            this.guildId = guildId;
            this.leaderId = leaderId;
            this.required = required;
            this.players.add(leaderId);
        }
    }

    public CrimeManager(DatabaseManager db) {
        this.db = db;
    }

    private String getMentions(Set<String> players) {
        StringBuilder sb = new StringBuilder();
        for (String p : players) sb.append("<@").append(p).append("> ");
        return sb.toString();
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

        if (cmd.equals("heist")) {
            int ekipa = event.getOption("team") != null ? event.getOption("team").getAsInt() : 2;

            if (ekipa < 2 || ekipa > 5) {
                event.reply("Ekipa na napad musi liczyć od 2 do 5 osób!").setEphemeral(true).queue();
                return;
            }

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
                    .setDescription(LanguageManager.t(settings, "heist_intro") + "\n\nPlanowana ekipa: **" + ekipa + " os.**\n" + gearLine);

            if (!posiadaSprzet) {
                event.replyEmbeds(eb.build()).addComponents(ActionRow.of(
                        Button.success("heist_buy_cash:" + ekipa, LanguageManager.t(settings, "heist_btn_buy_cash", C4_COST)),
                        Button.primary("heist_buy_card:" + ekipa, LanguageManager.t(settings, "heist_btn_buy_card", C4_COST))
                )).setEphemeral(true).queue();
            } else {
                event.replyEmbeds(eb.build()).addComponents(ActionRow.of(
                        Button.danger("heist_start:" + ekipa, "🔥 Rozpocznij Rekrutację")
                )).setEphemeral(true).queue();
            }
            return;
        }

        if (cmd.equals("rob")) {
            long currentRobTime = System.currentTimeMillis();
            long robCooldownTime = db.getCooldown(guildId, userId, "robbery");

            if (currentRobTime < robCooldownTime) {
                long minutesLeft = ((robCooldownTime - currentRobTime) / 1000) / 60;
                event.reply(LanguageManager.t(settings, "rob_cooldown", userId, minutesLeft)).setEphemeral(true).queue();
                return;
            }

            User ofiara = event.getOption("victim").getAsUser();
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

                // NALICZANIE ZYSKU DLA FIRMY (KRADZIEŻ JEST W CENIE!)
                CompanyManager.addCompanyRevenue(db, guildId, userId, ukradzione);

                event.reply(LanguageManager.t(settings, "rob_success", userId, ukradzione, settings.currencyEmoji, ofiara.getId())).queue();
            } else {
                int kara = 250;
                if (!db.removeCoins(guildId, userId, kara, "gotowka")) db.removeBankCoins(guildId, userId, kara);
                event.reply(LanguageManager.t(settings, "rob_fail", userId, ofiara.getId(), kara, settings.currencyEmoji)).queue();
            }
            return;
        }

        if (cmd.equals("bounty")) {
            User poszukiwany = event.getOption("player").getAsUser();
            int kwota = event.getOption("amount").getAsInt();
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

        if (cmd.equals("hunt")) {
            long currentHuntTime = System.currentTimeMillis();
            long huntCooldownTime = db.getCooldown(guildId, userId, "hunt");

            if (currentHuntTime < huntCooldownTime) {
                long minutesLeft = ((huntCooldownTime - currentHuntTime) / 1000) / 60;
                event.reply(LanguageManager.t(settings, "hunt_cooldown", userId, minutesLeft)).setEphemeral(true).queue();
                return;
            }

            User poszukiwany = event.getOption("player").getAsUser();
            int bounty = db.getBounty(guildId, poszukiwany.getId());
            if (bounty <= 0) {
                event.reply(LanguageManager.t(settings, "hunt_no_bounty")).setEphemeral(true).queue();
                return;
            }

            db.setCooldown(guildId, userId, "hunt", currentHuntTime + 3600000);

            if (random.nextInt(100) < 30) {
                db.clearBounty(guildId, poszukiwany.getId());
                db.addCoins(guildId, userId, bounty, "gotowka");

                // NALICZANIE ZYSKU DLA FIRMY (ŁOWCA NAGRÓD)
                CompanyManager.addCompanyRevenue(db, guildId, userId, bounty);

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
            int ekipa = Integer.parseInt(parts[1]);
            boolean useCard = action.equals("heist_buy_card");
            boolean success = useCard ? db.removeBankCoins(guildId, userId, C4_COST) : db.removeCoins(guildId, userId, C4_COST, "gotowka");

            if (success) {
                hasC4.put(cdKey, true);
                String payMethod = useCard
                        ? LanguageManager.t(settings, "heist_dealer_pay_card")
                        : LanguageManager.t(settings, "heist_dealer_pay_cash");
                event.editMessageEmbeds(new EmbedBuilder().setColor(Color.GREEN)
                                .setDescription(LanguageManager.t(settings, "heist_dealer_ok", payMethod)).build())
                        .setComponents(ActionRow.of(Button.danger("heist_start:" + ekipa, "🔥 Rozpocznij Rekrutację"))).queue();
            } else {
                event.reply(LanguageManager.t(settings, "heist_buy_fail", C4_COST)).setEphemeral(true).queue();
            }
        }
        else if (action.equals("heist_start")) {
            int ekipa = Integer.parseInt(parts[1]);
            if (!hasC4.getOrDefault(cdKey, false)) {
                event.reply(LanguageManager.t(settings, "heist_no_c4")).setEphemeral(true).queue();
                return;
            }

            event.editMessageEmbeds(new EmbedBuilder().setColor(Color.GRAY)
                    .setDescription("📡 Ogłoszenie o rekrutacji wysłane na kanał!").build()).setComponents().queue();

            EmbedBuilder lobbyEb = new EmbedBuilder().setColor(Color.decode("#34495E"))
                    .setTitle("🔥 Trwa zbieranie ekipy na napad!")
                    .setDescription("Przywódca <@" + userId + "> kupił sprzęt i planuje skok na skarbiec!\n\nPotrzebujemy: **1/" + ekipa + "** osób.\nZgłoszeni: <@" + userId + ">");

            event.getChannel().sendMessageEmbeds(lobbyEb.build()).addComponents(ActionRow.of(
                    Button.success("heist_join", "👊 Dołącz do ekipy"),
                    Button.danger("heist_cancel", "✖️ Anuluj")
            )).queue(msg -> lobbies.put(msg.getId(), new HeistLobby(guildId, userId, ekipa)));
        }
        else if (action.equals("heist_cancel")) {
            HeistLobby lobby = lobbies.get(event.getMessageId());
            if (lobby != null && lobby.leaderId.equals(userId)) {
                lobbies.remove(event.getMessageId());
                event.editMessageEmbeds(new EmbedBuilder().setColor(Color.RED)
                        .setDescription("🛑 Napad został anulowany przez przywódcę. Sprzęt zostaje na później!").build()).setComponents().queue();
            } else {
                event.reply("Tylko przywódca może anulować ten napad!").setEphemeral(true).queue();
            }
        }
        else if (action.equals("heist_join")) {
            HeistLobby lobby = lobbies.get(event.getMessageId());
            if (lobby == null) {
                event.reply("Ten napad już się zakończył lub został anulowany!").setEphemeral(true).queue();
                return;
            }
            if (lobby.players.contains(userId)) {
                event.reply("Już jesteś w tej ekipie!").setEphemeral(true).queue();
                return;
            }

            if (heistCooldowns.containsKey(cdKey) && System.currentTimeMillis() < heistCooldowns.get(cdKey)) {
                long hoursLeft = ((heistCooldowns.get(cdKey) - System.currentTimeMillis()) / 1000) / 3600;
                event.reply("Niedawno brałeś udział w napadzie! Musisz odpocząć jeszcze " + hoursLeft + " godzin(y).").setEphemeral(true).queue();
                return;
            }

            lobby.players.add(userId);

            if (lobby.players.size() >= lobby.required) {
                lobbies.remove(event.getMessageId());
                String leaderKey = lobby.guildId + "-" + lobby.leaderId;
                hasC4.put(leaderKey, false);
                long nextCd = System.currentTimeMillis() + 86400000L;

                event.editMessageEmbeds(new EmbedBuilder().setColor(Color.ORANGE)
                        .setDescription("🔥 Ekipa zebrana! Rozpoczynamy włamanie do skarbca...").build()).setComponents().queue();

                int chance = 30 + (lobby.players.size() * 5);
                boolean win = random.nextInt(100) < chance;

                StringBuilder finalDesc = new StringBuilder();
                finalDesc.append("Ekipa: ").append(getMentions(lobby.players)).append("\n\n");

                EmbedBuilder resultEb = new EmbedBuilder();
                if (win) {
                    resultEb.setColor(Color.decode("#F1C40F")).setTitle("💰 Napad Zakończony Sukcesem!");
                    for (String pId : lobby.players) {
                        heistCooldowns.put(lobby.guildId + "-" + pId, nextCd);
                        int lup = random.nextInt(8000) + 5000;
                        db.addCoins(lobby.guildId, pId, lup, "gotowka");

                        // NALICZANIE ZYSKU DLA FIRMY (Napad)
                        CompanyManager.addCompanyRevenue(db, lobby.guildId, pId, lup);

                        finalDesc.append("✅ <@").append(pId).append("> zgarnia **").append(lup).append("** ").append(settings.currencyEmoji).append("\n");
                    }
                } else {
                    resultEb.setColor(Color.RED).setTitle("🚨 Zostaliście złapani!");
                    int kara = 2500;
                    for (String pId : lobby.players) {
                        heistCooldowns.put(lobby.guildId + "-" + pId, nextCd);
                        db.removeBankCoins(lobby.guildId, pId, kara);
                        db.removeCoins(lobby.guildId, pId, kara, "gotowka");
                        finalDesc.append("❌ <@").append(pId).append("> traci **").append(kara).append("** ").append(settings.currencyEmoji).append(" (Grzywna)\n");
                    }
                }

                resultEb.setDescription(finalDesc.toString());
                event.getChannel().sendMessageEmbeds(resultEb.build()).queue();

            } else {
                EmbedBuilder updateEb = new EmbedBuilder().setColor(Color.decode("#34495E"))
                        .setTitle("🔥 Trwa zbieranie ekipy na napad!")
                        .setDescription("Przywódca <@" + lobby.leaderId + "> kupił sprzęt i planuje skok na skarbiec!\n\nPotrzebujemy: **" + lobby.players.size() + "/" + lobby.required + "** osób.\nZgłoszeni: " + getMentions(lobby.players));
                event.editMessageEmbeds(updateEb.build()).queue();
            }
        }
    }
}