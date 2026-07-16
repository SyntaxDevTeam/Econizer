package pl.syntaxdevteam.listeners;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class PetManager extends ListenerAdapter {
    private final DatabaseManager db;

    public PetManager(DatabaseManager db) { this.db = db; }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        String userId = event.getUser().getId();
        GuildSettings settings = db.getGuildSettings(guildId);

        // GŁÓWNY PANEL ZWIERZAKA
        if (!event.getName().equals("pets")) return;

        if (!settings.economyEnabled || !db.isPetsEnabled(guildId)) {
            event.reply(LanguageManager.t(settings, "pet_disabled")).setEphemeral(true).queue();
            return;
        }

        EmbedBuilder eb = new EmbedBuilder().setColor(Color.decode("#9B59B6"))
                .setAuthor(LanguageManager.t(settings, "pet_title"), null, event.getUser().getEffectiveAvatarUrl())
                .setDescription(LanguageManager.t(settings, "pet_desc"));

        int myPetId = db.getUserPetId(guildId, userId);
        List<Button> buttons = new ArrayList<>();

        if (myPetId > 0) {
            // Ma zwierzaka - pokazujemy nakarm, status, wypuść
            buttons.add(Button.success("pet_feed_prompt:" + userId, LanguageManager.t(settings, "pet_btn_feed")));
            buttons.add(Button.secondary("pet_status:" + userId, LanguageManager.t(settings, "pet_btn_status")));
            buttons.add(Button.danger("pet_release:" + userId, "👋 Wypuść zwierzaka"));
        } else {
            // Nie ma zwierzaka - pokazujemy tylko przycisk kupna
            buttons.add(Button.primary("pet_buy:" + userId, LanguageManager.t(settings, "pet_btn_buy")));
        }

        event.replyEmbeds(eb.build()).addComponents(ActionRow.of(buttons)).setEphemeral(true).queue();
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
            event.reply(LanguageManager.t(settings, "pet_err_not_yours")).setEphemeral(true).queue();
            return;
        }

        switch (action) {
            case "pet_release":
                db.removeUserPet(guildId, userId);
                event.editMessageEmbeds(new EmbedBuilder().setColor(Color.RED)
                                .setDescription("👋 Wypuściłeś swojego zwierzaka na wolność! Możesz teraz adoptować nowego.").build())
                        .setComponents().queue();
                break;

            case "pet_buy":
                if (db.getUserPetId(guildId, userId) > 0) {
                    event.reply(LanguageManager.t(settings, "pet_err_has_pet")).setEphemeral(true).queue();
                    return;
                }
                EmbedBuilder shopEmbed = new EmbedBuilder().setColor(Color.decode("#E67E22"))
                        .setAuthor(LanguageManager.t(settings, "pet_shop_title"), null, event.getUser().getEffectiveAvatarUrl());

                List<Button> buttons = new ArrayList<>();
                for (Object[] pet : db.getAllPets(guildId, settings.isPremium)) {
                    shopEmbed.addField("ID: " + pet[0] + " | " + pet[1],
                            LanguageManager.t(settings, "pet_shop_field", pet[4], settings.currencyEmoji, pet[2], pet[3]), false);
                    buttons.add(Button.primary("pet_buy_select:" + pet[0] + ":" + userId,
                            LanguageManager.t(settings, "pet_btn_select", pet[1])));
                }

                if (buttons.isEmpty()) {
                    shopEmbed.setDescription("Brak zwierzaków w sklepie.");
                    event.editMessageEmbeds(shopEmbed.build()).setComponents().queue();
                } else {
                    event.editMessageEmbeds(shopEmbed.build()).setComponents(ActionRow.of(buttons)).queue();
                }
                break;

            case "pet_buy_select":
                int selectId = Integer.parseInt(parts[1]);
                Object[] pConf = db.getPetConfig(guildId, selectId, settings.isPremium);
                if (pConf == null) {
                    event.reply("Ten zwierzak nie istnieje!").setEphemeral(true).queue();
                    return;
                }
                int cost = (int) pConf[4];
                event.editMessageEmbeds(new EmbedBuilder().setColor(Color.orange)
                                .setDescription(LanguageManager.t(settings, "pet_pay_prompt", pConf[0], cost, settings.currencyEmoji)).build())
                        .setComponents(ActionRow.of(
                                Button.success("buy_pay_cash:" + selectId + ":" + cost + ":" + userId, LanguageManager.t(settings, "btn_wallet")),
                                Button.primary("buy_pay_card:" + selectId + ":" + cost + ":" + userId, LanguageManager.t(settings, "btn_card"))
                        )).queue();
                break;

            case "buy_pay_cash":
            case "buy_pay_card":
                int buyId = Integer.parseInt(parts[1]);
                int price = Integer.parseInt(parts[2]);
                boolean bCard = action.equals("buy_pay_card");
                boolean bSuccess = bCard ? db.removeBankCoins(guildId, userId, price) : db.removeCoins(guildId, userId, price, "gotowka");
                if (bSuccess) {
                    db.assignPet(guildId, userId, buyId);
                    Object[] finalPet = db.getPetConfig(guildId, buyId, settings.isPremium);
                    event.editMessageEmbeds(new EmbedBuilder().setColor(Color.GREEN)
                            .setDescription(LanguageManager.t(settings, "pet_buy_ok", finalPet[0])).build()).setComponents().queue();
                } else {
                    event.reply(LanguageManager.t(settings, "pet_feed_fail")).setEphemeral(true).queue();
                }
                break;

            case "pet_feed_prompt":
                if (db.getUserPetId(guildId, userId) == 0) {
                    event.reply(LanguageManager.t(settings, "pet_err_no_pet")).setEphemeral(true).queue();
                    return;
                }
                event.editMessageEmbeds(new EmbedBuilder().setColor(Color.decode("#2ECC71"))
                                .setDescription(LanguageManager.t(settings, "pet_feed_prompt", settings.currencyEmoji)).build())
                        .setComponents(ActionRow.of(
                                Button.success("feed_pay_cash:150:" + userId, LanguageManager.t(settings, "btn_wallet")),
                                Button.primary("feed_pay_card:150:" + userId, LanguageManager.t(settings, "btn_card"))
                        )).queue();
                break;

            case "feed_pay_cash":
            case "feed_pay_card":
                boolean fCard = action.equals("feed_pay_card");
                boolean fSuccess = fCard ? db.removeBankCoins(guildId, userId, 150) : db.removeCoins(guildId, userId, 150, "gotowka");
                if (fSuccess) {
                    db.updatePetFed(guildId, userId, System.currentTimeMillis());
                    event.editMessageEmbeds(new EmbedBuilder().setColor(Color.GREEN)
                            .setDescription(LanguageManager.t(settings, "pet_feed_ok")).build()).setComponents().queue();
                } else {
                    event.reply(LanguageManager.t(settings, "pet_feed_fail")).setEphemeral(true).queue();
                }
                break;

            case "pet_status":
                int myPet = db.getUserPetId(guildId, userId);
                if (myPet == 0) {
                    event.reply(LanguageManager.t(settings, "pet_err_no_pet")).setEphemeral(true).queue();
                    return;
                }
                Object[] conf = db.getPetConfig(guildId, myPet, settings.isPremium);
                long left = 86400000 - (System.currentTimeMillis() - db.getPetLastFed(guildId, userId));
                EmbedBuilder st = new EmbedBuilder().setAuthor(LanguageManager.t(settings, "pet_status_author", conf[0]), null, event.getUser().getEffectiveAvatarUrl());
                if (left > 0) {
                    st.setColor(Color.GREEN).setDescription(LanguageManager.t(settings, "pet_status_happy", conf[0], conf[2], conf[3], left / 3600000));
                } else {
                    st.setColor(Color.RED).setDescription(LanguageManager.t(settings, "pet_status_sad"));
                }
                event.editMessageEmbeds(st.build()).setComponents().queue();
                break;
        }
    }
}