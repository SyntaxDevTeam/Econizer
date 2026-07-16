# Consolidated Econizer Code Documentation

This document contains all source code files from the Econizer project, organized by module.

---

## Econizer.java

```java
package pl.syntaxdevteam;

import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import pl.syntaxdevteam.listeners.*;

import java.util.concurrent.ScheduledExecutorService;

public class Econizer {
    public static void main(String[] args) {
        System.out.println("[System] Uruchamianie bota Econizer...");

        DatabaseManager db = new DatabaseManager();
        db.connect();

        String token = loadDiscordToken();
        if (token == null) {
            System.err.println("[System Błąd] Brak tokenu Discord!");
            System.err.println("Utwórz plik .env (na podstawie .env.example) i ustaw DISCORD_TOKEN");
            System.exit(1);
        }

        try {
            JDABuilder builder = JDABuilder.createDefault(token);

            builder.enableIntents(
                    GatewayIntent.GUILD_MESSAGES,
                    GatewayIntent.MESSAGE_CONTENT,
                    GatewayIntent.GUILD_MEMBERS
            );

            builder.addEventListeners(
                    new CoreManager(db),
                    new EconomyManager(db),
                    new CrimeManager(db),
                    new MarketManager(db),
                    new PetManager(db),
                    new CompanyManager(db),
                    new LevelingManager(db),
                    new ModerationManager(db)
            );

            builder.build().awaitReady();
            ScheduledExecutorService uptimeHeartbeat = MiniPortalUptimeHeartbeat.startFromConfig();
            if (uptimeHeartbeat != null) {
                Runtime.getRuntime().addShutdownHook(new Thread(uptimeHeartbeat::shutdown, "miniportal-uptime-shutdown"));
            }
            System.out.println("[System] Bot jest online i gotowy do działania!");

        } catch (InterruptedException e) {
            System.err.println("[System Błąd] Przerwano uruchamianie bota!");
            e.printStackTrace();
        }
    }

    private static String loadDiscordToken() {
        String token = System.getenv("DISCORD_TOKEN");
        if (token != null && !token.isBlank()) {
            return token.trim();
        }

        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        token = dotenv.get("DISCORD_TOKEN");
        if (token != null && !token.isBlank()) {
            return token.trim();
        }

        return null;
    }
}
```

---

## MiniPortalUptimeHeartbeat.java

```java
package pl.syntaxdevteam;

import io.github.cdimascio.dotenv.Dotenv;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class MiniPortalUptimeHeartbeat {

    private static final String DEFAULT_EVENT = "online";
    private static final long DEFAULT_INTERVAL_SECONDS = 60L;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final URI endpoint;
    private final String uuid;
    private final String event;

    private MiniPortalUptimeHeartbeat(String endpoint, String uuid, String event) {
        this.endpoint = URI.create(endpoint);
        this.uuid = uuid;
        this.event = event;
    }

    public static ScheduledExecutorService startFromConfig() {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

        String endpoint = readConfig(dotenv, "MINIPORTAL_UPTIME_ENDPOINT");
        String uuid = readConfig(dotenv, "MINIPORTAL_UPTIME_UUID");
        String event = readConfig(dotenv, "MINIPORTAL_UPTIME_EVENT", DEFAULT_EVENT);
        long intervalSeconds = readPositiveLongConfig(
                dotenv,
                "MINIPORTAL_UPTIME_INTERVAL_SECONDS",
                DEFAULT_INTERVAL_SECONDS
        );

        if (endpoint == null || uuid == null) {
            System.err.println("[Uptime] Brak MINIPORTAL_UPTIME_ENDPOINT lub MINIPORTAL_UPTIME_UUID - heartbeat wyłączony.");
            return null;
        }

        try {
            MiniPortalUptimeHeartbeat heartbeat = new MiniPortalUptimeHeartbeat(endpoint, uuid, event);
            return heartbeat.start(intervalSeconds);
        } catch (IllegalArgumentException exception) {
            System.err.println("[Uptime] Niepoprawny MINIPORTAL_UPTIME_ENDPOINT - heartbeat wyłączony.");
            return null;
        }
    }

    private ScheduledExecutorService start(long intervalSeconds) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "miniportal-uptime-heartbeat");
            thread.setDaemon(true);
            return thread;
        });

        scheduler.scheduleAtFixedRate(
                () -> send("Online"),
                0,
                intervalSeconds,
                TimeUnit.SECONDS
        );
        System.out.println("[Uptime] Heartbeat miniPORTAL uruchomiony co " + intervalSeconds + "s.");
        return scheduler;
    }

    private void send(String message) {
        String payload = "{\"uuid\":\"" + escapeJson(uuid)
                + "\",\"event\":\"" + escapeJson(event)
                + "\",\"message\":\"" + escapeJson(message) + "\"}";

        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json")
                    .header("X-Uptime-UUID", uuid)
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                System.err.println("[Uptime] Heartbeat miniPORTAL odrzucony. HTTP " + response.statusCode());
            }
        } catch (IOException exception) {
            System.err.println("[Uptime] Błąd połączenia heartbeat miniPORTAL: " + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (IllegalArgumentException exception) {
            System.err.println("[Uptime] Nie udało się przygotować żądania heartbeat: " + exception.getMessage());
        }
    }

    private static String readConfig(Dotenv dotenv, String key) {
        String value = System.getenv(key);
        if (value != null && !value.isBlank()) {
            return value.trim();
        }

        value = dotenv.get(key);
        if (value != null && !value.isBlank()) {
            return value.trim();
        }

        return null;
    }

    private static String readConfig(Dotenv dotenv, String key, String defaultValue) {
        String value = readConfig(dotenv, key);
        return value == null ? defaultValue : value;
    }

    private static long readPositiveLongConfig(Dotenv dotenv, String key, long defaultValue) {
        String value = readConfig(dotenv, key);
        if (value == null) {
            return defaultValue;
        }

        try {
            long parsed = Long.parseLong(value);
            if (parsed > 0) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
        }

        System.err.println("[Uptime] Niepoprawna wartość " + key + " - używam " + defaultValue + "s.");
        return defaultValue;
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
```

---

## CompanyManager.java

```java
package pl.syntaxdevteam.listeners;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.modals.Modal;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.sql.*;

public class CompanyManager extends ListenerAdapter {
    private final DatabaseManager db;
    private final int COMPANY_CREATION_COST = 25000;

    public CompanyManager(DatabaseManager db) {
        this.db = db;
    }

    public static void handleEmployeeWork(Connection conn, String guildId, String userId, int employeeEarnings) {
        if (conn == null) return;
        try (PreparedStatement pstmt = conn.prepareStatement("SELECT company_id FROM bot_employees WHERE guild_id = ? AND user_id = ?")) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, userId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                int companyId = rs.getInt("company_id");
                int bonus = (int) (employeeEarnings * 0.25);
                try (PreparedStatement u = conn.prepareStatement("UPDATE bot_companies SET vault = vault + ? WHERE id = ?")) {
                    u.setInt(1, bonus);
                    u.setInt(2, companyId);
                    u.executeUpdate();
                }
            }
        } catch (SQLException ignored) {}
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (!event.getName().equals("firma")) return;
        String guildId = event.getGuild().getId();
        String userId = event.getUser().getId();
        GuildSettings settings = db.getGuildSettings(guildId);

        if (!settings.economyEnabled) {
            event.reply(LanguageManager.t(settings, "eco_disabled")).setEphemeral(true).queue();
            return;
        }

        db.logCommandUsage("firma", guildId);
        Connection conn = db.getConnection();

        try {
            String sql = "SELECT c.name, c.owner_id, c.vault, c.split_owner, c.split_emp, c.id FROM bot_employees e JOIN bot_companies c ON e.company_id = c.id WHERE e.guild_id = ? AND e.user_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, guildId);
                pstmt.setString(2, userId);
                ResultSet rs = pstmt.executeQuery();

                if (rs.next()) {
                    String name = rs.getString("name");
                    String owner = rs.getString("owner_id");
                    int vault = rs.getInt("vault");
                    int sOwner = rs.getInt("split_owner");
                    int sEmp = rs.getInt("split_emp");
                    int cId = rs.getInt("id");
                    int emps = 0;

                    try (PreparedStatement c = conn.prepareStatement("SELECT COUNT(*) FROM bot_employees WHERE company_id = ?")) {
                        c.setInt(1, cId);
                        ResultSet crs = c.executeQuery();
                        if (crs.next()) emps = crs.getInt(1);
                    }

                    EmbedBuilder eb = new EmbedBuilder().setColor(Color.decode("#34495E"))
                            .setAuthor(LanguageManager.t(settings, "comp_employee_author", name), null, event.getUser().getEffectiveAvatarUrl())
                            .setDescription(LanguageManager.t(settings, "comp_employee_desc"))
                            .addField(LanguageManager.t(settings, "comp_field_owner"), "<@" + owner + ">", true)
                            .addField(LanguageManager.t(settings, "comp_field_staff"),
                                    LanguageManager.t(settings, "comp_field_staff_value", emps), true)
                            .addField(LanguageManager.t(settings, "comp_field_vault"), "**" + vault + "** " + settings.currencyEmoji, false)
                            .addField(LanguageManager.t(settings, "comp_field_splits"),
                                    LanguageManager.t(settings, "comp_field_splits_value", sOwner, sEmp), false);

                    if (owner.equals(userId)) {
                        event.replyEmbeds(eb.build()).addComponents(ActionRow.of(
                                Button.link("https://econizer.syntaxdevteam.pl/dashboard", LanguageManager.t(settings, "comp_btn_manage")),
                                Button.danger("comp_disband", LanguageManager.t(settings, "comp_btn_disband"))
                        )).queue();
                    } else {
                        event.replyEmbeds(eb.build()).addComponents(ActionRow.of(
                                Button.danger("comp_leave", LanguageManager.t(settings, "comp_btn_leave"))
                        )).queue();
                    }
                } else {
                    EmbedBuilder eb = new EmbedBuilder().setColor(Color.RED)
                            .setAuthor(LanguageManager.t(settings, "comp_unemployed_author"), null, event.getUser().getEffectiveAvatarUrl())
                            .setDescription(LanguageManager.t(settings, "comp_unemployed_desc", COMPANY_CREATION_COST, settings.currencyEmoji));

                    event.replyEmbeds(eb.build()).addComponents(ActionRow.of(
                            Button.success("comp_create_cash", LanguageManager.t(settings, "comp_btn_create_cash")),
                            Button.primary("comp_create_card", LanguageManager.t(settings, "comp_btn_create_card")),
                            Button.secondary("comp_top", LanguageManager.t(settings, "comp_btn_top"))
                    )).setEphemeral(true).queue();
                }
            }
        } catch (SQLException e) {
            event.reply(LanguageManager.t(settings, "err_db")).setEphemeral(true).queue();
        }
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        String[] parts = event.getComponentId().split(":");
        String action = parts[0];
        if (!action.startsWith("comp_")) return;

        GuildSettings settings = db.getGuildSettings(event.getGuild().getId());

        if (action.equals("comp_create_cash") || action.equals("comp_create_card")) {
            TextInput nameInput = TextInput.create("company_name", TextInputStyle.SHORT).setMinLength(3).setMaxLength(30).build();
            Modal modal = Modal.create("modal_" + action, LanguageManager.t(settings, "comp_modal_title"))
                    .addComponents(Label.of(LanguageManager.t(settings, "comp_modal_label"), nameInput))
                    .build();
            event.replyModal(modal).queue();
        } else if (action.equals("comp_top")) {
            Connection conn = db.getConnection();
            try (PreparedStatement pstmt = conn.prepareStatement("SELECT name, vault FROM bot_companies WHERE guild_id = ? ORDER BY vault DESC LIMIT 10")) {
                pstmt.setString(1, event.getGuild().getId());
                ResultSet rs = pstmt.executeQuery();
                EmbedBuilder eb = new EmbedBuilder().setColor(Color.decode("#F1C40F"))
                        .setTitle(LanguageManager.t(settings, "comp_top_title"));
                int i = 1;
                while (rs.next()) {
                    eb.addField(i + ". " + rs.getString("name"),
                            LanguageManager.t(settings, "comp_top_field", rs.getInt("vault"), settings.currencyEmoji), false);
                    i++;
                }
                if (i == 1) eb.setDescription(LanguageManager.t(settings, "comp_top_empty"));
                event.replyEmbeds(eb.build()).setEphemeral(true).queue();
            } catch (SQLException e) {
                event.reply(LanguageManager.t(settings, "err_db")).setEphemeral(true).queue();
            }
        }
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        if (event.getModalId().startsWith("modal_comp_create_")) {
            String guildId = event.getGuild().getId();
            String userId = event.getUser().getId();
            GuildSettings settings = db.getGuildSettings(guildId);
            String compName = event.getValue("company_name").getAsString();

            boolean useCard = event.getModalId().equals("modal_comp_create_card");
            boolean success = useCard
                    ? db.removeBankCoins(guildId, userId, COMPANY_CREATION_COST)
                    : db.removeCoins(guildId, userId, COMPANY_CREATION_COST, "gotowka");

            if (success) {
                Connection conn = db.getConnection();
                try {
                    try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO bot_companies (guild_id, name, owner_id) VALUES (?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                        stmt.setString(1, guildId);
                        stmt.setString(2, compName);
                        stmt.setString(3, userId);
                        stmt.executeUpdate();
                        ResultSet rs = stmt.getGeneratedKeys();
                        if (rs.next()) {
                            try (PreparedStatement empStmt = conn.prepareStatement("INSERT INTO bot_employees (guild_id, user_id, company_id) VALUES (?, ?, ?)")) {
                                empStmt.setString(1, guildId);
                                empStmt.setString(2, userId);
                                empStmt.setInt(3, rs.getInt(1));
                                empStmt.executeUpdate();
                            }
                        }
                    }
                    String payMethod = useCard
                            ? LanguageManager.t(settings, "comp_create_pay_card")
                            : LanguageManager.t(settings, "comp_create_pay_cash");
                    event.reply(LanguageManager.t(settings, "comp_create_ok", compName, payMethod)).queue();
                } catch (Exception e) {
                    event.reply(LanguageManager.t(settings, "comp_create_fail")).setEphemeral(true).queue();
                }
            } else {
                event.reply(LanguageManager.t(settings, "comp_no_funds", COMPANY_CREATION_COST)).setEphemeral(true).queue();
            }
        }
    }
}
```

---

## CoreManager.java

```java
package pl.syntaxdevteam.listeners;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;

public class CoreManager extends ListenerAdapter {

    private final DatabaseManager db;

    public CoreManager(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        System.out.println("[System] Bot w pełni połączony! Uruchamiam procesy w tle...");

        ShopSyncTask shopSync = new ShopSyncTask(event.getJDA(), db);
        shopSync.startPolling();

        for (Guild guild : event.getJDA().getGuilds()) {
            WebAPIManager.reportGuildAction(guild.getId(), guild.getName(), "installed");
            registerCommands(guild);
        }
    }

    @Override
    public void onGuildJoin(@NotNull GuildJoinEvent event) {
        WebAPIManager.reportGuildAction(event.getGuild().getId(), event.getGuild().getName(), "installed");
        registerCommands(event.getGuild());
    }

    @Override
    public void onGuildLeave(@NotNull GuildLeaveEvent event) {
        WebAPIManager.reportGuildAction(event.getGuild().getId(), event.getGuild().getName(), "removed");
    }

    private void registerCommands(Guild guild) {
        guild.updateCommands().addCommands(
                localized(Commands.slash("profil", "Check account status and level")
                        .addOption(OptionType.USER, "uzytkownik", "User to check", false),
                        "Sprawdź stan konta i poziom", "Kogo sprawdzić"),
                localized(Commands.slash("work", "Work to earn currency"),
                        "Podejmij pracę i zarób walutę", null),
                localized(Commands.slash("daily", "Claim your daily reward"),
                        "Odbierz codzienną nagrodę", null),
                localized(Commands.slash("zaplac", "Transfer currency to a player")
                        .addOptions(
                                new OptionData(OptionType.USER, "gracz", "Recipient", true),
                                new OptionData(OptionType.INTEGER, "kwota", "Amount", true)),
                        "Przelej walutę graczowi", null),
                localized(Commands.slash("dodajkase", "[ADMIN] Add coins")
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
                        .addOptions(
                                new OptionData(OptionType.USER, "gracz", "Recipient", true),
                                new OptionData(OptionType.INTEGER, "kwota", "Amount", true)),
                        "[ADMIN] Dodaj monety", null),
                localized(Commands.slash("bank", "Manage your bank account")
                        .addOptions(
                                new OptionData(OptionType.STRING, "operacja", "Operation", true)
                                        .addChoice("Deposit", "wplac").addChoice("Withdraw", "wyplac"),
                                new OptionData(OptionType.INTEGER, "kwota", "Amount", true)),
                        "Zarządzaj kontem bankowym", null),
                localized(Commands.slash("sklep", "Open the server shop"),
                        "Otwórz sklep serwerowy", null),
                localized(Commands.slash("statystyki", "Global bot statistics"),
                        "Globalne statystyki bota", null),
                localized(Commands.slash("coinflip", "Casino coinflip game")
                        .addOption(OptionType.INTEGER, "kwota", "Bet amount", true),
                        "Gra z kasynem", null),
                localized(Commands.slash("pets", "Interactive pet panel"),
                        "Panel interaktywny Twojego zwierzaka", null),
                localized(Commands.slash("napad", "Organize a heist on the Server Bank!"),
                        "Zorganizuj napad na Bank Serwera!", null),
                localized(Commands.slash("okradnij", "Try to rob a player (cash only)")
                        .addOption(OptionType.USER, "ofiara", "Victim", true),
                        "Spróbuj okraść gracza (tylko z gotówki)", "Kogo okradamy"),
                localized(Commands.slash("bounty", "Post a bounty on a wanted player")
                        .addOptions(
                                new OptionData(OptionType.USER, "gracz", "Wanted player", true),
                                new OptionData(OptionType.INTEGER, "kwota", "Reward", true)),
                        "Wystaw nagrodę za głowę poszukiwanego gracza", null),
                localized(Commands.slash("poluj", "Hunt a wanted player for bounty")
                        .addOption(OptionType.USER, "gracz", "Target", true),
                        "Spróbuj upolować poszukiwanego i zgarnąć bounty", "Na kogo polujesz"),
                localized(Commands.slash("firma", "Interactive labor office - manage companies"),
                        "Interaktywny Urząd Pracy - Zarządzaj firmami", null),
                localized(Commands.slash("dashboard", "Open your player dashboard on the website"),
                        "Otwórz swój główny panel gracza na stronie WWW", null),
                localized(Commands.slash("konfiguracja", "[ADMIN] Open server management panel on the website")
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR)),
                        "[ADMIN] Otwórz panel zarządzania serwerem na stronie WWW", null),
                localized(Commands.slash("reseteco", "[ADMIN] End season (FULL SERVER ECONOMY RESET)")
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
                        .addOptions(new OptionData(OptionType.BOOLEAN, "potwierdzenie", "Select True", true)),
                        "[ADMIN] Zakończ sezon (CAŁKOWITY RESET EKONOMII SERWERA)", "Wybierz True"),
                localized(Commands.slash("gielda", "Manage your stock portfolio")
                        .addOptions(
                                new OptionData(OptionType.STRING, "operacja", "Operation", true)
                                        .addChoice("📊 Check market", "sprawdz")
                                        .addChoice("📈 Buy shares", "kup")
                                        .addChoice("📉 Sell shares", "sprzedaj"),
                                new OptionData(OptionType.STRING, "akcja", "Stock symbol", false),
                                new OptionData(OptionType.INTEGER, "ilosc", "Quantity", false)),
                        "Zarządzaj swoim portfelem akcji", null)
        ).queue();
    }

    private static net.dv8tion.jda.api.interactions.commands.build.SlashCommandData localized(
            net.dv8tion.jda.api.interactions.commands.build.SlashCommandData cmd,
            String plDescription, String plOptionDesc) {
        cmd.setDescriptionLocalization(DiscordLocale.POLISH, plDescription);
        if (plOptionDesc != null && !cmd.getOptions().isEmpty()) {
            cmd.getOptions().get(cmd.getOptions().size() - 1)
                    .setDescriptionLocalization(DiscordLocale.POLISH, plOptionDesc);
        }
        return cmd;
    }
}
```

---

## CrimeManager.java

```java
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
```

---

## DatabaseManager.java

```java


package pl.syntaxdevteam.listeners;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.List;
import java.util.ArrayList;

                                        public class DatabaseManager {

                                            private Connection connection;
                                            private final Random random = new Random();

                                            // DANE LOGOWANIA DO BAZY MYSQL
                                            private final String DB_HOST = "localhost";
                                            private final String DB_PORT = "3306";
                                            private final String DB_NAME = "econizer";
                                            private final String DB_USER = "bot_econizer";
                                            private final String DB_PASS = "BJTVp-/g[z-a0*yy";

                                            public void connect() {
                                                try {
                                                    Class.forName("com.mysql.cj.jdbc.Driver");
                                                    String url = "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME + "?autoReconnect=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
                                                    connection = DriverManager.getConnection(url, DB_USER, DB_PASS);
                                                    createTables();
                                                    System.out.println("[Database] Silnik MySQL uruchomiony pomyślnie.");
                                                } catch (Exception e) {
                                                    e.printStackTrace();
                                                }
                                            }

                                            public Connection getConnection() {
                                                ensureConnection();
                                                return connection;
                                            }

                                            private void createTables() {
                                                try (Statement stmt = connection.createStatement()) {
                                                    // 1. Ustawienia serwerów
                                                    stmt.execute("CREATE TABLE IF NOT EXISTS bot_guild_settings (" +
                                                            "guild_id VARCHAR(64) PRIMARY KEY, " +
                                                            "economy_enabled INT DEFAULT 1, " +
                                                            "pets_enabled INT DEFAULT 1, " +
                                                            "is_premium INT DEFAULT 0, " +
                                                            "language VARCHAR(10) DEFAULT 'eng', " +
                                                            "currency_name VARCHAR(50) DEFAULT 'coins', " +
                                                            "currency_emoji VARCHAR(50) DEFAULT '🪙', " +
                                                            "daily_amount INT DEFAULT 200, " +
                                                            "min_work INT DEFAULT 50, " +
                                                            "max_work INT DEFAULT 150, " +
                                                            "transfer_tax DOUBLE DEFAULT 0.03, " +
                                                            "level_up_channel_id VARCHAR(64) DEFAULT NULL, " +
                                                            "automod_enabled INT DEFAULT 0, " +
                                                            "autorole_id VARCHAR(64) DEFAULT NULL, " +
                                                            "welcome_channel_id VARCHAR(64) DEFAULT NULL, " +
                                                            "welcome_message TEXT);");

                                                    // 2. Zwierzaki i Słowa
                                                    stmt.execute("CREATE TABLE IF NOT EXISTS bot_guild_pets (guild_id VARCHAR(64), pet_id INT, name VARCHAR(100), coin_mult DOUBLE, exp_mult DOUBLE, price INT, PRIMARY KEY (guild_id, pet_id));");
                                                    stmt.execute("CREATE TABLE IF NOT EXISTS bot_guild_blocked_words (guild_id VARCHAR(64), word VARCHAR(100), PRIMARY KEY (guild_id, word));");
                                                    stmt.execute("CREATE TABLE IF NOT EXISTS bot_analytics_commands (id INT PRIMARY KEY AUTO_INCREMENT, command_name VARCHAR(50), guild_id VARCHAR(64), timestamp BIGINT);");

                                                    // 3. Tabela graczy (główna)
                                                    stmt.execute("CREATE TABLE IF NOT EXISTS bot_user_activity (" +
                                                            "guild_id VARCHAR(64), " +
                                                            "user_id VARCHAR(64), " +
                                                            "exp INT DEFAULT 0, " +
                                                            "level INT DEFAULT 1, " +
                                                            "coins INT DEFAULT 0, " +
                                                            "bank_coins INT DEFAULT 0, " +
                                                            "pet_id INT DEFAULT 0, " +
                                                            "pet_last_fed BIGINT DEFAULT 0, " +
                                                            "work_last_used BIGINT DEFAULT 0, " +
                                                            "daily_last_used BIGINT DEFAULT 0, " +
                                                            "robbery_last_used BIGINT DEFAULT 0, " +
                                                            "hunt_last_used BIGINT DEFAULT 0, " +
                                                            "PRIMARY KEY (guild_id, user_id));");

                                                    // RATUNKOWE DODAWANIE KOLUMN COOLDOWNÓW (O to się rozbijało wszystko wcześniej)
                                                    try { stmt.execute("ALTER TABLE bot_user_activity ADD COLUMN robbery_last_used BIGINT DEFAULT 0;"); } catch (SQLException ignored) {}
                                                    try { stmt.execute("ALTER TABLE bot_user_activity ADD COLUMN hunt_last_used BIGINT DEFAULT 0;"); } catch (SQLException ignored) {}

                                                    // 4. Bounties i Firmy
                                                    stmt.execute("CREATE TABLE IF NOT EXISTS bot_bounties (guild_id VARCHAR(64), user_id VARCHAR(64), amount INT DEFAULT 0, PRIMARY KEY (guild_id, user_id));");
                                                    stmt.execute("CREATE TABLE IF NOT EXISTS bot_companies (id INT PRIMARY KEY AUTO_INCREMENT, guild_id VARCHAR(64), name VARCHAR(100), owner_id VARCHAR(64), vault INT DEFAULT 0, split_owner INT DEFAULT 70, split_emp INT DEFAULT 30);");
                                                    stmt.execute("CREATE TABLE IF NOT EXISTS bot_employees (guild_id VARCHAR(64), user_id VARCHAR(64), company_id INT, PRIMARY KEY (guild_id, user_id));");

                                                } catch (SQLException e) {
                                                    e.printStackTrace();
                                                }
                                            }

                                            private void ensureConnection() {
                                                try {
                                                    if (connection == null || connection.isClosed()) {
                                                        connect();
                                                    }
                                                } catch (SQLException e) { e.printStackTrace(); }
                                            }

                                            public GuildSettings getGuildSettings(String guildId) {
                                                ensureConnection(); GuildSettings settings = new GuildSettings();
                                                try (PreparedStatement pstmt = connection.prepareStatement("SELECT * FROM bot_guild_settings WHERE guild_id = ?")) {
                                                    pstmt.setString(1, guildId); ResultSet rs = pstmt.executeQuery();
                                                    if (rs.next()) {
                                                        settings.economyEnabled = rs.getBoolean("economy_enabled"); settings.isPremium = rs.getBoolean("is_premium");
                                                        settings.language = rs.getString("language"); settings.currencyName = rs.getString("currency_name");
                                                        settings.currencyEmoji = rs.getString("currency_emoji"); settings.dailyAmount = rs.getInt("daily_amount");
                                                        settings.minWork = rs.getInt("min_work"); settings.maxWork = rs.getInt("max_work");
                                                        settings.transferTax = rs.getDouble("transfer_tax"); settings.levelUpChannelId = rs.getString("level_up_channel_id");
                                                        settings.automodEnabled = rs.getBoolean("automod_enabled"); settings.autoroleId = rs.getString("autorole_id");
                                                        settings.welcomeChannelId = rs.getString("welcome_channel_id"); settings.welcomeMessage = rs.getString("welcome_message");
                                                    } else {
                                                        try (PreparedStatement ins = connection.prepareStatement("INSERT INTO bot_guild_settings (guild_id) VALUES (?)")) { ins.setString(1, guildId); ins.executeUpdate(); }
                                                    }
                                                } catch (SQLException e) { e.printStackTrace(); } return settings;
                                            }

                                            public List<String> getBlockedWords(String guildId) {
                                                ensureConnection(); List<String> words = new ArrayList<>();
                                                try (PreparedStatement pstmt = connection.prepareStatement("SELECT word FROM bot_guild_blocked_words WHERE guild_id = ?")) {
                                                    pstmt.setString(1, guildId); ResultSet rs = pstmt.executeQuery();
                                                    while (rs.next()) words.add(rs.getString("word"));
                                                } catch (SQLException e) { e.printStackTrace(); } return words;
                                            }

                                            public long[] getBotStatistics() {
                                                ensureConnection(); long totalCmds = 0, cmds24h = 0; long time24hAgo = System.currentTimeMillis() - 86400000L;
                                                try {
                                                    try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM bot_analytics_commands")) { if (rs.next()) totalCmds = rs.getLong(1); }
                                                    try (PreparedStatement pstmt = connection.prepareStatement("SELECT COUNT(*) FROM bot_analytics_commands WHERE timestamp > ?")) { pstmt.setLong(1, time24hAgo); ResultSet rs = pstmt.executeQuery(); if (rs.next()) cmds24h = rs.getLong(1); }
                                                } catch (SQLException e) { e.printStackTrace(); } return new long[]{totalCmds, cmds24h};
                                            }

                                            public void logCommandUsage(String commandName, String guildId) {
                                                ensureConnection();
                                                try (PreparedStatement pstmt = connection.prepareStatement("INSERT INTO bot_analytics_commands (command_name, guild_id, timestamp) VALUES (?, ?, ?)")) {
                                                    pstmt.setString(1, commandName); pstmt.setString(2, guildId); pstmt.setLong(3, System.currentTimeMillis()); pstmt.executeUpdate();
                                                } catch (SQLException e) { e.printStackTrace(); }
                                            }

                                            public void checkAndCreateUser(String guildId, String userId) {
                                                ensureConnection();
                                                try (PreparedStatement stmt = connection.prepareStatement("INSERT IGNORE INTO bot_user_activity (guild_id, user_id) VALUES (?, ?)")) {
                                                    stmt.setString(1, guildId); stmt.setString(2, userId); stmt.executeUpdate();
                                                } catch (SQLException e) { e.printStackTrace(); }
                                            }

                                            public int[] getUserStats(String guildId, String userId) {
                                                ensureConnection(); checkAndCreateUser(guildId, userId);
                                                try (PreparedStatement pstmt = connection.prepareStatement("SELECT coins, level, exp, bank_coins FROM bot_user_activity WHERE guild_id = ? AND user_id = ?")) {
                                                    pstmt.setString(1, guildId); pstmt.setString(2, userId); ResultSet rs = pstmt.executeQuery();
                                                    if (rs.next()) return new int[]{rs.getInt("coins"), rs.getInt("level"), rs.getInt("exp"), rs.getInt("bank_coins")};
                                                } catch (SQLException e) { e.printStackTrace(); } return new int[]{0, 1, 0, 0};
                                            }

                                            public void addCoins(String guildId, String userId, int amount, String type) {
                                                ensureConnection(); checkAndCreateUser(guildId, userId);
                                                try (PreparedStatement pstmt = connection.prepareStatement("UPDATE bot_user_activity SET coins = coins + ? WHERE guild_id = ? AND user_id = ?")) {
                                                    pstmt.setInt(1, amount); pstmt.setString(2, guildId); pstmt.setString(3, userId); pstmt.executeUpdate();
                                                } catch (SQLException e) { e.printStackTrace(); }
                                            }

                                            public boolean removeCoins(String guildId, String userId, int amount, String type) {
                                                ensureConnection(); checkAndCreateUser(guildId, userId);
                                                if (getUserStats(guildId, userId)[0] < amount) return false;
                                                try (PreparedStatement pstmt = connection.prepareStatement("UPDATE bot_user_activity SET coins = coins - ? WHERE guild_id = ? AND user_id = ?")) {
                                                    pstmt.setInt(1, amount); pstmt.setString(2, guildId); pstmt.setString(3, userId); pstmt.executeUpdate(); return true;
                                                } catch (SQLException e) { return false; }
                                            }

                                            public void addBankCoins(String guildId, String userId, int amount) {
                                                ensureConnection(); checkAndCreateUser(guildId, userId);
                                                try (PreparedStatement pstmt = connection.prepareStatement("UPDATE bot_user_activity SET bank_coins = bank_coins + ? WHERE guild_id = ? AND user_id = ?")) {
                                                    pstmt.setInt(1, amount); pstmt.setString(2, guildId); pstmt.setString(3, userId); pstmt.executeUpdate();
                                                } catch (SQLException e) { e.printStackTrace(); }
                                            }

                                            public boolean removeBankCoins(String guildId, String userId, int amount) {
                                                ensureConnection(); checkAndCreateUser(guildId, userId);
                                                if (getUserStats(guildId, userId)[3] < amount) return false;
                                                try (PreparedStatement pstmt = connection.prepareStatement("UPDATE bot_user_activity SET bank_coins = bank_coins - ? WHERE guild_id = ? AND user_id = ?")) {
                                                    pstmt.setInt(1, amount); pstmt.setString(2, guildId); pstmt.setString(3, userId); pstmt.executeUpdate(); return true;
                                                } catch (SQLException e) { return false; }
                                            }

                                            public int addExpAndCheckLevel(String guildId, String userId, int expAmount) {
                                                ensureConnection(); int currentExp = 0, currentLevel = 1;
                                                try (PreparedStatement pstmt = connection.prepareStatement("SELECT exp, level FROM bot_user_activity WHERE guild_id = ? AND user_id = ?")) {
                                                    pstmt.setString(1, guildId); pstmt.setString(2, userId); ResultSet rs = pstmt.executeQuery();
                                                    if (rs.next()) { currentExp = rs.getInt("exp"); currentLevel = rs.getInt("level"); }
                                                } catch (SQLException e) { e.printStackTrace(); }
                                                currentExp += expAmount; int expNeeded = currentLevel * 100; boolean leveledUp = false;
                                                if (currentExp >= expNeeded) { currentExp -= expNeeded; currentLevel++; leveledUp = true; addCoins(guildId, userId, currentLevel * 50, "LEVEL_UP"); }
                                                try (PreparedStatement pstmt = connection.prepareStatement("UPDATE bot_user_activity SET exp = ?, level = ? WHERE guild_id = ? AND user_id = ?")) {
                                                    pstmt.setInt(1, currentExp); pstmt.setInt(2, currentLevel); pstmt.setString(3, guildId); pstmt.setString(4, userId); pstmt.executeUpdate();
                                                } catch (SQLException e) { e.printStackTrace(); } return leveledUp ? currentLevel : 0;
                                            }

                                            public long getCooldown(String guildId, String userId, String type) {
                                                ensureConnection();
                                                String column = null;
                                                switch (type) {
                                                    case "work": column = "work_last_used"; break;
                                                    case "daily": column = "daily_last_used"; break;
                                                    case "robbery": column = "robbery_last_used"; break;
                                                    case "hunt": column = "hunt_last_used"; break;
                                                }
                                                if (column == null) return 0;

                                                try (PreparedStatement pstmt = connection.prepareStatement("SELECT " + column + " FROM bot_user_activity WHERE guild_id = ? AND user_id = ?")) {
                                                    pstmt.setString(1, guildId); pstmt.setString(2, userId);
                                                    ResultSet rs = pstmt.executeQuery();
                                                    if (rs.next()) return rs.getLong(column);
                                                } catch (SQLException e) { e.printStackTrace(); }
                                                return 0;
                                            }

                                            public void setCooldown(String guildId, String userId, String type, long timestamp) {
                                                ensureConnection(); checkAndCreateUser(guildId, userId);
                                                String column = null;
                                                switch (type) {
                                                    case "work": column = "work_last_used"; break;
                                                    case "daily": column = "daily_last_used"; break;
                                                    case "robbery": column = "robbery_last_used"; break;
                                                    case "hunt": column = "hunt_last_used"; break;
                                                }
                                                if (column == null) return;

                                                try (PreparedStatement pstmt = connection.prepareStatement("UPDATE bot_user_activity SET " + column + " = ? WHERE guild_id = ? AND user_id = ?")) {
                                                    pstmt.setLong(1, timestamp); pstmt.setString(2, guildId); pstmt.setString(3, userId); pstmt.executeUpdate();
                                                } catch (SQLException e) { e.printStackTrace(); }
                                            }

                                            public boolean isPetsEnabled(String guildId) {
                                                ensureConnection();
                                                try (PreparedStatement pstmt = connection.prepareStatement("SELECT pets_enabled FROM bot_guild_settings WHERE guild_id = ?")) {
                                                    pstmt.setString(1, guildId); ResultSet rs = pstmt.executeQuery(); if (rs.next()) return rs.getBoolean("pets_enabled");
                                                } catch (SQLException e) { e.printStackTrace(); } return true;
                                            }

                                            public List<Object[]> getAllPets(String guildId, boolean isPremium) {
                                                ensureConnection(); List<Object[]> pets = new ArrayList<>();
                                                if (isPremium) {
                                                    try (PreparedStatement pstmt = connection.prepareStatement("SELECT pet_id, name, coin_mult, exp_mult, price FROM bot_guild_pets WHERE guild_id = ?")) {
                                                        pstmt.setString(1, guildId); ResultSet rs = pstmt.executeQuery();
                                                        while (rs.next()) { pets.add(new Object[]{rs.getInt("pet_id"), rs.getString("name"), rs.getDouble("coin_mult"), rs.getDouble("exp_mult"), rs.getInt("price")}); }
                                                    } catch (SQLException e) { e.printStackTrace(); }
                                                }
                                                if (pets.isEmpty()) {
                                                    pets.add(new Object[]{1, "Blue Slime", 1.25, 1.00, 2500});
                                                    pets.add(new Object[]{2, "Fire Fox", 1.50, 1.25, 10000});
                                                    pets.add(new Object[]{3, "Dark Dragon", 1.75, 1.50, 25000});
                                                }
                                                return pets;
                                            }

                                            public Object[] getPetConfig(String guildId, int petId, boolean isPremium) {
                                                ensureConnection();
                                                if (isPremium) {
                                                    try (PreparedStatement pstmt = connection.prepareStatement("SELECT name, coin_mult, exp_mult, price FROM bot_guild_pets WHERE guild_id = ? AND pet_id = ?")) {
                                                        pstmt.setString(1, guildId); pstmt.setInt(2, petId); ResultSet rs = pstmt.executeQuery();
                                                        if (rs.next()) return new Object[]{rs.getString("name"), null, rs.getDouble("coin_mult"), rs.getDouble("exp_mult"), rs.getInt("price")};
                                                    } catch (SQLException e) { e.printStackTrace(); }
                                                }
                                                if (petId == 1) return new Object[]{"Blue Slime", null, 1.25, 1.00, 2500};
                                                if (petId == 2) return new Object[]{"Fire Fox", null, 1.50, 1.25, 10000};
                                                if (petId == 3) return new Object[]{"Dark Dragon", null, 1.75, 1.50, 25000};
                                                return null;
                                            }

                                            public double[] getActiveMultipliers(String guildId, String userId) {
                                                ensureConnection(); int petId = getUserPetId(guildId, userId); if (petId == 0) return new double[]{1.0, 1.0};
                                                long lastFed = getPetLastFed(guildId, userId); if (System.currentTimeMillis() - lastFed > 86400000) return new double[]{1.0, 1.0};
                                                Object[] config = getPetConfig(guildId, petId, getGuildSettings(guildId).isPremium);
                                                if (config == null) return new double[]{1.0, 1.0}; return new double[]{(double)config[2], (double)config[3]};
                                            }

                                            public int getUserPetId(String guildId, String userId) {
                                                ensureConnection();
                                                try (PreparedStatement pstmt = connection.prepareStatement("SELECT pet_id FROM bot_user_activity WHERE guild_id = ? AND user_id = ?")) {
                                                    pstmt.setString(1, guildId); pstmt.setString(2, userId); ResultSet rs = pstmt.executeQuery(); if (rs.next()) return rs.getInt("pet_id");
                                                } catch (SQLException e) { e.printStackTrace(); } return 0;
                                            }

                                            public long getPetLastFed(String guildId, String userId) {
                                                ensureConnection();
                                                try (PreparedStatement pstmt = connection.prepareStatement("SELECT pet_last_fed FROM bot_user_activity WHERE guild_id = ? AND user_id = ?")) {
                                                    pstmt.setString(1, guildId); pstmt.setString(2, userId); ResultSet rs = pstmt.executeQuery(); if (rs.next()) return rs.getLong("pet_last_fed");
                                                } catch (SQLException e) { e.printStackTrace(); } return 0;
                                            }

                                            public void assignPet(String guildId, String userId, int petId) {
                                                ensureConnection();
                                                try (PreparedStatement pstmt = connection.prepareStatement("UPDATE bot_user_activity SET pet_id = ?, pet_last_fed = ? WHERE guild_id = ? AND user_id = ?")) {
                                                    pstmt.setInt(1, petId); pstmt.setLong(2, System.currentTimeMillis()); pstmt.setString(3, guildId); pstmt.setString(4, userId); pstmt.executeUpdate();
                                                } catch (SQLException e) { e.printStackTrace(); }
                                            }

                                            public void updatePetFed(String guildId, String userId, long timestamp) {
                                                ensureConnection();
                                                try (PreparedStatement pstmt = connection.prepareStatement("UPDATE bot_user_activity SET pet_last_fed = ? WHERE guild_id = ? AND user_id = ?")) {
                                                    pstmt.setLong(1, timestamp); pstmt.setString(2, guildId); pstmt.setString(3, userId); pstmt.executeUpdate();
                                                } catch (SQLException e) { e.printStackTrace(); }
                                            }

                                            public Map<String, Integer> getAndRefreshStocks(String guildId) {
                                                ensureConnection(); Map<String, Integer> currentPrices = new HashMap<>();
                                                try (PreparedStatement pstmt = connection.prepareStatement("SELECT id, symbol, current_price, updated_at FROM econizer_market_assets WHERE guild_id = ? AND is_active = 1")) {
                                                    pstmt.setString(1, guildId); ResultSet rs = pstmt.executeQuery();
                                                    while (rs.next()) {
                                                        long assetId = rs.getLong("id"); String symbol = rs.getString("symbol"); int price = rs.getInt("current_price");
                                                        Timestamp updated = rs.getTimestamp("updated_at");
                                                        if (System.currentTimeMillis() - updated.getTime() > 600000) {
                                                            price = Math.max(price + (random.nextInt(61) - 30), 1);
                                                            try (PreparedStatement u1 = connection.prepareStatement("UPDATE econizer_market_assets SET current_price = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?")) { u1.setInt(1, price); u1.setLong(2, assetId); u1.executeUpdate(); }
                                                        }
                                                        currentPrices.put(symbol, price);
                                                    }
                                                } catch (SQLException e) { e.printStackTrace(); } return currentPrices;
                                            }

                                            public int getUserStockAmount(String guildId, String userId, String symbol) {
                                                ensureConnection();
                                                try (PreparedStatement pstmt = connection.prepareStatement("SELECT h.quantity FROM econizer_market_holdings h JOIN econizer_market_assets a ON h.asset_id = a.id WHERE a.guild_id = ? AND h.user_id = ? AND a.symbol = ?")) {
                                                    pstmt.setString(1, guildId); pstmt.setString(2, userId); pstmt.setString(3, symbol); ResultSet rs = pstmt.executeQuery(); if (rs.next()) return rs.getInt("quantity");
                                                } catch (SQLException e) { e.printStackTrace(); } return 0;
                                            }

                                            public void updateUserStock(String guildId, String userId, String symbol, int amountChange, int price) {
                                                ensureConnection(); long assetId = -1;
                                                try (PreparedStatement pstmt = connection.prepareStatement("SELECT id FROM econizer_market_assets WHERE guild_id = ? AND symbol = ?")) {
                                                    pstmt.setString(1, guildId); pstmt.setString(2, symbol); ResultSet rs = pstmt.executeQuery(); if (rs.next()) assetId = rs.getLong("id");
                                                } catch (SQLException e) { e.printStackTrace(); }
                                                if (assetId == -1) return; int currentAmount = getUserStockAmount(guildId, userId, symbol); int newAmount = currentAmount + amountChange;
                                                try {
                                                    if (currentAmount == 0 && amountChange > 0) {
                                                        try (PreparedStatement p = connection.prepareStatement("INSERT INTO econizer_market_holdings (asset_id, user_id, quantity, average_price) VALUES (?, ?, ?, ?)")) { p.setLong(1, assetId); p.setString(2, userId); p.setInt(3, newAmount); p.setInt(4, price); p.executeUpdate(); }
                                                    } else if (newAmount > 0) {
                                                        try (PreparedStatement p = connection.prepareStatement("UPDATE econizer_market_holdings SET quantity = ? WHERE asset_id = ? AND user_id = ?")) { p.setInt(1, newAmount); p.setLong(2, assetId); p.setString(3, userId); p.executeUpdate(); }
                                                    } else {
                                                        try (PreparedStatement p = connection.prepareStatement("DELETE FROM econizer_market_holdings WHERE asset_id = ? AND user_id = ?")) { p.setLong(1, assetId); p.setString(2, userId); p.executeUpdate(); }
                                                    }
                                                } catch (SQLException e) { e.printStackTrace(); }
                                            }

                                            public int getBounty(String guildId, String userId) {
                                                try (PreparedStatement pstmt = connection.prepareStatement("SELECT amount FROM bot_bounties WHERE guild_id = ? AND user_id = ?")) {
                                                    pstmt.setString(1, guildId); pstmt.setString(2, userId); ResultSet rs = pstmt.executeQuery(); if (rs.next()) return rs.getInt("amount");
                                                } catch (SQLException e) { e.printStackTrace(); } return 0;
                                            }

                                            public void addBounty(String guildId, String userId, int amount) {
                                                try (PreparedStatement pstmt = connection.prepareStatement("INSERT INTO bot_bounties (guild_id, user_id, amount) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE amount = amount + ?")) {
                                                    pstmt.setString(1, guildId); pstmt.setString(2, userId); pstmt.setInt(3, amount); pstmt.setInt(4, amount); pstmt.executeUpdate();
                                                } catch (SQLException e) { e.printStackTrace(); }
                                            }

                                            public void clearBounty(String guildId, String userId) {
                                                try (PreparedStatement pstmt = connection.prepareStatement("DELETE FROM bot_bounties WHERE guild_id = ? AND user_id = ?")) {
                                                    pstmt.setString(1, guildId); pstmt.setString(2, userId); pstmt.executeUpdate();
                                                } catch (SQLException e) { e.printStackTrace(); }
                                            }

                                            public void resetServerEconomy(String guildId) {
                                                ensureConnection();
                                                try {
                                                    try (PreparedStatement pstmt = connection.prepareStatement("UPDATE bot_user_activity SET coins = 0, bank_coins = 0, exp = 0, level = 1 WHERE guild_id = ?")) { pstmt.setString(1, guildId); pstmt.executeUpdate(); }
                                                    try (PreparedStatement pstmt = connection.prepareStatement("DELETE FROM bot_companies WHERE guild_id = ?")) { pstmt.setString(1, guildId); pstmt.executeUpdate(); }
                                                    try (PreparedStatement pstmt = connection.prepareStatement("DELETE FROM bot_employees WHERE guild_id = ?")) { pstmt.setString(1, guildId); pstmt.executeUpdate(); }
                                                    try (PreparedStatement pstmt = connection.prepareStatement("DELETE FROM bot_bounties WHERE guild_id = ?")) { pstmt.setString(1, guildId); pstmt.executeUpdate(); }
                                                } catch (SQLException e) { e.printStackTrace(); }
                                            }
                                        }```

---

## EconomyManager.java

```java
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
import java.sql.Connection;
import java.sql.DriverManager;
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

        if (cmd.equals("sklep")) {
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

        if (cmd.equals("konfiguracja")) {
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
            if (!event.getOption("potwierdzenie").getAsBoolean()) {
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

        if (cmd.equals("statystyki")) {
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
            String op = event.getOption("operacja").getAsString();
            int kwota = event.getOption("kwota").getAsInt();
            if (kwota <= 0) {
                event.reply(LanguageManager.t(settings, "err_amount")).setEphemeral(true).queue();
                return;
            }
            if (op.equals("wplac")) {
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

        if (cmd.equals("profil")) {
            OptionMapping uOpt = event.getOption("uzytkownik");
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
            EmbedBuilder eb = new EmbedBuilder().setColor(Color.decode("#F1C40F"))
                    .setAuthor(LanguageManager.t(settings, "profile_author", target.getName()), null, target.getEffectiveAvatarUrl())
                    .setThumbnail(target.getEffectiveAvatarUrl())
                    .addField(LanguageManager.t(settings, "profile_field_cash"), "`" + userStats[0] + "` " + settings.currencyEmoji, true)
                    .addField(LanguageManager.t(settings, "profile_field_bank"), "`" + userStats[3] + "` " + settings.currencyEmoji, true)
                    .addField(LanguageManager.t(settings, "profile_field_level"), "Lvl `" + userStats[1] + "`", true)
                    .addField(LanguageManager.t(settings, "profile_field_exp"), "`" + userStats[2] + " / " + (userStats[1] * 100) + "` XP", false)
                    .addField(LanguageManager.t(settings, "profile_field_pet"), "**" + petName + "**", false);
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
            int earned = (int) ((random.nextInt((settings.maxWork - settings.minWork) + 1) + settings.minWork) * mults[0]);
            int exp = (int) ((random.nextInt(21) + 20) * mults[1]);
            db.addCoins(guildId, userId, earned, "WORK");
            db.setCooldown(guildId, userId, "work", cur + 600000);
            int newLvl = db.addExpAndCheckLevel(guildId, userId, exp);

            try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/econizer", "root", "twoje_haslo")) {
                CompanyManager.handleEmployeeWork(conn, guildId, userId, earned);
            } catch (Exception ignored) {}

            EmbedBuilder eb = new EmbedBuilder().setColor(Color.decode("#2ECC71"))
                    .setAuthor(LanguageManager.t(settings, "work_author", event.getUser().getName()), null, event.getUser().getEffectiveAvatarUrl())
                    .setThumbnail(event.getUser().getEffectiveAvatarUrl())
                    .setDescription(LanguageManager.t(settings, "work_desc", userId, earned, settings.currencyEmoji, exp));
            if (mults[0] > 1.0) eb.appendDescription(LanguageManager.t(settings, "pet_bonus"));
            if (newLvl > 0) eb.appendDescription(LanguageManager.t(settings, "level_up", newLvl));
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
            int earned = (int) (settings.dailyAmount * mults[0]);
            int exp = (int) ((random.nextInt(101) + 150) * mults[1]);
            db.addCoins(guildId, userId, earned, "DAILY");
            db.setCooldown(guildId, userId, "daily", cur + 86400000);
            int newLvl = db.addExpAndCheckLevel(guildId, userId, exp);

            EmbedBuilder eb = new EmbedBuilder().setColor(Color.decode("#3498DB"))
                    .setAuthor(LanguageManager.t(settings, "daily_author"), null, event.getUser().getEffectiveAvatarUrl())
                    .setThumbnail(event.getUser().getEffectiveAvatarUrl())
                    .setDescription(LanguageManager.t(settings, "daily_desc", userId, earned, settings.currencyEmoji, exp));
            if (mults[0] > 1.0) eb.appendDescription(LanguageManager.t(settings, "daily_pet_bonus"));
            if (newLvl > 0) eb.appendDescription(LanguageManager.t(settings, "level_up", newLvl));
            event.replyEmbeds(eb.build()).queue();
            return;
        }

        if (cmd.equals("zaplac")) {
            User target = event.getOption("gracz").getAsUser();
            int amt = event.getOption("kwota").getAsInt();
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
            int amt = event.getOption("kwota").getAsInt();
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

        if (cmd.equals("dodajkase")) {
            if (event.getMember() != null && !event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
                event.reply(LanguageManager.t(settings, "admin_err_perm")).setEphemeral(true).queue();
                return;
            }
            User target = event.getOption("gracz").getAsUser();
            int amt = event.getOption("kwota").getAsInt();
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
```

---

## GuildSettings.java

```java
package pl.syntaxdevteam.listeners;

public class GuildSettings {

    // --- Ustawienia Ekonomii ---
    public boolean economyEnabled = true;
    public boolean isPremium = false;
    public String language = "eng";
    public String currencyName = "coins";
    public String currencyEmoji = "🪙";
    public int dailyAmount = 200;
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
}```

---

## LanguageManager.java

```java
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
}```

---

## LevelingManager.java

```java
package pl.syntaxdevteam.listeners;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class LevelingManager extends ListenerAdapter {
    private final DatabaseManager db;
    private final Random random = new Random();
    private final Map<String, Long> chatCooldown = new ConcurrentHashMap<>();

    public LevelingManager(DatabaseManager db) { this.db = db; }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || !event.isFromGuild()) return;
        String guildId = event.getGuild().getId();
        String userId = event.getAuthor().getId();
        GuildSettings settings = db.getGuildSettings(guildId);
        if (!settings.economyEnabled) return;

        long cur = System.currentTimeMillis();
        String key = guildId + "-" + userId;
        if (chatCooldown.size() > 5000) chatCooldown.clear();

        if (!chatCooldown.containsKey(key) || cur >= chatCooldown.get(key)) {
            double[] mults = db.getActiveMultipliers(guildId, userId);
            int exp = (int) ((random.nextInt(16) + 10) * mults[1]);
            int newLvl = db.addExpAndCheckLevel(guildId, userId, exp);

            if (newLvl > 0) {
                int reward = newLvl * 50;
                EmbedBuilder eb = new EmbedBuilder().setColor(Color.decode("#E67E22"))
                        .setTitle(LanguageManager.t(settings, "levelup_chat_title"))
                        .setDescription(LanguageManager.t(settings, "levelup_chat_desc", userId, newLvl, reward, settings.currencyEmoji))
                        .setThumbnail(event.getAuthor().getEffectiveAvatarUrl());
                TextChannel ch = (settings.levelUpChannelId != null)
                        ? event.getGuild().getTextChannelById(settings.levelUpChannelId)
                        : event.getChannel().asTextChannel();
                if (ch != null && ch.canTalk()) ch.sendMessageEmbeds(eb.build()).queue();
            }
            chatCooldown.put(key, cur + 60000);
        }
    }
}
```

---

## MarketManager.java

```java
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
        if (!event.getName().equals("gielda")) return;
        String guildId = event.getGuild().getId();
        String userId = event.getUser().getId();
        GuildSettings settings = db.getGuildSettings(guildId);

        if (!settings.economyEnabled) {
            event.reply(LanguageManager.t(settings, "eco_disabled")).setEphemeral(true).queue();
            return;
        }

        String op = event.getOption("operacja").getAsString();
        OptionMapping aOpt = event.getOption("akcja");
        OptionMapping iOpt = event.getOption("ilosc");
        Map<String, Integer> stocks = db.getAndRefreshStocks(guildId);

        if (op.equals("sprawdz")) {
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
        if (op.equals("kup")) {
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
```

---

## ModerationManager.java

```java
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
```

---

## PetManager.java

```java
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
        String guildId = event.getGuild().getId();
        String userId = event.getUser().getId();
        GuildSettings settings = db.getGuildSettings(guildId);

        if (!settings.economyEnabled || !db.isPetsEnabled(guildId)) {
            event.reply(LanguageManager.t(settings, "pet_disabled")).setEphemeral(true).queue();
            return;
        }

        EmbedBuilder eb = new EmbedBuilder().setColor(Color.decode("#9B59B6"))
                .setAuthor(LanguageManager.t(settings, "pet_title"), null, event.getUser().getEffectiveAvatarUrl())
                .setDescription(LanguageManager.t(settings, "pet_desc"));

        event.replyEmbeds(eb.build()).addComponents(ActionRow.of(
                Button.primary("pet_buy:" + userId, LanguageManager.t(settings, "pet_btn_buy")),
                Button.success("pet_feed_prompt:" + userId, LanguageManager.t(settings, "pet_btn_feed")),
                Button.secondary("pet_status:" + userId, LanguageManager.t(settings, "pet_btn_status"))
        )).setEphemeral(true).queue();
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
            case "pet_buy":
                if (db.getUserPetId(guildId, userId) > 0) {
                    event.reply(LanguageManager.t(settings, "pet_err_has_pet")).setEphemeral(true).queue();
                    return;
                }
                EmbedBuilder shopEmbed = new EmbedBuilder().setColor(Color.decode("#E67E22"))
                        .setAuthor(LanguageManager.t(settings, "pet_shop_title"), null, event.getUser().getEffectiveAvatarUrl());
                List<Button> buttons = new ArrayList<>();
                for (Object[] pet : db.getAllPets(guildId, settings.isPremium)) {
                    shopEmbed.addField(pet[0] + ". " + pet[1],
                            LanguageManager.t(settings, "pet_shop_field", pet[4], settings.currencyEmoji, pet[2], pet[3]), false);
                    buttons.add(Button.primary("pet_buy_select:" + pet[0] + ":" + userId,
                            LanguageManager.t(settings, "pet_btn_select", pet[1])));
                }
                event.editMessageEmbeds(shopEmbed.build()).setComponents(ActionRow.of(buttons)).queue();
                break;

            case "pet_buy_select":
                int selectId = Integer.parseInt(parts[1]);
                Object[] pConf = db.getPetConfig(guildId, selectId, settings.isPremium);
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
```

---

## ShopSyncTask.java

```java
package pl.syntaxdevteam.listeners;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ShopSyncTask {
    private final JDA jda;
    private final DatabaseManager db;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final HttpClient client = HttpClient.newHttpClient();

    private final String apiToken = WebAPIManager.getApiToken();
    private final String baseUrl = WebAPIManager.BASE_URL;

    public ShopSyncTask(JDA jda, DatabaseManager db) { this.jda = jda; this.db = db; }

    public void startPolling() {
        scheduler.scheduleAtFixedRate(() -> {
            for (Guild guild : jda.getGuilds()) fetchAndFulfillOrders(guild);
        }, 10, 60, TimeUnit.SECONDS);
    }

    private void fetchAndFulfillOrders(Guild guild) {
        if (apiToken == null) {
            return;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/econizer/shop/orders?guild_id=" + guild.getId())).header("X-Econizer-Token", apiToken).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && !response.body().isEmpty() && !response.body().equals("[]")) {
                processOrdersJson(guild, response.body());
            }
        } catch (Exception ignored) {}
    }

    private void processOrdersJson(Guild guild, String json) {
        Pattern pattern = Pattern.compile("\\{\"order_id\":(\\d+),\"discord_user_id\":\"(\\d+)\",\"delivery_type\":\"([^\"]+)\",\"delivery_reference\":\"([^\"]+)\"\\}");
        Matcher matcher = pattern.matcher(json);
        while (matcher.find()) {
            String orderId = matcher.group(1); String userId = matcher.group(2); String type = matcher.group(3); String reference = matcher.group(4);
            boolean success = false;

            if (type.equals("discord_role")) {
                Role role = guild.getRoleById(reference);
                if (role != null) {
                    guild.retrieveMemberById(userId).queue(member -> { guild.addRoleToMember(member, role).queue(); }, err -> {});
                    success = true;
                }
            } else if (type.equals("virtual_item")) { success = true; } // Item dla bota

            if (success) confirmFulfillment(orderId);
        }
    }

    private void confirmFulfillment(String orderId) {
        if (apiToken == null) {
            return;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/econizer/shop/orders/fulfill")).header("X-Econizer-Token", apiToken).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{\"order_id\":" + orderId + "}")).build();
            client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception ignored) {}
    }
}
```

---

## WebAPIManager.java

```java
package pl.syntaxdevteam.listeners;

import io.github.cdimascio.dotenv.Dotenv;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

public class WebAPIManager {

    public static final String BASE_URL = "https://econizer.syntaxdevteam.pl";
    private static final String API_TOKEN = loadApiToken();
    private static final HttpClient httpClient = HttpClient.newHttpClient();

    public static void reportGuildAction(String guildId, String guildName, String action) {
        String url = BASE_URL + "/api/econizer/guilds";
        String jsonPayload = String.format(
                "{\"guild_id\":\"%s\",\"name\":\"%s\",\"action\":\"%s\"}",
                escapeJson(guildId), escapeJson(guildName), escapeJson(action)
        );
        sendPostRequest(url, jsonPayload, "guild " + action + " " + guildId);
    }

    public static void reportEconomyEvent(String guildId, String userId, String type, int amount, int exp, int level, String description) {
        String url = BASE_URL + "/api/econizer/events";
        String eventId = UUID.randomUUID().toString();

        String jsonPayload = String.format(
                "{\"event_id\":\"%s\",\"guild_id\":\"%s\",\"user_id\":\"%s\",\"type\":\"%s\",\"amount\":%d,\"experience\":%d,\"level\":%d,\"description\":\"%s\"}",
                escapeJson(eventId), escapeJson(guildId), escapeJson(userId), escapeJson(type), amount, exp, level, escapeJson(description)
        );
        sendPostRequest(url, jsonPayload, "economy event " + eventId);
    }

    public static String getApiToken() {
        return API_TOKEN;
    }

    private static void sendPostRequest(String url, String jsonPayload, String context) {
        if (API_TOKEN == null) {
            System.err.println("[WebAPI Błąd] Brak ECONIZER_API_TOKEN - pomijam synchronizację: " + context);
            return;
        }

        Thread.startVirtualThread(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(10))
                        .header("X-Econizer-Token", API_TOKEN)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    System.err.println("[WebAPI Błąd] Nieudana synchronizacja " + context + ". HTTP " + response.statusCode());
                }
            } catch (Exception e) {
                System.err.println("[WebAPI Błąd] Nie udało się połączyć ze stroną WWW dla: " + context);
            }
        });
    }

    private static String loadApiToken() {
        String token = System.getenv("ECONIZER_API_TOKEN");
        if (token != null && !token.isBlank()) {
            return token.trim();
        }

        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        token = dotenv.get("ECONIZER_API_TOKEN");
        if (token != null && !token.isBlank()) {
            return token.trim();
        }

        return null;
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
```

---

