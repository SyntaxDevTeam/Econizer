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
        if (!event.getName().equals("pets")) return;
        String guildId = event.getGuild().getId(); String userId = event.getUser().getId(); GuildSettings settings = db.getGuildSettings(guildId);
        if (!settings.economyEnabled || !db.isPetsEnabled(guildId)) { event.reply("❌ System towarzyszy jest wyłączony.").setEphemeral(true).queue(); return; }

        EmbedBuilder eb = new EmbedBuilder().setColor(Color.decode("#9B59B6")).setAuthor("Centrum zarządzania towarzyszem", null, event.getUser().getEffectiveAvatarUrl())
                .setDescription("Twój zwierzak zapewnia stałe pasywne mnożniki do monet i XP z komend `/work` oraz `/daily`, pod warunkiem, że dbasz o jego regularne wyżywienie co 24 godziny.");

        event.replyEmbeds(eb.build()).addComponents(ActionRow.of(
                Button.primary("pet_buy:" + userId, "🛒 Zakup zwierzaka"),
                Button.success("pet_feed_prompt:" + userId, "🍖 Nakarm pupilka"),
                Button.secondary("pet_status:" + userId, "📊 Sprawdź status")
        )).setEphemeral(true).queue();
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        String[] parts = event.getComponentId().split(":"); String action = parts[0];
        if (!action.startsWith("pet_") && !action.startsWith("buy_pay_") && !action.startsWith("feed_pay_")) return;
        String guildId = event.getGuild().getId(); String userId = event.getUser().getId(); GuildSettings settings = db.getGuildSettings(guildId);

        if (parts.length > 1 && !action.startsWith("buy_pay_") && !action.startsWith("feed_pay_") && !parts[parts.length - 1].equals(userId)) {
            event.reply("❌ To nie jest Twoje menu!").setEphemeral(true).queue(); return;
        }

        switch (action) {
            case "pet_buy":
                if (db.getUserPetId(guildId, userId) > 0) { event.reply("❌ Posiadasz już aktywnego towarzysza!").setEphemeral(true).queue(); return; }
                EmbedBuilder shopEmbed = new EmbedBuilder().setColor(Color.decode("#E67E22")).setAuthor("Sklep zoologiczny Econizer", null, event.getUser().getEffectiveAvatarUrl());
                List<Button> buttons = new ArrayList<>();
                for (Object[] pet : db.getAllPets(guildId, settings.isPremium)) {
                    shopEmbed.addField(pet[0] + ". " + pet[1], "Cena: **" + pet[4] + "** " + settings.currencyEmoji + "\nMnożnik: Kasa x" + pet[2] + " | Exp x" + pet[3], false);
                    buttons.add(Button.primary("pet_buy_select:" + pet[0] + ":" + userId, "Wybierz " + pet[1]));
                }
                event.editMessageEmbeds(shopEmbed.build()).setComponents(ActionRow.of(buttons)).queue(); break;

            case "pet_buy_select":
                int selectId = Integer.parseInt(parts[1]); Object[] pConf = db.getPetConfig(guildId, selectId, settings.isPremium); int cost = (int) pConf[4];
                event.editMessageEmbeds(new EmbedBuilder().setColor(Color.orange).setDescription("Wybierz metodę płatności za towarzysza **" + pConf[0] + "** (Koszt: " + cost + " " + settings.currencyEmoji + "):").build())
                        .setComponents(ActionRow.of(Button.success("buy_pay_cash:" + selectId + ":" + cost + ":" + userId, "💵 Gotówką"), Button.primary("buy_pay_card:" + selectId + ":" + cost + ":" + userId, "💳 Kartą"))).queue(); break;

            case "buy_pay_cash":
            case "buy_pay_card":
                int buyId = Integer.parseInt(parts[1]); int price = Integer.parseInt(parts[2]); boolean bCard = action.equals("buy_pay_card");
                boolean bSuccess = bCard ? db.removeBankCoins(guildId, userId, price) : db.removeCoins(guildId, userId, price, "gotowka");
                if (bSuccess) {
                    db.assignPet(guildId, userId, buyId); Object[] finalPet = db.getPetConfig(guildId, buyId, settings.isPremium);
                    event.editMessageEmbeds(new EmbedBuilder().setColor(Color.GREEN).setDescription("🎉 Pomyślnie zakupiono zwierzaka: **" + finalPet[0] + "**!").build()).setComponents().queue();
                } else { event.reply("❌ Brak środków na wybranym rachunku!").setEphemeral(true).queue(); } break;

            case "pet_feed_prompt":
                if (db.getUserPetId(guildId, userId) == 0) { event.reply("❌ Nie masz zwierzaka!").setEphemeral(true).queue(); return; }
                event.editMessageEmbeds(new EmbedBuilder().setColor(Color.decode("#2ECC71")).setDescription("Koszt karmy na kolejne 24h to **150 " + settings.currencyEmoji + "**. Wybierz metodę płatności:").build())
                        .setComponents(ActionRow.of(Button.success("feed_pay_cash:150:" + userId, "💵 Gotówką"), Button.primary("feed_pay_card:150:" + userId, "💳 Kartą"))).queue(); break;

            case "feed_pay_cash":
            case "feed_pay_card":
                boolean fCard = action.equals("feed_pay_card"); boolean fSuccess = fCard ? db.removeBankCoins(guildId, userId, 150) : db.removeCoins(guildId, userId, 150, "gotowka");
                if (fSuccess) { db.updatePetFed(guildId, userId, System.currentTimeMillis()); event.editMessageEmbeds(new EmbedBuilder().setColor(Color.GREEN).setDescription("🍖 Twój przyjaciel został nakarmiony!").build()).setComponents().queue();
                } else { event.reply("❌ Brak środków na koncie!").setEphemeral(true).queue(); } break;

            case "pet_status":
                int myPet = db.getUserPetId(guildId, userId); if (myPet == 0) { event.reply("❌ Nie posiadasz zwierzaka.").setEphemeral(true).queue(); return; }
                Object[] conf = db.getPetConfig(guildId, myPet, settings.isPremium); long left = 86400000 - (System.currentTimeMillis() - db.getPetLastFed(guildId, userId));
                EmbedBuilder st = new EmbedBuilder().setAuthor("Status towarzysza: " + conf[0], null, event.getUser().getEffectiveAvatarUrl());
                if (left > 0) st.setColor(Color.GREEN).setDescription("🐾 Pupil jest syty i zadowolony!\n🔥 **Bonus Monet:** x" + conf[2] + "\n✨ **Bonus EXP:** x" + conf[3] + "\n⏳ Kolejny posiłek wymagany za: `" + (left / 3600000) + "` godz.");
                else st.setColor(Color.RED).setDescription("❌ Pupil jest wygłodniały! Jego bonusy przestały działać. Nakarm go!");
                event.editMessageEmbeds(st.build()).setComponents().queue(); break;
        }
    }
}