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
}