package pl.syntaxdevteam.listeners;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.util.Map;

public class MarketManager extends ListenerAdapter {
    private final DatabaseManager db;
    public MarketManager(DatabaseManager db) { this.db = db; }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (!event.getName().equals("gielda")) return;
        String guildId = event.getGuild().getId(); String userId = event.getUser().getId(); GuildSettings settings = db.getGuildSettings(guildId);
        if (!settings.economyEnabled) return;

        String op = event.getOption("operacja").getAsString(); OptionMapping aOpt = event.getOption("akcja"); OptionMapping iOpt = event.getOption("ilosc");
        Map<String, Integer> stocks = db.getAndRefreshStocks(guildId);

        if (op.equals("sprawdz")) {
            EmbedBuilder eb = new EmbedBuilder().setColor(Color.decode("#2980B9")).setAuthor("Wirtualny Rynek Papierów Wartościowych", null, event.getUser().getEffectiveAvatarUrl());
            if (stocks.isEmpty()) { eb.addField("Rynek jest zamknięty", "Brak aktywnych spółek.", false); }
            else { for(Map.Entry<String, Integer> entry : stocks.entrySet()) { eb.addField("📈 " + entry.getKey(), "Kurs akcji: **" + entry.getValue() + "** " + settings.currencyEmoji + "\nTwój portfel: `" + db.getUserStockAmount(guildId, userId, entry.getKey()) + "` szt.", false); } }
            event.replyEmbeds(eb.build()).queue(); return;
        }

        if (aOpt == null || iOpt == null || iOpt.getAsInt() <= 0) { event.reply("❌ Błędne parametry.").setEphemeral(true).queue(); return; }
        String ticker = aOpt.getAsString().toUpperCase(); int ilosc = iOpt.getAsInt();
        if (!stocks.containsKey(ticker)) { event.reply("❌ Taka spółka nie istnieje!").setEphemeral(true).queue(); return; }

        int totalCost = stocks.get(ticker) * ilosc;
        if (op.equals("kup")) {
            event.reply("💳 Koszt zakupu **" + ilosc + "x " + ticker + "** wynosi **" + totalCost + " " + settings.currencyEmoji + "**. Wybierz formę płatności:")
                    .addActionRow(Button.success("st_b_cash:" + ticker + ":" + ilosc + ":" + totalCost, "💵 Gotówką"), Button.primary("st_b_card:" + ticker + ":" + ilosc + ":" + totalCost, "💳 Kartą (z Banku)")).setEphemeral(true).queue();
        } else {
            event.reply("📈 Chcesz upłynnić **" + ilosc + "x " + ticker + "** za kwotę **" + totalCost + " " + settings.currencyEmoji + "**. Wybierz konto docelowe:")
                    .addActionRow(Button.success("st_s_cash:" + ticker + ":" + ilosc + ":" + totalCost, "💵 Portfel"), Button.primary("st_s_card:" + ticker + ":" + ilosc + ":" + totalCost, "🏦 Konto Bankowe")).setEphemeral(true).queue();
        }
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        String[] parts = event.getComponentId().split(":"); String action = parts[0]; if (!action.startsWith("st_")) return;
        String guildId = event.getGuild().getId(); String userId = event.getUser().getId(); GuildSettings s = db.getGuildSettings(guildId);
        String ticker = parts[1]; int ilosc = Integer.parseInt(parts[2]); int kwota = Integer.parseInt(parts[3]);

        if (action.equals("st_b_cash")) {
            if (db.removeCoins(guildId, userId, kwota, "gotowka")) { db.updateUserStock(guildId, userId, ticker, ilosc, kwota / ilosc); event.editMessage("✅ Akcje zakupione gotówką!").setComponents().queue();
            } else { event.reply("❌ Brak gotówki w portfelu!").setEphemeral(true).queue(); }
        } else if (action.equals("st_b_card")) {
            if (db.removeBankCoins(guildId, userId, kwota)) { db.updateUserStock(guildId, userId, ticker, ilosc, kwota / ilosc); event.editMessage("✅ Transakcja bezgotówkowa udana! Środki pobrane z konta bankowego.").setComponents().queue();
            } else { event.reply("❌ Brak wolnych środków w banku!").setEphemeral(true).queue(); }
        } else {
            int currentStocks = db.getUserStockAmount(guildId, userId, ticker);
            if (currentStocks < ilosc) { event.reply("❌ Nie posiadasz tylu akcji!").setEphemeral(true).queue(); return; }
            db.updateUserStock(guildId, userId, ticker, -ilosc, kwota / ilosc);
            if (action.equals("st_s_card")) db.addBankCoins(guildId, userId, kwota); else db.addCoins(guildId, userId, kwota, "gotowka");
            event.editMessage("✅ Pozycja giełdowa zamknięta. Środki zostały pomyślnie zaksięgowane.").setComponents().queue();
        }
    }
}