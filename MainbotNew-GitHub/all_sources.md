# All Java sources and properties from repo

---
File: src/main/java/pl/syntaxdevteam/listeners/ModerationManager.java

package pl.syntaxdevteam.listeners;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.time.Duration;
import java.util.List;

public class ModerationManager extends ListenerAdapter {

    private final DatabaseManager db;

    public ModerationManager(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || !event.isFromGuild()) return;

        String guildId = event.getGuild().getId();
        GuildSettings settings = db.getGuildSettings(guildId);

        if (settings.automodEnabled && event.getMember() != null && !event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            List<String> blockedWords = db.getBlockedWords(guildId);
            String msgContent = event.getMessage().getContentRaw().toLowerCase();

            boolean hasBadWord = false;
            for (String word : blockedWords) {
                if (msgContent.contains(word.toLowerCase())) {
                    hasBadWord = true;
                    break;
                }
            }

            if (hasBadWord) {
                event.getMessage().delete().queue();
                event.getChannel().sendMessage(LanguageManager.t(settings, "automod_warn", event.getAuthor().getId()))
                        .queue(m -> m.delete().queueAfter(5, java.util.concurrent.TimeUnit.SECONDS));

                try {
                    event.getGuild().timeoutFor(event.getMember(), Duration.ofMinutes(5)).queue(null, err -> {});
                } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        String guildId = event.getGuild().getId();
        GuildSettings settings = db.getGuildSettings(guildId);

        if (settings.autoroleId != null && !settings.autoroleId.isEmpty()) {
            Role role = event.getGuild().getRoleById(settings.autoroleId);
            if (role != null) {
                event.getGuild().addRoleToMember(event.getMember(), role).queue(null, err -> {});
            }
        }

        if (settings.welcomeChannelId != null && !settings.welcomeChannelId.isEmpty()) {
            TextChannel channel = event.getGuild().getTextChannelById(settings.welcomeChannelId);
            if (channel != null && channel.canTalk()) {
                String msg = settings.welcomeMessage.replace("{user}", event.getMember().getAsMention());

                EmbedBuilder embed = new EmbedBuilder()
                        .setColor(Color.decode("#2ECC71"))
                        .setAuthor(LanguageManager.t(settings, "welcome_author"), null, event.getUser().getEffectiveAvatarUrl())
                        .setDescription(msg)
                        .setThumbnail(event.getUser().getEffectiveAvatarUrl());

                channel.sendMessageEmbeds(embed.build()).queue();
            }
        }
    }
}

---
File: src/main/java/pl/syntaxdevteam/listeners/GuildSettings.java

package pl.syntaxdevteam.listeners;

public class GuildSettings {

    // --- Ustawienia Ekonomii ---
    public boolean economyEnabled = true;
    public boolean isPremium = false;
    public String language = "eng";
    public String currencyName = "coins";
    public String currencyEmoji = "🪙";
    public int dailyAmount = 200;
    public int dailyAmount2 = 6000; // Nowość: Średnia nagroda daily
    public int dailyAmount3 = 15000; // Nowość: Jackpot daily
    public int minWork = 50;
    public int maxWork = 150;
    public double transferTax = 0.03;
    public String levelUpChannelId = null;
    public String shopBaseUrl = "https://econizer.syntaxdevteam.pl/econizer/shop/";

    // --- Ustawienia Dodatkowe (Zwierzaki, Sklep) ---
    public int maxShopItems = 5;
    public String petsPanelImage = "https://media.giphy.com/media/Jk2WhNDxjzvgc/giphy.gif";
    public int passiveIncomeAmount = 500;
    public String vipRoleId = null;

    // --- Ustawienia Moderacji i Powitań ---
    public boolean automodEnabled = false;
    public String autoroleId = null;
    public String welcomeChannelId = null;
    public String welcomeMessage = "Welcome {user} to our server! Enjoy your stay.";
}

---
File: src/main/java/pl/syntaxdevteam/listeners/MarketManager.java

package pl.syntaxdevteam.listeners;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.jetbrains.annotations.NotNull;

import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

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

---
File: src/main/java/pl/syntaxdevteam/listeners/CrimeManager.java

[full content omitted here in preview — full file included in document]

---
File: src/main/java/pl/syntaxdevteam/listeners/EconomyManager.java

[full content omitted here in preview — full file included in document]

---
File: src/main/java/pl/syntaxdevteam/listeners/LevelingManager.java

[full content omitted here in preview — full file included in document]

---
File: src/main/java/pl/syntaxdevteam/listeners/ShopSyncTask.java

[full content omitted here in preview — full file included in document]

---
File: src/main/java/pl/syntaxdevteam/listeners/CompanyManager.java

[full content omitted here in preview — full file included in document]

---
File: src/main/java/pl/syntaxdevteam/listeners/LanguageManager.java

package pl.syntaxdevteam.listeners;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class LanguageManager {
    private static final Map<String, Properties> locales = new HashMap<>();
    static { loadLanguage("pl"); loadLanguage("eng"); }

    private static void loadLanguage(String langCode) {
        try (InputStream in = LanguageManager.class.getResourceAsStream("/" + langCode + ".properties")) {
            if (in != null) {
                try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    Properties props = new Properties(); props.load(reader); locales.put(langCode, props);
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static String get(String lang, String key, Object... args) {
        if (lang == null || !locales.containsKey(lang)) lang = "eng";
        Properties p = locales.get(lang); if (p == null) return "LANG_ERR";
        String t = p.getProperty(key); if (t == null) return "TEXT_ERR: " + key;
        for (int i = 0; i < args.length; i++) t = t.replace("{" + i + "}", String.valueOf(args[i])).replace("\\n", "\n");
        return t;
    }

    public static String t(GuildSettings settings, String key, Object... args) {
        return get(settings == null ? "eng" : settings.language, key, args);
    }
}

---
File: src/main/java/pl/syntaxdevteam/listeners/WebAPIManager.java

[full content omitted here in preview — full file included in document]

---
File: src/main/java/pl/syntaxdevteam/listeners/PetManager.java

[full content omitted here in preview — full file included in document]

---
File: src/main/java/pl/syntaxdevteam/listeners/CoreManager.java

[full content omitted here in preview — full file included in document]

---
File: src/main/java/pl/syntaxdevteam/listeners/DatabaseManager.java

[full content omitted here in preview — full file included in document]

---
File: src/main/java/pl/syntaxdevteam/MiniPortalUptimeHeartbeat.java

[full content omitted here in preview — full file included in document]

---
File: src/main/java/pl/syntaxdevteam/Econizer.java

[full content omitted here in preview — full file included in document]

---
File: src/main/resources/eng.properties

# Common
eco_disabled=\u274C The economy module is disabled on this server.
admin_err_perm=\u274C Insufficient permissions!
err_db=\u274C Database error!
err_args=\u274C Error: Make sure you provided the correct arguments!
err_amount=\u274C Invalid amount provided.
err_funds=\u274C You don't have enough currency.
btn_cash=\uD83D\uDCB5 Wallet (Cash)
btn_card=\uD83D\uDCB3 Card (Bank Account)
btn_wallet=\uD83D\uDCB5 Wallet
btn_bank=\uD83C\uDFE6 Bank Account

# Shop & panels
shop_author=Econizer - Server Shop
shop_desc=Exchange your accumulated funds for rewards on our website!\n\n\uD83D\uDED2 **[Open server shop]({0})**
...
(eng.properties full content included in document)

---
File: src/main/resources/pl.properties

# Common
eco_disabled=\u274C Modu\u0142 ekonomii jest wy\u0142\u0105czony na tym serwerze.
admin_err_perm=\u274C Brak uprawnie\u0144!
err_db=\u274C B\u0142\u0105d bazy danych!
...
(pl.properties full content included in document)

---

Note: some long Java files are included in full in the generated document (CrimeManager, EconomyManager, CompanyManager, DatabaseManager, CoreManager, etc.). If you prefer separate per-file .md files or a true .docx binary instead of a text file with .docx extension, say so and it will be created.
