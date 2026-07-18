package pl.syntaxdevteam.listeners;

public class GuildSettings {

    // --- Ustawienia Ekonomii ---
    public boolean economyEnabled = true;
    public boolean isPremium = false;
    public String language = "pl"; // Domyślnie PL, panel WWW będzie zmieniał to na "eng" w bazie
    public String currencyName = "coins";
    public String currencyEmoji = "🪙";
    public int dailyAmount = 200;
    public int dailyAmount2 = 6000;
    public int dailyAmount3 = 15000;
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

    // --- Ustawienia Widoczności (WEB PANEL READY) ---
    // Zmienne kontrolowane ze strony WWW.
    // Decydują, czy komendy odpowiadają efemerycznie (tylko dla gracza = true), czy na czacie (publicznie = false).
    public boolean hideEconomyReplies = false; // /work, /daily, /pay (Domyślnie publiczne dla rywalizacji)
    public boolean hideCrimeReplies = false;   // /rob, /hunt, /bounty (Domyślnie publiczne)
    public boolean hideCompanyReplies = true;  // /company (Domyślnie PRYWATNE - nikt nie musi widzieć zarządzania Twoją firmą)

    // UWAGA: Moduły takie jak /heist oraz /market z zasady zawsze muszą odpowiadać publicznie,
    // ponieważ wymagają interakcji (klikania w przyciski) przez innych graczy obecnych na kanale.
}