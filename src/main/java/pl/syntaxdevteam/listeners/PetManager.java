package pl.syntaxdevteam.listeners;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class PetManager extends ListenerAdapter {
    private final DatabaseManager db;

    public PetManager(DatabaseManager db) { this.db = db; }

    private String getLang(GuildSettings settings, String key, Object... args) {
        String result = LanguageManager.t(settings, key, args);
        return (result != null && !result.startsWith("TEXT_ERR")) ? result : key;
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        String userId = event.getUser().getId();
        GuildSettings settings = db.getGuildSettings(guildId);

        if (!event.getName().equals("pets")) return;

        if (!settings.economyEnabled || !db.isPetsEnabled(guildId)) {
            event.reply(getLang(settings, "pet_disabled")).setEphemeral(true).queue();
            return;
        }

        EmbedBuilder eb = new EmbedBuilder().setColor(Color.decode("#9B59B6"))
                .setAuthor(getLang(settings, "pet_title"), null, event.getUser().getEffectiveAvatarUrl())
                .setDescription(getLang(settings, "pet_desc"))
                .setFooter("Econizer.gg");

        int myPetId = db.getUserPetId(guildId, userId);
        List<Button> buttons = new ArrayList<>();

        if (myPetId > 0) {
            buttons.add(Button.success("pet_feed_prompt:" + userId, getLang(settings, "pet_btn_feed")));
            buttons.add(Button.secondary("pet_status:" + userId, getLang(settings, "pet_btn_status")));
            buttons.add(Button.danger("pet_release:" + userId, getLang(settings, "pet_btn_release")));
        } else {
            buttons.add(Button.primary("pet_buy:" + userId, getLang(settings, "pet_btn_buy")));
        }

        // WEB PANEL READY: Sterowanie widocznością z poziomu panelu
        event.replyEmbeds(eb.build()).addComponents(ActionRow.of(buttons)).setEphemeral(settings.hideEconomyReplies).queue();
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        String[] parts = event.getComponentId().split(":");
        String action = parts[0];
        if (!action.startsWith("pet_") && !action.startsWith("buy_pay_") && !action.startsWith("feed_pay_")) return;

        String guildId = event.getGuild().getId();
        String userId = event.getUser().getId();
        GuildSettings settings = db.getGuildSettings(guildId);

        if (parts.length > 1 && !action.startsWith("buy_pay_") && !action.startsWith("feed_pay_") && !parts[parts.length - 1].equals(userId)) {
            event.reply(getLang(settings, "pet_err_not_yours")).setEphemeral(true).queue();
            return;
        }

        switch (action) {
            case "pet_release":
                db.removeUserPet(guildId, userId);
                event.editMessageEmbeds(new EmbedBuilder().setColor(Color.RED)
                        .setDescription(getLang(settings, "pet_release_ok"))
                        .setFooter("Econizer.gg").build()).setComponents().queue();
                break;

            case "pet_buy":
                if (db.getUserPetId(guildId, userId) > 0) {
                    event.reply(getLang(settings, "pet_err_has_pet")).setEphemeral(true).queue();
                    return;
                }

                EmbedBuilder shopEmbed = new EmbedBuilder().setColor(Color.decode("#E67E22"))
                        .setAuthor(getLang(settings, "pet_shop_title"), null, event.getUser().getEffectiveAvatarUrl())
                        .setFooter("Econizer.gg");

                List<Button> buttons = new ArrayList<>();
                for (Object[] pet : db.getAllPets(guildId, settings.isPremium)) {
                    shopEmbed.addField("ID: " + pet[0] + " | " + pet[1], getLang(settings, "pet_shop_field", pet[4], settings.currencyEmoji, pet[2], pet[3]), false);
                    buttons.add(Button.primary("pet_buy_select:" + pet[0] + ":" + userId, getLang(settings, "pet_btn_select", pet[1])));
                }

                if (buttons.isEmpty()) {
                    shopEmbed.setDescription(getLang(settings, "pet_shop_empty"));
                    event.editMessageEmbeds(shopEmbed.build()).setComponents().queue();
                } else {
                    event.editMessageEmbeds(shopEmbed.build()).setComponents(ActionRow.of(buttons)).queue();
                }
                break;

            case "pet_buy_select":
                int selectId = Integer.parseInt(parts[1]);
                Object[] pConf = db.getPetConfig(guildId, selectId, settings.isPremium);

                if (pConf == null) {
                    event.reply(getLang(settings, "pet_err_not_exist")).setEphemeral(true).queue();
                    return;
                }

                event.editMessageEmbeds(new EmbedBuilder().setColor(Color.orange)
                                .setDescription(getLang(settings, "pet_pay_prompt", pConf[0], (int)pConf[4], settings.currencyEmoji))
                                .setFooter("Econizer.gg").build())
                        .setComponents(ActionRow.of(
                                Button.success("buy_pay_cash:" + selectId + ":" + (int)pConf[4] + ":" + userId, getLang(settings, "btn_wallet")),
                                Button.primary("buy_pay_card:" + selectId + ":" + (int)pConf[4] + ":" + userId, getLang(settings, "btn_card"))
                        )).queue();
                break;

            case "buy_pay_cash":
            case "buy_pay_card":
                int buyId = Integer.parseInt(parts[1]);
                int price = Integer.parseInt(parts[2]);

                boolean buySuccess = action.equals("buy_pay_card")
                        ? db.removeBankCoins(guildId, userId, price)
                        : db.removeCoins(guildId, userId, price, "gotowka");

                if (buySuccess) {
                    db.assignPet(guildId, userId, buyId);
                    event.editMessageEmbeds(new EmbedBuilder().setColor(Color.GREEN)
                            .setDescription(getLang(settings, "pet_buy_ok", db.getPetConfig(guildId, buyId, settings.isPremium)[0]))
                            .setFooter("Econizer.gg").build()).setComponents().queue();
                } else {
                    event.reply(getLang(settings, "pet_feed_fail")).setEphemeral(true).queue();
                }
                break;

            case "pet_feed_prompt":
                if (db.getUserPetId(guildId, userId) == 0) {
                    event.reply(getLang(settings, "pet_err_no_pet")).setEphemeral(true).queue();
                    return;
                }

                event.editMessageEmbeds(new EmbedBuilder().setColor(Color.decode("#2ECC71"))
                                .setDescription(getLang(settings, "pet_feed_prompt", settings.currencyEmoji))
                                .setFooter("Econizer.gg").build())
                        .setComponents(ActionRow.of(
                                Button.success("feed_pay_cash:150:" + userId, getLang(settings, "btn_wallet")),
                                Button.primary("feed_pay_card:150:" + userId, getLang(settings, "btn_card"))
                        )).queue();
                break;

            case "feed_pay_cash":
            case "feed_pay_card":
                boolean feedSuccess = action.equals("feed_pay_card")
                        ? db.removeBankCoins(guildId, userId, 150)
                        : db.removeCoins(guildId, userId, 150, "gotowka");

                if (feedSuccess) {
                    db.updatePetFed(guildId, userId, System.currentTimeMillis());
                    event.editMessageEmbeds(new EmbedBuilder().setColor(Color.GREEN)
                            .setDescription(getLang(settings, "pet_feed_ok"))
                            .setFooter("Econizer.gg").build()).setComponents().queue();
                } else {
                    event.reply(getLang(settings, "pet_feed_fail")).setEphemeral(true).queue();
                }
                break;

            case "pet_status":
                int myPet = db.getUserPetId(guildId, userId);
                if (myPet == 0) {
                    event.reply(getLang(settings, "pet_err_no_pet")).setEphemeral(true).queue();
                    return;
                }

                Object[] conf = db.getPetConfig(guildId, myPet, settings.isPremium);
                long left = 86400000 - (System.currentTimeMillis() - db.getPetLastFed(guildId, userId));

                EmbedBuilder st = new net.dv8tion.jda.api.EmbedBuilder()
                        .setAuthor(getLang(settings, "pet_status_author", conf[0]), null, event.getUser().getEffectiveAvatarUrl())
                        .setFooter("Econizer.gg");

                if (left > 0) {
                    st.setColor(Color.GREEN).setDescription(getLang(settings, "pet_status_happy", conf[0], conf[2], conf[3], left / 3600000));
                } else {
                    st.setColor(Color.RED).setDescription(getLang(settings, "pet_status_sad"));
                }

                event.editMessageEmbeds(st.build()).setComponents().queue();
                break;
        }
    }
}