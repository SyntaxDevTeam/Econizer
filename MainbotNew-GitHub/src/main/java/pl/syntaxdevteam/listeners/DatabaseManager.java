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

    // Pobieranie danych z ENV z fallbackiem na stare, zakodowane na sztywno dane.
    // Zdecydowanie zalecam przenieść te dane do pliku .env!
    private final String DB_HOST = System.getenv("DB_HOST") != null ? System.getenv("DB_HOST") : "localhost";
    private final String DB_PORT = System.getenv("DB_PORT") != null ? System.getenv("DB_PORT") : "3306";
    private final String DB_NAME = System.getenv("DB_NAME") != null ? System.getenv("DB_NAME") : "econizer";
    private final String DB_USER = System.getenv("DB_USER") != null ? System.getenv("DB_USER") : "bot_econizer";
    private final String DB_PASS = System.getenv("DB_PASS") != null ? System.getenv("DB_PASS") : "BJTVp-/g[z-a0*yy";

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
            stmt.execute("CREATE TABLE IF NOT EXISTS bot_guild_settings (" +
                    "guild_id VARCHAR(64) PRIMARY KEY, " +
                    "economy_enabled INT DEFAULT 1, " +
                    "pets_enabled INT DEFAULT 1, " +
                    "is_premium INT DEFAULT 0, " +
                    "language VARCHAR(10) DEFAULT 'pl', " +
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
                    "welcome_message TEXT, " +
                    "max_shop_items INT DEFAULT 5, " +
                    "pets_panel_image VARCHAR(255) DEFAULT 'https://media.giphy.com/media/Jk2WhNDxjzvgc/giphy.gif', " +
                    "passive_income_amount INT DEFAULT 500, " +
                    "vip_role_id VARCHAR(64) DEFAULT NULL, " +
                    "shop_base_url VARCHAR(255) DEFAULT 'https://econizer.syntaxdevteam.pl/econizer/shop/', " +
                    "comp_cost INT DEFAULT 25000, " +
                    "company_roles_enabled INT DEFAULT 1, " +
                    "company_tags_enabled INT DEFAULT 1, " +
                    "company_chat_enabled INT DEFAULT 1" +
                    ");");

            try { stmt.execute("ALTER TABLE bot_guild_settings ADD COLUMN max_shop_items INT DEFAULT 5;"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE bot_guild_settings ADD COLUMN pets_panel_image VARCHAR(255) DEFAULT 'https://media.giphy.com/media/Jk2WhNDxjzvgc/giphy.gif';"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE bot_guild_settings ADD COLUMN passive_income_amount INT DEFAULT 500;"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE bot_guild_settings ADD COLUMN vip_role_id VARCHAR(64) DEFAULT NULL;"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE bot_guild_settings ADD COLUMN shop_base_url VARCHAR(255) DEFAULT 'https://econizer.syntaxdevteam.pl/econizer/shop/';"); } catch (SQLException ignored) {}

            try { stmt.execute("ALTER TABLE bot_guild_settings ADD COLUMN comp_cost INT DEFAULT 25000;"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE bot_guild_settings ADD COLUMN company_roles_enabled INT DEFAULT 1;"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE bot_guild_settings ADD COLUMN company_tags_enabled INT DEFAULT 1;"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE bot_guild_settings ADD COLUMN company_chat_enabled INT DEFAULT 1;"); } catch (SQLException ignored) {}

            try { stmt.execute("ALTER TABLE bot_guild_settings ADD COLUMN daily_amount_2 INT DEFAULT 6000;"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE bot_guild_settings ADD COLUMN daily_amount_3 INT DEFAULT 15000;"); } catch (SQLException ignored) {}

            // WEB PANEL READY: Kolumny zarządzające widocznością odpowiedzi na komendy
            try { stmt.execute("ALTER TABLE bot_guild_settings ADD COLUMN hide_economy_replies INT DEFAULT 0;"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE bot_guild_settings ADD COLUMN hide_crime_replies INT DEFAULT 0;"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE bot_guild_settings ADD COLUMN hide_company_replies INT DEFAULT 1;"); } catch (SQLException ignored) {}

            stmt.execute("CREATE TABLE IF NOT EXISTS bot_guild_pets (guild_id VARCHAR(64), pet_id INT, name VARCHAR(100), coin_mult DOUBLE, exp_mult DOUBLE, price INT, PRIMARY KEY (guild_id, pet_id));");
            stmt.execute("CREATE TABLE IF NOT EXISTS bot_guild_blocked_words (guild_id VARCHAR(64), word VARCHAR(100), PRIMARY KEY (guild_id, word));");
            stmt.execute("CREATE TABLE IF NOT EXISTS bot_analytics_commands (id INT PRIMARY KEY AUTO_INCREMENT, command_name VARCHAR(50), guild_id VARCHAR(64), timestamp BIGINT);");

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

            try { stmt.execute("ALTER TABLE bot_user_activity ADD COLUMN robbery_last_used BIGINT DEFAULT 0;"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE bot_user_activity ADD COLUMN hunt_last_used BIGINT DEFAULT 0;"); } catch (SQLException ignored) {}

            try { stmt.execute("ALTER TABLE bot_user_activity ADD COLUMN loan_amount INT DEFAULT 0;"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE bot_user_activity ADD COLUMN loan_taken_at BIGINT DEFAULT 0;"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE bot_user_activity ADD COLUMN loan_last_interest BIGINT DEFAULT 0;"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE bot_user_activity ADD COLUMN deposit_last_claim BIGINT DEFAULT 0;"); } catch (SQLException ignored) {}

            stmt.execute("CREATE TABLE IF NOT EXISTS bot_bounties (guild_id VARCHAR(64), user_id VARCHAR(64), amount INT DEFAULT 0, PRIMARY KEY (guild_id, user_id));");
            stmt.execute("CREATE TABLE IF NOT EXISTS bot_companies (id INT PRIMARY KEY AUTO_INCREMENT, guild_id VARCHAR(64), name VARCHAR(100), owner_id VARCHAR(64), vault INT DEFAULT 0, split_owner INT DEFAULT 70, split_emp INT DEFAULT 30);");
            stmt.execute("CREATE TABLE IF NOT EXISTS bot_employees (guild_id VARCHAR(64), user_id VARCHAR(64), company_id INT, PRIMARY KEY (guild_id, user_id));");

            try { stmt.execute("ALTER TABLE bot_companies ADD COLUMN total_earned INT DEFAULT 0;"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE bot_companies ADD COLUMN tag VARCHAR(10) DEFAULT 'CMP';"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE bot_companies ADD COLUMN role_id VARCHAR(64) DEFAULT NULL;"); } catch (SQLException ignored) {}

            stmt.execute("CREATE TABLE IF NOT EXISTS econizer_market_assets (id BIGINT PRIMARY KEY AUTO_INCREMENT, guild_id VARCHAR(64), symbol VARCHAR(10), current_price INT, is_active INT DEFAULT 1, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);");
            stmt.execute("CREATE TABLE IF NOT EXISTS econizer_market_holdings (asset_id BIGINT, user_id VARCHAR(64), quantity INT, average_price INT, PRIMARY KEY (asset_id, user_id));");

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
        ensureConnection();
        GuildSettings settings = new GuildSettings();
        try (PreparedStatement pstmt = connection.prepareStatement("SELECT * FROM bot_guild_settings WHERE guild_id = ?")) {
            pstmt.setString(1, guildId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                settings.economyEnabled = rs.getBoolean("economy_enabled");
                settings.isPremium = rs.getBoolean("is_premium");
                settings.language = rs.getString("language");
                settings.currencyName = rs.getString("currency_name");
                settings.currencyEmoji = rs.getString("currency_emoji");
                settings.dailyAmount = rs.getInt("daily_amount");
                try { settings.dailyAmount2 = rs.getInt("daily_amount_2"); } catch (SQLException ignored) {}
                try { settings.dailyAmount3 = rs.getInt("daily_amount_3"); } catch (SQLException ignored) {}
                settings.minWork = rs.getInt("min_work");
                settings.maxWork = rs.getInt("max_work");
                settings.transferTax = rs.getDouble("transfer_tax");
                settings.levelUpChannelId = rs.getString("level_up_channel_id");
                settings.automodEnabled = rs.getBoolean("automod_enabled");
                settings.autoroleId = rs.getString("autorole_id");
                settings.welcomeChannelId = rs.getString("welcome_channel_id");
                settings.welcomeMessage = rs.getString("welcome_message");
                settings.maxShopItems = rs.getInt("max_shop_items");
                settings.petsPanelImage = rs.getString("pets_panel_image");
                settings.passiveIncomeAmount = rs.getInt("passive_income_amount");
                settings.vipRoleId = rs.getString("vip_role_id");
                settings.shopBaseUrl = rs.getString("shop_base_url");

                // WEB PANEL READY: Pobieranie flag widoczności
                try { settings.hideEconomyReplies = rs.getBoolean("hide_economy_replies"); } catch (SQLException ignored) {}
                try { settings.hideCrimeReplies = rs.getBoolean("hide_crime_replies"); } catch (SQLException ignored) {}
                try { settings.hideCompanyReplies = rs.getBoolean("hide_company_replies"); } catch (SQLException ignored) {}
            } else {
                try (PreparedStatement ins = connection.prepareStatement("INSERT INTO bot_guild_settings (guild_id) VALUES (?)")) {
                    ins.setString(1, guildId);
                    ins.executeUpdate();
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return settings;
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

    public long[] getBankData(String guildId, String userId) {
        ensureConnection(); checkAndCreateUser(guildId, userId);
        try (PreparedStatement pstmt = connection.prepareStatement("SELECT bank_coins, loan_amount, deposit_last_claim, loan_taken_at, level, coins FROM bot_user_activity WHERE guild_id = ? AND user_id = ?")) {
            pstmt.setString(1, guildId); pstmt.setString(2, userId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return new long[]{rs.getLong("bank_coins"), rs.getLong("loan_amount"), rs.getLong("deposit_last_claim"), rs.getLong("loan_taken_at"), rs.getLong("level"), rs.getLong("coins")};
        } catch (SQLException e) { e.printStackTrace(); }
        return new long[]{0, 0, 0, 0, 1, 0};
    }

    public void takeLoan(String guildId, String userId, int amount) {
        ensureConnection();
        try (PreparedStatement pstmt = connection.prepareStatement("UPDATE bot_user_activity SET loan_amount = ?, loan_taken_at = ?, loan_last_interest = ? WHERE guild_id = ? AND user_id = ?")) {
            long now = System.currentTimeMillis();
            pstmt.setInt(1, amount); pstmt.setLong(2, now); pstmt.setLong(3, now); pstmt.setString(4, guildId); pstmt.setString(5, userId);
            pstmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public void payLoan(String guildId, String userId, int amount) {
        ensureConnection();
        try (PreparedStatement pstmt = connection.prepareStatement("UPDATE bot_user_activity SET loan_amount = GREATEST(0, loan_amount - ?) WHERE guild_id = ? AND user_id = ?")) {
            pstmt.setInt(1, amount); pstmt.setString(2, guildId); pstmt.setString(3, userId);
            pstmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public void updateDepositClaim(String guildId, String userId) {
        ensureConnection();
        try (PreparedStatement pstmt = connection.prepareStatement("UPDATE bot_user_activity SET deposit_last_claim = ? WHERE guild_id = ? AND user_id = ?")) {
            pstmt.setLong(1, System.currentTimeMillis()); pstmt.setString(2, guildId); pstmt.setString(3, userId);
            pstmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public void checkAndApplyLoanInterest(String guildId, String userId) {
        ensureConnection();
        try (PreparedStatement pstmt = connection.prepareStatement("SELECT loan_amount, loan_taken_at, loan_last_interest FROM bot_user_activity WHERE guild_id = ? AND user_id = ?")) {
            pstmt.setString(1, guildId); pstmt.setString(2, userId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                int loan = rs.getInt("loan_amount");
                if (loan <= 0) return;

                long takenAt = rs.getLong("loan_taken_at");
                long lastInterest = rs.getLong("loan_last_interest");
                long now = System.currentTimeMillis();

                if (now - takenAt < 3 * 86400000L) return;

                long referenceTime = Math.max(lastInterest, takenAt + 3 * 86400000L);
                long daysPassed = (now - referenceTime) / 86400000L;

                if (daysPassed > 0) {
                    int newLoan = loan;
                    for (int i = 0; i < daysPassed; i++) {
                        newLoan += Math.max(1, (int)(newLoan * 0.01));
                    }
                    try (PreparedStatement upd = connection.prepareStatement("UPDATE bot_user_activity SET loan_amount = ?, loan_last_interest = ? WHERE guild_id = ? AND user_id = ?")) {
                        upd.setInt(1, newLoan); upd.setLong(2, referenceTime + daysPassed * 86400000L); upd.setString(3, guildId); upd.setString(4, userId);
                        upd.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {}
    }

    public int addCoins(String guildId, String userId, int amount, String type) {
        ensureConnection(); checkAndCreateUser(guildId, userId);
        if (amount <= 0) return 0;

        boolean bypassTax = type.equals("LOAN_TAKE") || type.equals("BANK_TRANSFER") || type.equals("ADMIN_ADD");
        int deductedAmount = 0;

        try (PreparedStatement check = connection.prepareStatement("SELECT coins, loan_amount FROM bot_user_activity WHERE guild_id = ? AND user_id = ?")) {
            check.setString(1, guildId); check.setString(2, userId);
            ResultSet rs = check.executeQuery();
            if (rs.next()) {
                int currentCoins = rs.getInt("coins");
                int loan = rs.getInt("loan_amount");
                int finalToAdd = amount;

                if (loan > 0 && !bypassTax) {
                    double deductionRate = (currentCoins < 0) ? 0.90 : 0.15;
                    int toDeduct = Math.max(1, (int) (amount * deductionRate));
                    if (toDeduct > loan) toDeduct = loan;

                    deductedAmount = toDeduct;
                    finalToAdd -= toDeduct;
                    loan -= toDeduct;

                    try (PreparedStatement updateLoan = connection.prepareStatement("UPDATE bot_user_activity SET loan_amount = ? WHERE guild_id = ? AND user_id = ?")) {
                        updateLoan.setInt(1, loan); updateLoan.setString(2, guildId); updateLoan.setString(3, userId);
                        updateLoan.executeUpdate();
                    }
                }

                try (PreparedStatement pstmt = connection.prepareStatement("UPDATE bot_user_activity SET coins = coins + ? WHERE guild_id = ? AND user_id = ?")) {
                    pstmt.setInt(1, finalToAdd); pstmt.setString(2, guildId); pstmt.setString(3, userId);
                    pstmt.executeUpdate();
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }

        return deductedAmount; // Zwracamy ile komornik zabrał
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
        try (PreparedStatement pstmt = connection.prepareStatement("SELECT pet_id, name, coin_mult, exp_mult, price FROM bot_guild_pets WHERE guild_id = ?")) {
            pstmt.setString(1, guildId); ResultSet rs = pstmt.executeQuery();
            while (rs.next()) { pets.add(new Object[]{rs.getInt("pet_id"), rs.getString("name"), rs.getDouble("coin_mult"), rs.getDouble("exp_mult"), rs.getInt("price")}); }
        } catch (SQLException e) { e.printStackTrace(); }
        if (pets.isEmpty()) {
            pets.add(new Object[]{1, "Blue Slime", 1.25, 1.00, 2500});
            pets.add(new Object[]{2, "Fire Fox", 1.50, 1.25, 10000});
            pets.add(new Object[]{3, "Dark Dragon", 1.75, 1.50, 25000});
        }
        return pets;
    }

    public Object[] getPetConfig(String guildId, int petId, boolean isPremium) {
        ensureConnection();
        try (PreparedStatement pstmt = connection.prepareStatement("SELECT name, coin_mult, exp_mult, price FROM bot_guild_pets WHERE guild_id = ? AND pet_id = ?")) {
            pstmt.setString(1, guildId); pstmt.setInt(2, petId); ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return new Object[]{rs.getString("name"), null, rs.getDouble("coin_mult"), rs.getDouble("exp_mult"), rs.getInt("price")};
        } catch (SQLException e) { e.printStackTrace(); }
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

    public void removeUserPet(String guildId, String userId) {
        ensureConnection();
        try (PreparedStatement pstmt = connection.prepareStatement("UPDATE bot_user_activity SET pet_id = 0, pet_last_fed = 0 WHERE guild_id = ? AND user_id = ?")) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, userId);
            pstmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public void addCustomPet(String guildId, String name, double coinMult, double expMult, int price) {
        ensureConnection();
        try (PreparedStatement pstmt = connection.prepareStatement("SELECT MAX(pet_id) FROM bot_guild_pets WHERE guild_id = ?")) {
            pstmt.setString(1, guildId);
            ResultSet rs = pstmt.executeQuery();
            int nextId = 4;
            if (rs.next()) {
                int maxId = rs.getInt(1);
                if (maxId >= 4) nextId = maxId + 1;
            }
            try (PreparedStatement ins = connection.prepareStatement("INSERT INTO bot_guild_pets (guild_id, pet_id, name, coin_mult, exp_mult, price) VALUES (?, ?, ?, ?, ?, ?)")) {
                ins.setString(1, guildId);
                ins.setInt(2, nextId);
                ins.setString(3, name);
                ins.setDouble(4, coinMult);
                ins.setDouble(5, expMult);
                ins.setInt(6, price);
                ins.executeUpdate();
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public void removeCustomPet(String guildId, int petId) {
        ensureConnection();
        try (PreparedStatement pstmt = connection.prepareStatement("DELETE FROM bot_guild_pets WHERE guild_id = ? AND pet_id = ?")) {
            pstmt.setString(1, guildId);
            pstmt.setInt(2, petId);
            pstmt.executeUpdate();
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
            try (PreparedStatement pstmt = connection.prepareStatement("DELETE FROM bot_bounties_v2 WHERE guild_id = ?")) { pstmt.setString(1, guildId); pstmt.executeUpdate(); }
        } catch (SQLException e) { e.printStackTrace(); }
    }
}