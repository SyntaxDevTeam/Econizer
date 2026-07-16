package pl.syntaxdevteam.listeners;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.util.Map;

public class MarketManager extends ListenerAdapter {
    private final DatabaseManager db;

    public MarketManager(DatabaseManager db) { this.db = db; }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (!event.getName().equals("market")) return;
        String guildId = event.getGuild().getId();
        String userId = event.getUser().getId();
        GuildSettings settings = db.getGuildSettings(guildId);

        if (!settings.economyEnabled) {
            event.reply(LanguageManager.t(settings, "eco_disabled")).setEphemeral(true).queue();
            return;
        }

        String op = event.getOption("operation").getAsString();
        OptionMapping aOpt = event.getOption("symbol");
        OptionMapping iOpt = event.getOption("quantity");
        Map<String, Integer> stocks = db.getAndRefreshStocks(guildId);

        if (op.equals("check") || op.equals("sprawdz")) {
            EmbedBuilder eb = new EmbedBuilder().setColor(Color.decode("#2980B9"))
                    .setAuthor(LanguageManager.t(settings, "market_title"), null, event.getUser().getEffectiveAvatarUrl());
            if (stocks.isEmpty()) {
                eb.addField(LanguageManager.t(settings, "market_closed"), LanguageManager.t(settings, "market_closed_desc"), false);
            } else {
                for (Map.Entry<String, Integer> entry : stocks.entrySet()) {
                    eb.addField(LanguageManager.t(settings, "market_field", entry.getKey()),
                            LanguageManager.t(settings, "market_field_value", entry.getValue(), settings.currencyEmoji,
                                    db.getUserStockAmount(guildId, userId, entry.getKey())), false);
                }
            }
            event.replyEmbeds(eb.build()).queue();
            return;
        }

        if (aOpt == null || iOpt == null || iOpt.getAsInt() <= 0) {
            event.reply(LanguageManager.t(settings, "market_err_args")).setEphemeral(true).queue();
            return;
        }
        String ticker = aOpt.getAsString().toUpperCase();
        int ilosc = iOpt.getAsInt();
        if (!stocks.containsKey(ticker)) {
            event.reply(LanguageManager.t(settings, "market_err_ticker")).setEphemeral(true).queue();
            return;
        }

        int totalCost = stocks.get(ticker) * ilosc;
        if (op.equals("buy") || op.equals("kup")) {
            event.reply(LanguageManager.t(settings, "market_buy_prompt", ilosc, ticker, totalCost, settings.currencyEmoji))
                    .addComponents(ActionRow.of(
                            Button.success("st_b_cash:" + ticker + ":" + ilosc + ":" + totalCost, LanguageManager.t(settings, "btn_wallet")),
                            Button.primary("st_b_card:" + ticker + ":" + ilosc + ":" + totalCost, LanguageManager.t(settings, "btn_bank"))
                    )).setEphemeral(true).queue();
        } else {
            event.reply(LanguageManager.t(settings, "market_sell_prompt", ilosc, ticker, totalCost, settings.currencyEmoji))
                    .addComponents(ActionRow.of(
                            Button.success("st_s_cash:" + ticker + ":" + ilosc + ":" + totalCost, LanguageManager.t(settings, "btn_wallet")),
                            Button.primary("st_s_card:" + ticker + ":" + ilosc + ":" + totalCost, LanguageManager.t(settings, "btn_bank"))
                    )).setEphemeral(true).queue();
        }
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        String[] parts = event.getComponentId().split(":");
        String action = parts[0];
        if (!action.startsWith("st_")) return;
        String guildId = event.getGuild().getId();
        String userId = event.getUser().getId();
        GuildSettings settings = db.getGuildSettings(guildId);
        String ticker = parts[1];
        int ilosc = Integer.parseInt(parts[2]);
        int kwota = Integer.parseInt(parts[3]);

        if (action.equals("st_b_cash")) {
            if (db.removeCoins(guildId, userId, kwota, "gotowka")) {
                db.updateUserStock(guildId, userId, ticker, ilosc, kwota / ilosc);
                event.editMessage(LanguageManager.t(settings, "market_buy_cash_ok")).setComponents().queue();
            } else {
                event.reply(LanguageManager.t(settings, "market_no_cash")).setEphemeral(true).queue();
            }
        } else if (action.equals("st_b_card")) {
            if (db.removeBankCoins(guildId, userId, kwota)) {
                db.updateUserStock(guildId, userId, ticker, ilosc, kwota / ilosc);
                event.editMessage(LanguageManager.t(settings, "market_buy_card_ok")).setComponents().queue();
            } else {
                event.reply(LanguageManager.t(settings, "market_no_bank")).setEphemeral(true).queue();
            }
        } else {
            int currentStocks = db.getUserStockAmount(guildId, userId, ticker);
            if (currentStocks < ilosc) {
                event.reply(LanguageManager.t(settings, "market_sell_fail")).setEphemeral(true).queue();
                return;
            }
            db.updateUserStock(guildId, userId, ticker, -ilosc, kwota / ilosc);
            if (action.equals("st_s_card")) db.addBankCoins(guildId, userId, kwota);
            else db.addCoins(guildId, userId, kwota, "gotowka");
            event.editMessage(LanguageManager.t(settings, "market_sell_ok")).setComponents().queue();
        }
    }
}